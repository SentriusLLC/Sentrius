package io.sentrius.sso.config;

import java.net.URI;
import io.sentrius.sso.core.exceptions.ZtatException;
import io.sentrius.sso.core.model.ErrorOutput;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.utils.MessagingUtil;
import io.sentrius.sso.core.utils.ZTATUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    final ErrorOutputService errorOutputService;

    public static String createErrorHash(StackTraceElement[] trace, String t) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : trace) {
            sb.append(element.toString());
        }
        sb.append(t);
        return ZTATUtils.getCommandHash(sb.toString());
    }


    @ExceptionHandler(ZtatException.class)
    public ResponseEntity<String> handlePrecondition(ZtatException ex) {
        log.warn("Precondition failed: {}", ex.getMessage());
        return ResponseEntity.status(428).body(ex.getMessage());
    }

    @ExceptionHandler(Throwable.class) // Catches all unhandled exceptions
    public ResponseEntity<String> handleAllExceptions(Throwable ex, RedirectAttributes redirectAttributes) {
        // Add a general message ID, or customize based on exception type
        String messageId = "generalError";

        if (ex instanceof ResponseStatusException responseStatusException){

            if (responseStatusException.getStatusCode() == HttpStatus.PRECONDITION_REQUIRED ) {
                // Handle precondition required
                return ResponseEntity.status(428).body(responseStatusException.getReason());
            }
        }
        ex.printStackTrace();
        log.info("ahhasldigjudaslkgj {}", ex.getMessage());
        log.error("asldkjgadlskgj " + ex.getCause(), ex);
        String message = "Received Error Message: " + ex.getCause();
        ErrorOutput errorOutput = ErrorOutput.builder()
                .errorType(ex.getClass().getName())
                .errorLocation(ex.getStackTrace()[0].toString())
                .errorHash(createErrorHash(ex.getStackTrace(), ex.getMessage()))
                .errorLogs(message)
                .build();
        errorOutputService.saveErrorOutput(errorOutput);


        // Add messageId as a redirect attribute
        redirectAttributes.addAttribute("errorId", MessagingUtil.getMessageId(MessagingUtil.UNEXPECTED_ERROR));


        // Redirect to "/mydashboard" with the messageId parameter
        URI redirectUri = URI.create("/sso/v1/dashboard?errorId=" + MessagingUtil.getMessageId(MessagingUtil.UNEXPECTED_ERROR));
        log.info("redirectUri {}", redirectUri);
        return ResponseEntity.status(HttpStatus.FOUND).location(redirectUri).build();
        //return "redirect:/sso/v1/dashboard";
    }


}