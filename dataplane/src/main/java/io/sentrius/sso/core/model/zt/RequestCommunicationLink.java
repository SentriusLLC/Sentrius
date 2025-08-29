package io.sentrius.sso.core.model.zt;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import io.sentrius.sso.core.model.chat.AgentCommunication;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
// JITRequest Entity

@Entity
@Table(name = "request_communication_links",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"ztat_request_id", "communication_id"}),
        @UniqueConstraint(columnNames = {"operations_request_id", "communication_id"})
    })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"operationsRequest"}) // <-- add this
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class RequestCommunicationLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ztat_request_id")
    private ZeroTrustAccessTokenRequest ztatRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operations_request_id")
    @JsonBackReference
    private OpsZeroTrustAcessTokenRequest operationsRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "communication_id", nullable = false)
    @JsonManagedReference // <-- ADD THIS
    private AgentCommunication communication;

    @Column(name = "created_at", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private java.sql.Timestamp createdAt;
}
