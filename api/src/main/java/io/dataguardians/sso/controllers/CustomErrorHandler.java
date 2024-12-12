package io.dataguardians.sso.controllers;

import io.dataguardians.sso.core.model.ErrorOutput;
import io.dataguardians.sso.core.services.ErrorOutputService;
import io.dataguardians.sso.core.utils.MessagingUtil;
import io.dataguardians.sso.core.utils.ZTATUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.error.ErrorController;
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
    public String handleError(HttpServletRequest request, Model model) {
        // Retrieve error details
        Integer statusCode = (Integer) request.getAttribute("javax.servlet.error.status_code");
        Throwable ex = (Throwable) request.getAttribute("javax.servlet.error.exception");

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
    }
}
