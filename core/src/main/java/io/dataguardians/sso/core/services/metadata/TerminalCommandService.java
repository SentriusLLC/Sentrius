package io.dataguardians.sso.core.services.metadata;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import io.dataguardians.sso.core.model.metadata.TerminalCommand;
import io.dataguardians.sso.core.model.metadata.TerminalSessionMetadata;
import io.dataguardians.sso.core.repository.TerminalCommandRepository;
import io.dataguardians.sso.core.repository.TerminalSessionMetadataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TerminalCommandService {
    private final TerminalCommandRepository commandRepository;

    @Autowired
    public TerminalCommandService(TerminalCommandRepository commandRepository) {
        this.commandRepository = commandRepository;
    }

    public TerminalCommand logCommand(TerminalCommand command) {
        return commandRepository.save(command);
    }

    public List<TerminalCommand> getCommandsBySessionId(Long sessionId) {
        return commandRepository.findAll()
            .stream()
            .filter(command -> command.getSession().getId().equals(sessionId))
            .collect(Collectors.toList());
    }

    public int countCommandsInSession(Long sessionId) {
        return (int) commandRepository.findAll()
            .stream()
            .filter(command -> command.getSession().getId().equals(sessionId))
            .count();
    }
}
