package org.letspeppol.proxy.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.proxy.dto.RegistrationRequest;
import org.letspeppol.proxy.dto.RegistryDto;
import org.letspeppol.proxy.dto.PeppolSettings;
import org.letspeppol.proxy.exception.ConflictException;
import org.letspeppol.proxy.exception.BadRequestException;
import org.letspeppol.proxy.exception.NotFoundException;
import org.letspeppol.proxy.mapper.RegistryMapper;
import org.letspeppol.proxy.model.AccessPoint;
import org.letspeppol.proxy.model.Registry;
import org.letspeppol.proxy.repository.RegistryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public PeppolSettings getSettings(String peppolId) {
        Registry registry = registryRepository.findByIdForSettings(peppolId)
                .orElseThrow(() -> new NotFoundException("Company is not registered here"));
        return settingsService(registry).getSettings(registry);
    }

    @Transactional(noRollbackFor = RuntimeException.class)
    public PeppolSettings updateSettings(String peppolId, PeppolSettings settings) {
        Registry registry = registryRepository.findByIdForSettings(peppolId)
                .orElseThrow(() -> new NotFoundException("Company is not registered here"));
        // The locked entity is managed by JPA. Commit confirmed state or invalidation even if
        // the provider write fails, so the next read can recover an uncertain remote outcome.
        return settingsService(registry).updateSettings(registry, settings);
    }

    private AccessPointServiceInterface settingsService(Registry registry) {
        var service = accessPointServiceRegistry.get(registry.getAccessPoint());
        if (registry.getAccessPoint() == AccessPoint.NONE || service == null) {
            throw new ConflictException("An active access point is required to change company settings");
        }
        return service;
    }

    /** Correct stale local registration without deregistering or deleting anything at the provider. */
    public void markNotRegistered(String peppolId, AccessPoint expectedAccessPoint) {
        registryRepository.findById(peppolId).ifPresent(registry -> {
            if (registry.getAccessPoint() == expectedAccessPoint) {
                registry.setAccessPoint(AccessPoint.NONE);
                registry.setReceiveDocuments(false);
                registryRepository.save(registry);
            }
        });
    }

    private void register(RegistrationRequest data, AccessPoint accessPoint, Registry registry) {
        try {
            if (accessPoint != AccessPoint.NONE) {
                AccessPointServiceInterface service = accessPointServiceRegistry.get(accessPoint);
                if (service == null) {
                    throw new BadRequestException("Peppol Access Point " + accessPoint + " is not active");
                }
                service.register(registry, data);
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
            registry.setReceiveDocuments(false);
        } catch (Exception _) {
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
