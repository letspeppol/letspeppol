package org.letspeppol.proxy.exception;

import lombok.extern.slf4j.Slf4j;
import org.letspeppol.proxy.dto.SimpleMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.lang.SecurityException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<SimpleMessage> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new SimpleMessage(ex.getMessage()));
    }

    @ExceptionHandler(AlreadyRegisteredException.class)
    public ResponseEntity<Map<String, Object>> handleAlreadyRegisteredException(AlreadyRegisteredException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "code", "PEPPOL_ALREADY_REGISTERED",
                "provider", e.getProvider(),
                "message", "Company already registered on Peppol at another provider."
        ));
    }

    @ExceptionHandler(DuplicateRequestException.class)
    public ResponseEntity<SimpleMessage> handleDuplicateRequestException(DuplicateRequestException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new SimpleMessage(ex.getMessage()));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<SimpleMessage> handleSecurityException(SecurityException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new SimpleMessage(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("message", "Validation failed");
        Map<String, String> errors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.put(fe.getField(), fe.getDefaultMessage());
        }
        body.put("errors", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<SimpleMessage> handleOther(Exception ex) {
        log.error(ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new SimpleMessage("Unexpected error"));
    }
}

