package io.dataguardians.sso.core.model.dto;

import io.dataguardians.sso.core.model.Notification;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class NotificationDTO {
    private Long id;
    private String message;
    private String initiator;
    private int viewCount;

    public NotificationDTO(Notification notification){
        this.id = notification.getId();
        this.message = notification.getMessage();
        this.initiator = null != notification.getInitiator() ? notification.getInitiator().getUsername() : "SYSTEM";
        this.viewCount = notification.getRecipients().size();
    }

}
