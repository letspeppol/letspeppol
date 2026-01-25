package org.letspeppol.proxy.repository;

import org.letspeppol.proxy.model.AppLink;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppLinkRepository extends JpaRepository<AppLink, AppLink.AppLinkId>  {
}
