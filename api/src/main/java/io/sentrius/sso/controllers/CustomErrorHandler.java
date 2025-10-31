package io.sentrius.sso.controllers;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sentrius.sso.core.model.ErrorOutput;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.utils.JsonUtil;
import io.sentrius.sso.core.utils.MessagingUtil;
import io.sentrius.sso.core.utils.ZTATUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Slf4j
@Controller
@RequiredArgsConstructor
public class CustomErrorHandler implements ErrorController {


    final ErrorOutputService errorOutputService;


    public static String createErrorHash(StackTraceElement[] trace, String t) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : trace) {
            sb.append(element.toString());
        }
        sb.append(t);
        return ZTATUtils.getCommandHash(sb.toString());
    }

    @RequestMapping("/error")
    public Object handleError(HttpServletRequest request, HttpServletResponse response, Model model) {
        Integer statusCode = (Integer) request.getAttribute("jakarta.servlet.error.status_code");
        Throwable ex = (Throwable) request.getAttribute("jakarta.servlet.error.exception");
        String message = (ex != null) ? ex.getMessage() : "Unknown error";

        // Log as needed
        log.error("Error occurred: Status code {}, message {}", statusCode, message, ex);
        log.error(response.toString());

        boolean isAjax = "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));
        if (isAjax) {
            ObjectNode errorResponse = JsonUtil.MAPPER.createObjectNode();
            errorResponse.put("error", true);
            errorResponse.put("status", statusCode != null ? statusCode : 500);
            errorResponse.put("message", message);
            return ResponseEntity.status(statusCode != null ? statusCode : 500).body(errorResponse);
        }

        // Otherwise, redirect to dashboard
        model.addAttribute("errorId", MessagingUtil.getMessageId(MessagingUtil.UNEXPECTED_ERROR));
        return "redirect:/sso/v1/dashboard?errorId=" + MessagingUtil.getMessageId(MessagingUtil.UNEXPECTED_ERROR);
    }
    /*
    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        // Retrieve error details
        log.info("errror");
        Integer statusCode = (Integer) request.getAttribute("jakarta.servlet.error.status_code");
        Throwable ex = (Throwable) request.getAttribute("jakarta.servlet.error.exception");

        // Log error details (optional)
        if (ex != null) {
            ex.printStackTrace();
            for(StackTraceElement element : ex.getStackTrace()) {
                log.info(element.toString());
            }
            String message = "Received Error Message: " + ex.getCause();
            ErrorOutput errorOutput = ErrorOutput.builder()
                .errorType(ex.getClass().getName())
                .errorLocation(ex.getStackTrace()[0].toString())
                .errorHash(createErrorHash(ex.getStackTrace(), ex.getMessage()))
                .errorLogs(message)
                .logTm(new java.sql.Timestamp(System.currentTimeMillis()))
                .build();
            errorOutputService.saveErrorOutput(errorOutput);

        }

        model.addAttribute("errorId", MessagingUtil.getMessageId(MessagingUtil.UNEXPECTED_ERROR));

        // Redirect to "/mydashboard" with the messageId parameter
        return "redirect:/sso/v1/dashboard?errorId=" + MessagingUtil.getMessageId(MessagingUtil.UNEXPECTED_ERROR);
    }*/
}
