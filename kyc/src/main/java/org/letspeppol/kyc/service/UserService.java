package org.letspeppol.kyc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.kyc.exception.KycErrorCodes;
import org.letspeppol.kyc.exception.KycException;
import org.letspeppol.kyc.exception.NotFoundException;
import org.letspeppol.kyc.model.User;
import org.letspeppol.kyc.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public User findUserWithCredentials(String email, String password) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new NotFoundException(KycErrorCodes.USER_NOT_FOUND));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new KycException(KycErrorCodes.WRONG_PASSWORD);
        }
        return user;
    }

    public void updatePassword(User user, String rawPassword) {
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        userRepository.save(user);
    }
}
