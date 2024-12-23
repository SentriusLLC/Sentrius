package io.dataguardians.sso.core.services.metadata;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import io.dataguardians.sso.core.model.metadata.TerminalSessionMetadata;
import io.dataguardians.sso.core.repository.TerminalSessionMetadataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TerminalSessionMetadataService {
    private final TerminalSessionMetadataRepository metadataRepository;

    @Autowired
    public TerminalSessionMetadataService(TerminalSessionMetadataRepository metadataRepository) {
        this.metadataRepository = metadataRepository;
    }

    public TerminalSessionMetadata createSession(TerminalSessionMetadata sessionMetadata) {
        return metadataRepository.save(sessionMetadata);
    }

    public Optional<TerminalSessionMetadata> getSessionById(Long id) {
        return metadataRepository.findById(id);
    }

    public List<TerminalSessionMetadata> getAllSessions() {
        return metadataRepository.findAll();
    }

    public void closeSession(Long id, Timestamp endTime) {
        metadataRepository.findById(id).ifPresent(session -> {
            session.setEndTime(endTime);
            session.setSessionStatus("CLOSED");
            metadataRepository.save(session);
        });
    }
}
