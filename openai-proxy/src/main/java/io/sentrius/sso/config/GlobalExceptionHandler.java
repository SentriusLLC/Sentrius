package io.sentrius.sso.config;

import io.sentrius.sso.core.model.ErrorOutput;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.utils.MessagingUtil;
import io.sentrius.sso.core.utils.ZTATUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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

    @ExceptionHandler(Throwable.class) // Catches all unhandled exceptions
    public String handleAllExceptions(Throwable ex, RedirectAttributes redirectAttributes) {
        // Add a general message ID, or customize based on exception type
        ex.printStackTrace();
       return "";
    }


}