package io.sentrius.sso.core.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import io.sentrius.sso.core.model.sessions.SessionOutput;
import io.sentrius.sso.core.services.terminal.SessionTrackingService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SecureShellTask {

    private final SessionTrackingService sessionOutputService;
    private static final Logger log = LoggerFactory.getLogger(SecureShellTask.class);

    @Async("taskExecutor")
    public void execute(SessionOutput sessionOutput, InputStream outFromChannel) {
        try (InputStreamReader isr = new InputStreamReader(outFromChannel);
             BufferedReader br = new BufferedReader(isr, 32 * 1024)) {

            sessionOutputService.addOutput(sessionOutput);

            char[] buff = new char[32 * 1024];
            int read;
            log.debug("Setting up...");

            while (!Thread.currentThread().isInterrupted() && !sessionOutput.getConnectedSystem().getSession().getClosed()) {
                if (br.ready() && (read = br.read(buff)) != -1) {
                    sessionOutputService.addToOutput(
                        sessionOutput.getConnectedSystem(), buff, 0, read);
                }
                Thread.sleep(50);
            }
            sessionOutputService.removeOutput(sessionOutput.getConnectedSystem());

        } catch (IOException | InterruptedException ex) {
            log.error("Error occurred while processing SecureShellTask: {}", ex.toString(), ex);
        }
    }




}
