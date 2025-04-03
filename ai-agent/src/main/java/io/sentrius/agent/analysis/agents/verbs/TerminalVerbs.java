package io.sentrius.agent.analysis.agents.verbs;

import java.util.Map;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.services.agents.ZeroTrustClientService;
import io.sentrius.sso.core.model.verbs.Verb;
import org.springframework.stereotype.Service;

@Service
public class TerminalVerbs {


    final ZeroTrustClientService zeroTrustClientService;



    public TerminalVerbs(ZeroTrustClientService zeroTrustClientService) {
        this.zeroTrustClientService = zeroTrustClientService;
    }

    @Verb(
        name = "terminal_logs",
        description = "Fetches the terminal logs for users.",
        paramDescriptions =""
    )
    public static String fetchTerminalLogs() {
        return "Terminal logs are not available in this version.";
    }



    @Verb(name = "list_open_terminals", description = "Retrieves a list of currently open terminals.")
    public String listTerminals(Map<String, Object> args) {
        try {
            // Replace with your API URL
            String apiUrl = "http://localhost:8080/api/v1/ssh/terminal/list";

            var response = zeroTrustClientService.callGetOnApi("/ssh/terminal/list");
/*
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            // Optional: Add headers for authentication if needed
            conn.setRequestProperty("Authorization", "Bearer " + args.getOrDefault("token", ""));

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new RuntimeException("Failed to get terminals. Response code: " + responseCode);
            }

            try (Scanner scanner = new Scanner(conn.getInputStream())) {
                scanner.useDelimiter("\\A");
                return scanner.hasNext() ? scanner.next() : "";
            }
*/

        } catch (ZtatException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve terminal list", e);
        }
        return "";
    }
}
