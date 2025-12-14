package org.letspeppol.proxy.dto;

public record ValidationErrorDto(
        int column,
        int line,
        String message,
        String severity
) {}
