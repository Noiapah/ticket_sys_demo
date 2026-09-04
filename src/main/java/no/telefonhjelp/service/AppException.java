package no.telefonhjelp.service;

import org.springframework.http.HttpStatus;

public final class AppException extends RuntimeException {
    private final HttpStatus status;
    public AppException(HttpStatus status, String message) { super(message); this.status = status; }
    public HttpStatus status() { return status; }
    public static AppException notFound(String message) { return new AppException(HttpStatus.NOT_FOUND, message); }
    public static AppException badRequest(String message) { return new AppException(HttpStatus.BAD_REQUEST, message); }
    public static AppException conflict(String message) { return new AppException(HttpStatus.CONFLICT, message); }
}

