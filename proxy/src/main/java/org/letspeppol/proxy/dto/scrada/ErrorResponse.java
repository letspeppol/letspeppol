package org.letspeppol.proxy.dto.scrada;

import java.util.List;

public record ErrorResponse(
        int errorCode,
        Integer errorType,
        String defaultFormat,
        List<String> parameters,
        List<ErrorResponse> innerErrors
) {}
