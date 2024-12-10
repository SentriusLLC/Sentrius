package io.dataguardians.sso.core.config;

import io.dataguardians.sso.core.model.ErrorOutput;
import io.dataguardians.sso.core.services.ErrorOutputService;
import io.dataguardians.sso.core.utils.MessagingUtil;
import io.dataguardians.sso.core.utils.ZTATUtils;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestParam;
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
        String messageId = "generalError";

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
        return "redirect:/sso/v1/dashboard";
    }


}