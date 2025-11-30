package org.letspeppol.app.dto;

public record ValidationErrorDto(
        int column,
        int line,
        String message,
        String severity
) {}
