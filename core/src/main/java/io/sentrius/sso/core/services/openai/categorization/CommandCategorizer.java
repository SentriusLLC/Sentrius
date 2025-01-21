package io.sentrius.sso.core.services.openai.categorization;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.sentrius.sso.core.model.categorization.CommandCategory;
import io.sentrius.sso.core.repository.CommandCategoryRepository;
import io.sentrius.sso.core.services.IntegrationSecurityTokenService;
import io.sentrius.sso.core.utils.JsonUtil;
import io.sentrius.sso.genai.GenerativeAPI;
import io.sentrius.sso.genai.GeneratorConfiguration;
import io.sentrius.sso.genai.LLMCommandCategorizer;
import io.sentrius.sso.integrations.external.ExternalIntegrationDTO;
import io.sentrius.sso.security.ApiKey;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import jdk.jfr.TransitionTo;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommandCategorizer {

    private final IntegrationSecurityTokenService integrationSecurityTokenService;

    private final CommandCategoryRepository commandCategoryRepository;


    private final Cache<String, CommandCategory> commandCache = Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(1, TimeUnit.HOURS)
        .build();



    private CommandCategory fetchFromDatabase(String command) {
        List<CommandCategory> matchingCategories = commandCategoryRepository.findMatchingCategories(command);
        return matchingCategories.stream()
            .min(Comparator.comparingInt(CommandCategory::getPriority))
            .orElse(null);
    }


    @Transactional
    public CommandCategory categorizeCommand(String command) {
        return commandCache.get(command, this::categorizeWithRulesOrML);
    }

    protected List<CommandCategory> getDBCommandCategory(String command){
        return commandCategoryRepository.findByPattern(command);
    }


    public boolean isValidRegex(String regex) {
        try {
            Pattern.compile(regex);
            return true; // Valid regex
        } catch (PatternSyntaxException e) {
            return false; // Invalid regex
        }
    }


    @Transactional
    protected CommandCategory categorizeWithRulesOrML(String command) {
        CommandCategory category = fetchFromDatabase(command);
        if (category != null) {
            log.info("Found command category {} for {} ", category, command);
            return category;
        }
        
        var openaiService = integrationSecurityTokenService.findByConnectionType("openai").stream().findFirst().orElse(null);

        if (null != openaiService){
            log.info("OpenAI service is available");
            ExternalIntegrationDTO externalIntegrationDTO = null;
            try {
                externalIntegrationDTO = JsonUtil.MAPPER.readValue(openaiService.getConnectionInfo(),
                    ExternalIntegrationDTO.class);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
            ApiKey key =
                ApiKey.builder().apiKey(externalIntegrationDTO.getApiToken()).principal(externalIntegrationDTO.getUsername()).build();

            var commandCategorizer = new LLMCommandCategorizer(key, new GenerativeAPI(key), GeneratorConfiguration.builder().build());

            try {
                category = commandCategorizer.generate(command);

                if (isValidRegex(category.getPattern())) {
                    addCommandCategory(category.getPattern(), category);
                }
                log.info("Categorized command: {}", category);
                return category;
            } catch (Exception e) {
                e.printStackTrace();
                log.error("Error categorizing command", e);
            }

        } else {
            log.info("OpenAI service is not enabled");
        }

        log.info("Finished processing terminal commands");

        return CommandCategory.builder().build();
    }

    private void addCommandCategory(String pattern, CommandCategory category) {
        commandCategoryRepository.save(category);
        commandCache.put(pattern, category);
    }
}
