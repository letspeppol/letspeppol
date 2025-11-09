package io.tubs.kyc.service;

import io.tubs.kyc.exception.KycErrorCodes;
import io.tubs.kyc.exception.KycException;
import io.tubs.kyc.exception.NotFoundException;
import io.tubs.kyc.model.User;
import io.tubs.kyc.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
