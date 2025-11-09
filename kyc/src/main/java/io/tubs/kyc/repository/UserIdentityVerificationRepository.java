package io.tubs.kyc.repository;

import io.tubs.kyc.model.UserIdentityVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserIdentityVerificationRepository extends JpaRepository<UserIdentityVerification, Long> {
    List<UserIdentityVerification> findByUserId(Long userId);
}

