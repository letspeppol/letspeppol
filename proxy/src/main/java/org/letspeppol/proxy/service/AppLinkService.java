package org.letspeppol.proxy.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.proxy.model.AppLink;
import org.letspeppol.proxy.repository.AppLinkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Transactional
@Service
public class AppLinkService {

    private final AppLinkRepository appLinkRepository;

    public void add(String peppolId, UUID uid) {
        appLinkRepository.save(new AppLink(new AppLink.AppLinkId(peppolId, uid)));
    }

    public void remove(String peppolId, UUID uid) {
        appLinkRepository.deleteById(new AppLink.AppLinkId(peppolId, uid));
    }
}
