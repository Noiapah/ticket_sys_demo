package no.telefonhjelp.web;

import no.telefonhjelp.domain.ApiModels.ApiError;
import no.telefonhjelp.service.AppException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ApiExceptionHandler.class);
    @ExceptionHandler(AppException.class)
    ResponseEntity<ApiError> appError(AppException exception) {
        return ResponseEntity.status(exception.status()).body(new ApiError(exception.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.bind.ServletRequestBindingException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> invalid(Exception exception) {
        return ResponseEntity.badRequest().body(new ApiError("Ugyldig forespørsel. Kontroller feltene og prøv igjen."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception exception) {
        var reference = java.util.UUID.randomUUID().toString();
        // Exception messages, SQL parameters and request bodies can contain customer data.
        LOG.error("Request failed: reference={}, type={}", reference, exception.getClass().getName());
        return ResponseEntity.internalServerError().body(new ApiError("Operasjonen kunne ikke fullføres. Referanse: " + reference));
    }
}
