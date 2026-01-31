package org.letspeppol.app.repository;

import org.letspeppol.app.model.EmailJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmailJobRepository extends JpaRepository<EmailJob, Long> {

    List<EmailJob> findAllByStatusOrderByCreatedOnAsc(EmailJob.Status status);
}
