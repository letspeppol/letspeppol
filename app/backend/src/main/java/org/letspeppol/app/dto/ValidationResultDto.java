package org.letspeppol.app.dto;

import java.util.List;

public record ValidationResultDto(
        boolean isValid,
        int errorCount,
        List<ValidationErrorDto> errors,
        String vesId,
        String detectedVESIDRaw
) {}
