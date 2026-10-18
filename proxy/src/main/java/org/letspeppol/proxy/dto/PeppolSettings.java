package org.letspeppol.proxy.dto;

/** Null fields in an update leave the corresponding provider setting unchanged. */
public record PeppolSettings(Boolean receiveDocuments) {}
