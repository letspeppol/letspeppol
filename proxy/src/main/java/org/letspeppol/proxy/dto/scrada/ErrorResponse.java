package org.letspeppol.proxy.dto.scrada;

import java.util.List;

public record ErrorResponse(
        int errorCode,
        int errorType,
        String defaultFormat,
        List<String> parameters,
        List<ErrorResponse> innerErrors
) {}
