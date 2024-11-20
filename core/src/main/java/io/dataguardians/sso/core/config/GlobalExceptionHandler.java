package io.dataguardians.sso.core.config;

import io.dataguardians.sso.core.utils.MessagingUtil;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice
public class GlobalExceptionHandler {
/*
    @ExceptionHandler(Exception.class) // Catches all unhandled exceptions
    public String handleAllExceptions(Exception ex, RedirectAttributes redirectAttributes) {
        // Add a general message ID, or customize based on exception type
        String messageId = "generalError";

        ex.printStackTrace();

        if (ex instanceof EntityNotFoundException) {
            messageId = "entityNotFound";
        } else if (ex instanceof IllegalArgumentException) {
            messageId = "illegalArgument";
        }

        // Add messageId as a redirect attribute
        redirectAttributes.addAttribute("errorId", MessagingUtil.getMessageId(MessagingUtil.UNEXPECTED_ERROR));

        // Redirect to "/mydashboard" with the messageId parameter
        return "redirect:/sso/v1/dashboard";
    }

 */
}