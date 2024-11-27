package io.dataguardians.sso.integrations.ticketing;

import java.util.ArrayList;
import java.util.List;
import io.dataguardians.sso.core.model.dto.TicketDTO;
import io.dataguardians.sso.core.model.security.IntegrationSecurityToken;
import io.dataguardians.sso.core.model.users.User;
import io.dataguardians.sso.core.services.IntegrationSecurityTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TicketService {


    private final IntegrationSecurityTokenService integrationService;


    private final RestTemplateBuilder restTemplateBuilder;

    List<JiraService> jiraServiceList = new ArrayList<>();

    public TicketService(IntegrationSecurityTokenService integrationService, RestTemplateBuilder restTemplateBuilder) {
        this.integrationService = integrationService;
        this.restTemplateBuilder = restTemplateBuilder;
        var integrationAPI = integrationService.findByConnectionType("jira");
        for(IntegrationSecurityToken integration : integrationAPI){
            try {
                var jiraService = new JiraService(restTemplateBuilder.build(), integration);
                jiraServiceList.add(jiraService);
            }catch (Exception e){
                e.printStackTrace();
            }
        }

    }

    public List<TicketDTO> searchForIncidents(String query) {
        List<TicketDTO> tickets = new ArrayList<>();
        jiraServiceList.stream().forEach(jiraService -> {
            try {
                tickets.addAll(jiraService.searchForIncidents(query));
            }catch (Exception e){
                log.error("Error searching for incidents", e);
                e.printStackTrace();
            }
        });
        return tickets;
    }

    public boolean assignJira(String ticketKey, User user) {
        for(var jiraService : jiraServiceList){
            try {
                var assignee = jiraService.getUser(user);
                if ( jiraService.assignTicket(ticketKey, assignee) ){
                    return true;
                }
            }catch (Exception e){
                log.error("Error assigning ticket", e);
                e.printStackTrace();
            }
        }
        return false;
    }

    public boolean isTicketActive(String ticketKey) {
        /*
        if (ticketKey.startsWith("GH-")) {
            String[] parts = ticketKey.split("-");
            return gitHubService.isIssueOpen(parts[1], parts[0], Integer.parseInt(parts[2]));
        } else if (ticketKey.startsWith("JIRA-")) {
            return jiraService.isTicketActive(ticketKey);
        } else {
            throw new IllegalArgumentException("Unknown ticketing system: " + ticketKey);
        }

         */
        return false;
    }


    public boolean updateJira(String ticketKey, User operatingUser, String message) {
        for(var jiraService : jiraServiceList){

            try {
                var assignee = jiraService.getUser(operatingUser);
                if ( jiraService.updateTicket(ticketKey, message) ){

                    return true;
                }
            }catch (Exception e){
                log.error("Error assigning ticket", e);
                e.printStackTrace();
            }
        }
        return false;
    }
}
