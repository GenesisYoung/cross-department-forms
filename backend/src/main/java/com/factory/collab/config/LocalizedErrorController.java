package com.factory.collab.config;

import com.factory.collab.i18n.Messages;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/** Covers servlet/container failures outside controller advice, without exposing internal exception details. */
@RestController
public class LocalizedErrorController implements ErrorController {
    @RequestMapping("${server.error.path:${error.path:/error}}")
    public ResponseEntity<Map<String, Object>> error(HttpServletRequest request) {
        Object value = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = value instanceof Integer i && i >= 400 && i <= 599 ? i : 500;
        String code = switch (status) {
            case 401 -> "error.unauthorized";
            case 403 -> "error.denied";
            case 404 -> "error.resourceNotFound";
            case 405 -> "error.method";
            case 413 -> "error.tooLarge";
            case 415 -> "error.media";
            default -> status >= 500 ? "error.unexpected" : "error.invalidRequest";
        };
        return ResponseEntity.status(status).body(Map.of("status", status, "code", code, "message", Messages.text(code)));
    }
}
