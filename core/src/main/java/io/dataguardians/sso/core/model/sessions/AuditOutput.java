package io.dataguardians.sso.core.model.sessions;

import java.util.List;
import io.dataguardians.sso.protobuf.Session;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class AuditOutput {
    Session.TerminalMessage outputMessage;
    List<Session.TerminalMessage> triggers;
}
