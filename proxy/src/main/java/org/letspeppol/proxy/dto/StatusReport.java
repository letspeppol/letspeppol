package org.letspeppol.proxy.dto;

public record StatusReport(
        boolean success,
        String statusMessage
) {}
