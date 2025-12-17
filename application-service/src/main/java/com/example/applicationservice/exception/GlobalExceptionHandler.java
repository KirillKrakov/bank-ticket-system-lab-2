package com.example.applicationservice.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BadRequestException.class)
    public Mono<ResponseEntity<Object>> handleBadRequest(BadRequestException ex, WebRequest request) {
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage(), request, null);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public Mono<ResponseEntity<Object>> handleConflict(UnauthorizedException ex, WebRequest request) {
        return buildError(HttpStatus.UNAUTHORIZED, ex.getMessage(), request, null);
    }

    @ExceptionHandler(ForbiddenException.class)
    public Mono<ResponseEntity<Object>> handleConflict(ForbiddenException ex, WebRequest request) {
        return buildError(HttpStatus.FORBIDDEN, ex.getMessage(), request, null);
    }

    @ExceptionHandler(NotFoundException.class)
    public Mono<ResponseEntity<Object>> handleNotFound(NotFoundException ex, WebRequest request) {
        return buildError(HttpStatus.NOT_FOUND, ex.getMessage(), request, null);
    }

    @ExceptionHandler(ConflictException.class)
    public Mono<ResponseEntity<Object>> handleConflict(ConflictException ex, WebRequest request) {
        return buildError(HttpStatus.CONFLICT, ex.getMessage(), request, null);
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public Mono<ResponseEntity<Object>> handleConstraint(org.springframework.dao.DataIntegrityViolationException ex, WebRequest request) {
        return buildError(HttpStatus.CONFLICT, "Database constraint violation", request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected Mono<ResponseEntity<Object>> handleValidation(MethodArgumentNotValidException ex, WebRequest request) {
        List<Map<String,String>> errors = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            Map<String,String> e = new HashMap<>();
            e.put("field", fe.getField());
            e.put("message", fe.getDefaultMessage());
            errors.add(e);
        }
        return buildError(HttpStatus.BAD_REQUEST, "Validation failed", request, errors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Mono<ResponseEntity<Object>> handleUUIDException(MethodArgumentTypeMismatchException ex) {
        if (ex.getRequiredType() == UUID.class) {
            throw new BadRequestException("Invalid UUID: " + ex.getValue());
        }
        throw ex;
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<Object>> handleGeneric(Exception ex, WebRequest request) {
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), request, null);
    }

    private Mono<ResponseEntity<Object>> buildError(HttpStatus status, String message, WebRequest request, List<Map<String,String>> errors) {
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("path", request.getDescription(false).replace("uri=", ""));
        if (errors != null && !errors.isEmpty()) {
            body.put("errors", errors);
        }
        return Mono.just(new ResponseEntity<>(body, new HttpHeaders(), status));
    }
}