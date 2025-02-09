package io.sentrius.sso.core.model.chat;

import io.sentrius.sso.core.model.sessions.SessionLog;
import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Getter
@Table(name = "chat_log")
public class ChatLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "session_id", nullable = false)
    private SessionLog session;

    @Column(nullable = false)
    private String chatGroupId;  // Unique chat dialog within the session

    private Integer instanceId;

    @Column(nullable = false)
    private String sender;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "message_tm", nullable = false)
    private LocalDateTime messageTimestamp = LocalDateTime.now();

    // Getters and Setters
}