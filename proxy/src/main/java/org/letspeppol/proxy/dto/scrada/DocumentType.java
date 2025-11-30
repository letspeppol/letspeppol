package org.letspeppol.proxy.dto.scrada;

public record DocumentType(
        String scheme,
        String value,
        ProcessIdentifier processIdentifier
) {}
