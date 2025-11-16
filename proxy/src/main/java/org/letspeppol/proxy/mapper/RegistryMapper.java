package org.letspeppol.proxy.mapper;

import org.letspeppol.proxy.dto.RegistryDto;
import org.letspeppol.proxy.model.Registry;

public class RegistryMapper {
    public static RegistryDto toDto(Registry registry) {
        if (registry == null) {
            return null;
        }
        return new RegistryDto(
            registry.getPeppolId(),
            registry.getAccessPoint()
        );
    }
}
