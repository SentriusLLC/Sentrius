package io.sentrius.sso.config;

import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sentrius.sso.core.services.ErrorOutputService;
import io.sentrius.sso.core.utils.JsonUtil;
import io.sentrius.sso.core.utils.ZTATUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

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

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<Object> handleAllExceptions(Throwable ex) {
        ex.printStackTrace();

        if (ex instanceof ResponseStatusException rse) {
            HttpStatus status = (HttpStatus) rse.getStatusCode();
            Map<String, Object> error = new HashMap<>();
            error.put("status", status.value());
            error.put("error", status.getReasonPhrase());
            try{

                ObjectNode node = (ObjectNode) JsonUtil.MAPPER.readTree(rse.getReason());
                error.put("message", node);
            }catch(Exception e){
                error.put("message", rse.getReason());
            }

            return new ResponseEntity<>(error, status);
        }

        // Default fallback
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("status", 500);
        fallback.put("error", "Internal Server Error");
        fallback.put("message", ex.getMessage());
        return new ResponseEntity<>(fallback, HttpStatus.INTERNAL_SERVER_ERROR);
    }


}