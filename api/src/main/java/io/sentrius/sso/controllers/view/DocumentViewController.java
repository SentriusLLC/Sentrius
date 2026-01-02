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
 * View controller for data source management UI.
 * Provides data source search, retrieval, and visualization functionality
 * for documents, PDFs, wikis, and other data sources.
 */
@Slf4j
@Controller
@RequestMapping("/sso/v1/data")
public class DocumentViewController extends BaseController {

    public DocumentViewController(
        UserService userService,
        SystemOptions systemOptions,
        ErrorOutputService errorOutputService
    ) {
        super(userService, systemOptions, errorOutputService);
    }

    /**
     * Display the data sources page with search and browse capabilities.
     * Accessible to all authenticated users.
     */
    @GetMapping("/sources")
    public String dataSources(Model model) {
        log.info("Rendering data sources page");
        return "sso/data/sources";
    }
    
    /**
     * Legacy redirect from old documents path
     */
    @GetMapping("/documents")
    public String documentsRedirect(Model model) {
        return "redirect:/sso/v1/data/sources";
    }
}
