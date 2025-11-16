package org.letspeppol.proxy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
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

@RequiredArgsConstructor
@Transactional
@Service
public class RegistryService {

    private final RegistryRepository registryRepository;
    private final AccessPointServiceRegistry accessPointServiceRegistry;
    private final ObjectMapper objectMapper;

    public RegistryDto get(String peppolId) {
        return RegistryMapper.toDto(registryRepository.findById(peppolId).orElseThrow(() -> new NotFoundException("PeppolId " + peppolId + " is not registered here")));
    }

    public <T> T getVariables(String peppolId, Class<T> elementClass) {
        return objectMapper.convertValue(registryRepository.findById(peppolId).orElseThrow(() -> new NotFoundException("PeppolId " + peppolId + " is not registered here")).getVariables(), elementClass);
    }

    public AccessPoint getAccessPoint(String peppolId) {
        Optional<Registry> optionalRegistry = registryRepository.findById(peppolId);
        return optionalRegistry.isPresent() ? optionalRegistry.get().getAccessPoint() : AccessPoint.NONE;
    }

    private void unregister(Registry registry, AccessPoint accessPoint) {
        if (registry.getAccessPoint() != AccessPoint.NONE &&
            registry.getAccessPoint() != accessPoint) {
            AccessPointServiceInterface service = accessPointServiceRegistry.get(registry.getAccessPoint());
            if (service == null) {
                //TODO : LOG ERROR !!!
            } else {
                service.unregister(registry.getPeppolId());
            }
        }
    }

    public RegistryDto register(String peppolId, Map<String, Object> data, AccessPoint accessPoint) {
        Registry registry = registryRepository.findById(peppolId).orElse(new Registry(peppolId, AccessPoint.NONE, null));
        unregister(registry, accessPoint);
        if (accessPoint != AccessPoint.NONE) {
            AccessPointServiceInterface service = accessPointServiceRegistry.get(accessPoint);
            if (service == null) {
                throw new BadRequestException("Peppol Access Point " + accessPoint + " is not active");
            }
            Map<String, Object> variables = service.register(registry.getPeppolId(), data);
            registry.setVariables(variables);
        }
        registry.setAccessPoint(accessPoint);
        return RegistryMapper.toDto(registryRepository.save(registry));
    }

    public void suspend(String peppolId) {
        Registry registry = registryRepository.findById(peppolId).orElseThrow(() -> new NotFoundException("PeppolId " + peppolId + " is not registered here"));
        unregister(registry, AccessPoint.NONE);
        registry.setAccessPoint(AccessPoint.NONE);
    }

    public void remove(String peppolId) {
        suspend(peppolId);
        registryRepository.deleteById(peppolId);
    }

}
