package org.letspeppol.proxy.service;

import org.letspeppol.proxy.model.AccessPoint;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AccessPointServiceRegistry {

    private final Map<AccessPoint, AccessPointServiceInterface> services;

    public AccessPointServiceRegistry(List<AccessPointServiceInterface> implementations) {
        this.services = implementations.stream()
                .collect(Collectors.toMap(
                        AccessPointServiceInterface::getType,
                        Function.identity(),
                        (first, second) -> first, // in case of duplicate types, keep first
                        () -> new EnumMap<>(AccessPoint.class)
                ));
    }

    public AccessPointServiceInterface get(AccessPoint type) {
        return services.get(type);
    }
}