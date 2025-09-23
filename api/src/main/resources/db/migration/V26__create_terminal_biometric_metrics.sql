-- V26__create_terminal_biometric_metrics.sql
-- Create terminal_biometric_metrics table for behavioral biometrics tracking

CREATE TABLE terminal_biometric_metrics (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    avg_dwell_time REAL,
    avg_flight_time REAL,
    keystroke_variance REAL,
    mouse_entropy REAL,
    typing_entropy REAL,
    
    CONSTRAINT fk_terminal_biometric_metrics_session 
        FOREIGN KEY (session_id) REFERENCES terminal_session_metadata(id) 
        ON DELETE CASCADE,
    
    CONSTRAINT uq_terminal_biometric_metrics_session 
        UNIQUE (session_id)
);