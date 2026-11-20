package com.factory.collab.config;

import com.factory.collab.i18n.Messages;
import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final Object[] arguments;

    public ApiException(HttpStatus status, String code, Object... arguments) {
        super(code);
        this.status = status;
        this.code = code;
        this.arguments = arguments.clone();
    }
    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
    @Override public String getMessage() { return Messages.text(code, arguments); }
    public static ApiException notFound(String entity) {
        return new ApiException(HttpStatus.NOT_FOUND, "error.notFound", Messages.text("entity." + entity));
    }
    public static ApiException forbidden(String code, Object... args) {
        return new ApiException(HttpStatus.FORBIDDEN, code, args);
    }
    public static ApiException badRequest(String code, Object... args) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, args);
    }
}
