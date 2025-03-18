package io.sentrius.agent.analysis.sinks.log;

import java.util.List;
import io.sentrius.sso.core.model.sessions.TerminalLogs;

public interface LogSink {

    void process(List<TerminalLogs> logs);
}
