package io.sentrius.sso.core.services.metadata;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import io.sentrius.sso.core.model.metadata.TerminalSessionMetadata;
import io.sentrius.sso.core.model.sessions.SessionLog;
import io.sentrius.sso.core.repository.TerminalSessionMetadataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TerminalSessionMetadataService {
    private final TerminalSessionMetadataRepository metadataRepository;

    @Autowired
    public TerminalSessionMetadataService(TerminalSessionMetadataRepository metadataRepository) {
        this.metadataRepository = metadataRepository;
    }

    @Transactional
    public TerminalSessionMetadata createSession(TerminalSessionMetadata sessionMetadata) {
        return metadataRepository.save(sessionMetadata);
    }

    public Optional<TerminalSessionMetadata> getSessionById(Long id) {
        return metadataRepository.findById(id);
    }

    public List<TerminalSessionMetadata> getAllSessions() {
        return metadataRepository.findAll();
    }

    public List<TerminalSessionMetadata> getSessionsByState(String state) {
        return metadataRepository.findSessionsBySessionStatus(state);
    }

    public void closeSession(Long id, Timestamp endTime) {
        metadataRepository.findById(id).ifPresent(session -> {
            session.setEndTime(endTime);
            session.setSessionStatus("CLOSED");
            metadataRepository.save(session);
        });
    }

    public Optional<TerminalSessionMetadata> getSessionBySessionLog(SessionLog session) {
        return metadataRepository.findMetadataBySessionLogId(session.getId());
    }

    public void saveSession(TerminalSessionMetadata sessionMetadata) {
        metadataRepository.save(sessionMetadata);
    }
}
