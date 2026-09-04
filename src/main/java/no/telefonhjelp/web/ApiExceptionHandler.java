package no.telefonhjelp.web;

import no.telefonhjelp.domain.ApiModels.ApiError;
import no.telefonhjelp.service.AppException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(AppException.class)
    ResponseEntity<ApiError> appError(AppException exception) {
        return ResponseEntity.status(exception.status()).body(new ApiError(exception.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    ResponseEntity<ApiError> invalid(Exception exception) {
        return ResponseEntity.badRequest().body(new ApiError(exception.getMessage()));
    }
}

