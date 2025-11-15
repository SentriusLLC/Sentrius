package io.sentrius.sso.controllers.view;

import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import io.sentrius.sso.core.services.trust.AgentTrustScoreService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Slf4j
@Controller
@RequestMapping("/sso")
public class TrustScoreViewController extends BaseController {
    
    private final AgentTrustScoreService trustScoreService;
    
    protected TrustScoreViewController(
            UserService userService,
            SystemOptions systemOptions,
            ErrorOutputService errorOutputService,
            AgentTrustScoreService trustScoreService) {
        super(userService, systemOptions, errorOutputService);
        this.trustScoreService = trustScoreService;
    }
    
    @GetMapping("/trust-scores")
    public String trustScores(
            Model model,
            HttpServletRequest request,
            HttpServletResponse response) {
        var operatingUser = getOperatingUser(request, response);
        if (operatingUser == null) {
            return "redirect:/login";
        }
        
        model.addAttribute("systemOptions", systemOptions);
        return "sso/trust_scores";
    }
    
    @GetMapping("/trust-scores/agent/{agentId}")
    public String agentTrustScore(
            @PathVariable String agentId,
            Model model,
            HttpServletRequest request,
            HttpServletResponse response) {
        var operatingUser = getOperatingUser(request, response);
        if (operatingUser == null) {
            return "redirect:/login";
        }
        
        model.addAttribute("systemOptions", systemOptions);
        model.addAttribute("agentId", agentId);
        return "sso/agent_trust_score";
    }
}
