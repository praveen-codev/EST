package com.cognition.est.app.web;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> onValidation(
            MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<ApiError.FieldError> errors =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(fe -> new ApiError.FieldError(fe.getField(), fe.getDefaultMessage()))
                        .toList();
        return build(
                HttpStatus.BAD_REQUEST,
                "Validation failed",
                "One or more fields are invalid.",
                req,
                errors);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    ResponseEntity<ApiError> onNotFound(EntityNotFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "Not found", ex.getMessage(), req, List.of());
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    ResponseEntity<ApiError> onNoRoute(Exception ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "Not found", "No such endpoint.", req, List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> onDenied(AccessDeniedException ex, HttpServletRequest req) {
        return build(
                HttpStatus.FORBIDDEN,
                "Forbidden",
                "You do not have access to this resource.",
                req,
                List.of());
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> onStatus(ResponseStatusException ex, HttpServletRequest req) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        return build(status, status.getReasonPhrase(), ex.getReason(), req, List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> onUnexpected(Exception ex, HttpServletRequest req) {
        log.error("Unhandled error on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal error",
                "Something went wrong.",
                req,
                List.of());
    }

    private static ResponseEntity<ApiError> build(
            HttpStatus status,
            String title,
            String detail,
            HttpServletRequest req,
            List<ApiError.FieldError> errors) {
        return ResponseEntity.status(status)
                .body(
                        new ApiError(
                                "about:blank",
                                title,
                                status.value(),
                                detail,
                                req.getRequestURI(),
                                Instant.now(),
                                errors));
    }
}
