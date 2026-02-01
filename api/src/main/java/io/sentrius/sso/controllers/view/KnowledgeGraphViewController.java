package io.sentrius.sso.controllers.view;

import io.sentrius.sso.core.config.SystemOptions;
import io.sentrius.sso.core.controllers.BaseController;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.services.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * View controller for knowledge graph query UI.
 * Provides knowledge graph querying, document discovery, and question answering functionality.
 */
@Slf4j
@Controller
@RequestMapping("/sso/v1/knowledge-graph")
public class KnowledgeGraphViewController extends BaseController {

    public KnowledgeGraphViewController(
        UserService userService,
        SystemOptions systemOptions,
        ErrorOutputService errorOutputService
    ) {
        super(userService, systemOptions, errorOutputService);
    }

    /**
     * Display the knowledge graph query page.
     * Accessible to all authenticated users.
     */
    @GetMapping("/query")
    public String knowledgeGraphQuery(Model model) {
        log.info("Rendering knowledge graph query page");
        return "sso/knowledge_graph";
    }
}
