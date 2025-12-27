package org.letspeppol.proxy.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.proxy.dto.RegistrationRequest;
import org.letspeppol.proxy.dto.RegistryDto;
import org.letspeppol.proxy.exception.BadRequestException;
import org.letspeppol.proxy.exception.NotFoundException;
import org.letspeppol.proxy.mapper.RegistryMapper;
import org.letspeppol.proxy.model.AccessPoint;
import org.letspeppol.proxy.model.Registry;
import org.letspeppol.proxy.repository.RegistryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Transactional
@Service
public class RegistryService {

    private final RegistryRepository registryRepository;
    private final AccessPointServiceRegistry accessPointServiceRegistry;

    public RegistryDto get(String peppolId) {
        return RegistryMapper.toDto(registryRepository.findById(peppolId).orElseThrow(() -> new NotFoundException("PeppolId " + peppolId + " is not registered here")));
    }

    public AccessPoint getAccessPoint(String peppolId) {
        Optional<Registry> optionalRegistry = registryRepository.findById(peppolId);
        return optionalRegistry.isPresent() ? optionalRegistry.get().getAccessPoint() : AccessPoint.NONE;
    }

    private void register(RegistrationRequest data, AccessPoint accessPoint, Registry registry) {
        try {
            if (accessPoint != AccessPoint.NONE) {
                AccessPointServiceInterface service = accessPointServiceRegistry.get(accessPoint);
                if (service == null) {
                    throw new BadRequestException("Peppol Access Point " + accessPoint + " is not active");
                }
                Map<String, Object> variables = service.register(registry.getPeppolId(), data);
                registry.setVariables(variables);
                log.info("Registered PeppolId {} at access point {}", registry.getPeppolId(), accessPoint);
            }
            registry.setAccessPoint(accessPoint);
        } catch (Exception e) {
            log.error("Failed to register {} at access point {}", registry.getPeppolId(), accessPoint);
            throw e;
        }
    }

    private void unregister(Registry registry, AccessPoint accessPoint) {
        try {
            if (registry.getAccessPoint() != AccessPoint.NONE &&
                registry.getAccessPoint() != accessPoint) {
                AccessPointServiceInterface service = accessPointServiceRegistry.get(registry.getAccessPoint());
                if (service == null) {
                    throw new BadRequestException("Peppol Access Point " + accessPoint + " is not active");
                } else {
                    service.unregister(registry.getPeppolId(), registry.getVariables());
                    log.info("Unregistered PeppolId {} at access point {}", registry.getPeppolId(), accessPoint);
                }
            }
            registry.setAccessPoint(AccessPoint.NONE);
        } catch (Exception e) {
            log.error("Failed to unregister {} at access point {}", registry.getPeppolId(), accessPoint);
        }
    }

    public RegistryDto register(String peppolId, RegistrationRequest data, AccessPoint accessPoint) {
        Registry registry = registryRepository.findById(peppolId).orElse(new Registry(peppolId, AccessPoint.NONE, null));
        unregister(registry, accessPoint);
        register(data, accessPoint, registry);
        return RegistryMapper.toDto(registryRepository.save(registry));
    }

    public RegistryDto unregister(String peppolId) {
        Registry registry = registryRepository.findById(peppolId).orElseThrow(() -> new NotFoundException("PeppolId " + peppolId + " is not known here"));
        unregister(registry, AccessPoint.NONE);
        return RegistryMapper.toDto(registryRepository.save(registry));
    }

    public void remove(String peppolId) {
        unregister(peppolId);
        registryRepository.deleteById(peppolId);
    }

}
