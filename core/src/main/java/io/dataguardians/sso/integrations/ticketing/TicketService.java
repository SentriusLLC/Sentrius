package io.dataguardians.sso.integrations.ticketing;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TicketService {

    @Autowired
    private GitHubService gitHubService;
    @Autowired
    private JiraService jiraService;


    public boolean isTicketActive(String ticketKey) {
        if (ticketKey.startsWith("GH-")) {
            String[] parts = ticketKey.split("-");
            return gitHubService.isIssueOpen(parts[1], parts[0], Integer.parseInt(parts[2]));
        } else if (ticketKey.startsWith("JIRA-")) {
            return jiraService.isTicketActive(ticketKey);
        } else {
            throw new IllegalArgumentException("Unknown ticketing system: " + ticketKey);
        }
    }
}
