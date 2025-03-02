package io.sentrius.agent.analysis.model;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Getter
@Data
@Builder
public class AgentStatus {

    private String status;
    private String version;
    private String health;

    public AgentStatus(String status, String version, String health) {
        this.status = status;
        this.version = version;
        this.health = health;
    }

    public String getStatus() {
        return status;
    }

    public String getVersion() {
        return version;
    }

    public String getHealth() {
        return health;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void setHealth(String health) {
        this.health = health;
    }

    @Override
    public String toString() {
        return "AgentStatus{" +
                "status='" + status + '\'' +
                ", version='" + version + '\'' +
                ", health='" + health + '\'' +
                '}';
    }
}
