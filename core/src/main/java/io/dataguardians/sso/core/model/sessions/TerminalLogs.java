package io.dataguardians.sso.core.model.sessions;

import java.sql.Timestamp;
import io.dataguardians.sso.core.model.ConnectedSystem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/// TerminalLog Entity
@Entity
@Table(name = "terminal_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TerminalLogs {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private SessionLog session;

    @Column(name = "instance_id")
    private Integer instanceId;

    @Column(name = "output", columnDefinition = "TEXT")
    @Lob
    private String output;

    @Column(name = "log_tm", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private Timestamp logTm;

    @Column(name = "display_nm", nullable = false)
    private String displayNm;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "host", nullable = false)
    private String host;

    @Column(name = "port", nullable = false)
    private Integer port;

    // Static factory method to create TerminalLogs from output string
    public static TerminalLogs from(ConnectedSystem ident, String output) {
        TerminalLogs terminalLogs = new TerminalLogs();
        terminalLogs.setOutput(output);
        terminalLogs.setHost(ident.getHostSystem().getHost());
        terminalLogs.setDisplayNm("");
        terminalLogs.setPort(ident.getHostSystem().getPort());
        terminalLogs.setUsername(ident.getUser().getUsername());
        terminalLogs.setSession(ident.getSession());
        terminalLogs.setLogTm(new Timestamp(System.currentTimeMillis()));
        return terminalLogs;
    }

    // Method to append new output to existing logs
    public void append(String newOutput) {
        if (this.output == null) {
            this.output = newOutput;
        } else {
            this.output += newOutput;
        }
    }
}
