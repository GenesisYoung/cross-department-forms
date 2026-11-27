package com.factory.collab.config;

import com.factory.collab.i18n.Messages;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import java.util.*;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ApiException e) {
        return body(e.getStatus(), e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        var errors = e.getBindingResult().getFieldErrors().stream().map(f -> Map.of(
                "field", f.getField(), "message", validationMessage(f))).toList();
        String message = errors.stream().map(f -> {
            String field = f.get("field");
            String leaf = field.substring(field.lastIndexOf('.') + 1).replaceAll("\\[\\d+\\]", "");
            return Messages.text("field." + leaf) + ": " + f.get("message");
        }).reduce((a, b) -> a + "; " + b).orElse(Messages.text("error.invalidRequest"));
        return ResponseEntity.badRequest().body(Map.of("status", 400, "code", "error.invalidRequest", "message", message, "errors", errors));
    }

    private String validationMessage(FieldError f) {
        // Annotation arguments are available on Spring's constraint descriptor; no provider English leaks out.
        if (f.contains(jakarta.validation.ConstraintViolation.class)) {
            var attrs = f.unwrap(jakarta.validation.ConstraintViolation.class).getConstraintDescriptor().getAttributes();
            return switch (Objects.requireNonNullElse(f.getCode(), "")) {
                case "Size" -> Messages.text("validation.Size", attrs.get("min"), attrs.get("max"));
                case "Min", "Max" -> Messages.text("validation." + f.getCode(), attrs.get("value"));
                case "NotBlank", "NotEmpty", "NotNull", "Pattern" -> Messages.text("validation." + f.getCode());
                default -> Messages.text("error.invalidRequest");
            };
        }
        return Messages.text("error.invalidRequest");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleDenied(AccessDeniedException e) { return error(HttpStatus.FORBIDDEN, "error.denied"); }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleUpload(MaxUploadSizeExceededException e) { return error(HttpStatus.PAYLOAD_TOO_LARGE, "error.tooLarge"); }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            ServletRequestBindingException.class, ConstraintViolationException.class, org.springframework.web.multipart.MultipartException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception e) { return error(HttpStatus.BAD_REQUEST, "error.invalidRequest"); }
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethod(Exception e) { return error(HttpStatus.METHOD_NOT_ALLOWED, "error.method"); }
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMedia(Exception e) { return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "error.media"); }
    @ExceptionHandler({org.springframework.web.servlet.resource.NoResourceFoundException.class, org.springframework.web.servlet.NoHandlerFoundException.class})
    public ResponseEntity<Map<String, Object>> handleMissing(Exception e) { return error(HttpStatus.NOT_FOUND, "error.resourceNotFound"); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(Exception e) { return error(HttpStatus.CONFLICT, "error.conflict"); }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception e) {
        log.error("Unhandled error", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "error.unexpected");
    }
    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code) { return body(status, code, Messages.text(code)); }
    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("status", status.value(), "code", code, "message", message));
    }
}
