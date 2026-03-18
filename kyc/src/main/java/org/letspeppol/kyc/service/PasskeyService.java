package org.letspeppol.kyc.service;

import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.authenticator.AuthenticatorImpl;
import com.webauthn4j.converter.AttestedCredentialDataConverter;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.data.AuthenticationData;
import com.webauthn4j.data.AuthenticationParameters;
import com.webauthn4j.data.AuthenticationRequest;
import com.webauthn4j.data.RegistrationData;
import com.webauthn4j.data.RegistrationParameters;
import com.webauthn4j.data.RegistrationRequest;
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.server.ServerProperty;
import com.webauthn4j.verifier.exception.VerificationException;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;
import org.letspeppol.kyc.dto.PasskeyAuthenticationResponse;
import org.letspeppol.kyc.dto.PasskeyDto;
import org.letspeppol.kyc.dto.PasskeyRegistrationResponse;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.model.PasskeyCredential;
import org.letspeppol.kyc.repository.AccountRepository;
import org.letspeppol.kyc.repository.PasskeyCredentialRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class PasskeyService {

    private static final String SESSION_CHALLENGE_KEY = "webauthn_challenge";

    private final PasskeyCredentialRepository passkeyRepo;
    private final AccountRepository accountRepo;
    private final ChallengeStore challengeStore;
    private final WebAuthnManager webAuthnManager;
    private final AttestedCredentialDataConverter credentialDataConverter;
    private final SecureRandom secureRandom = new SecureRandom();

    private final String rpId;
    private final String rpName;
    private final Set<Origin> origins;

    public PasskeyService(
            PasskeyCredentialRepository passkeyRepo,
            AccountRepository accountRepo,
            ChallengeStore challengeStore,
            @Value("${webauthn.rp.id}") String rpId,
            @Value("${webauthn.rp.name}") String rpName,
            @Value("${webauthn.rp.origins}") String originsStr) {
        this.passkeyRepo = passkeyRepo;
        this.accountRepo = accountRepo;
        this.challengeStore = challengeStore;
        this.rpId = rpId;
        this.rpName = rpName;
        this.origins = new HashSet<>();
        for (String o : originsStr.split("[,;\\s]+")) {
            if (!o.isBlank()) origins.add(new Origin(o.trim()));
        }
        this.webAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager();
        this.credentialDataConverter = new AttestedCredentialDataConverter(new ObjectConverter());
    }

    // --- Registration (from SPA, authenticated with JWT) ---

    public Map<String, Object> generateRegistrationOptions(UUID accountExternalId, String displayName) {
        Account account = accountRepo.findByExternalId(accountExternalId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        String challengeToken = challengeStore.createChallenge();
        String challengeBase64 = challengeStore.getChallengeBase64Url(challengeToken);

        List<Map<String, Object>> excludeCredentials = buildCredentialDescriptors(
                passkeyRepo.findAllByAccountId(account.getId()));

        Map<String, Object> options = new LinkedHashMap<>();
        options.put("challengeToken", challengeToken);
        options.put("challenge", challengeBase64);
        options.put("rp", Map.of("name", rpName, "id", rpId));
        options.put("user", Map.of(
                "id", Base64.getUrlEncoder().withoutPadding().encodeToString(
                        account.getExternalId().toString().getBytes()),
                "name", account.getEmail(),
                "displayName", account.getName()
        ));
        options.put("pubKeyCredParams", List.of(
                Map.of("alg", -7, "type", "public-key"),   // ES256
                Map.of("alg", -257, "type", "public-key")  // RS256
        ));
        options.put("timeout", 300000);
        options.put("excludeCredentials", excludeCredentials);
        options.put("authenticatorSelection", Map.of(
                "residentKey", "preferred",
                "requireResidentKey", false,
                "userVerification", "preferred"
        ));
        options.put("attestation", "none");

        return options;
    }

    @Transactional
    public void verifyRegistration(UUID accountExternalId, PasskeyRegistrationResponse response,
                                   String challengeToken, String displayName) {
        Account account = accountRepo.findByExternalId(accountExternalId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        byte[] challenge = challengeStore.consumeChallenge(challengeToken);
        if (challenge == null) {
            throw new IllegalArgumentException("Invalid or expired challenge");
        }

        byte[] clientDataJSON = Base64.getUrlDecoder().decode(response.clientDataJSON());
        byte[] attestationObject = Base64.getUrlDecoder().decode(response.attestationObject());

        ServerProperty serverProperty = new ServerProperty(origins, rpId, new DefaultChallenge(challenge), null);
        RegistrationRequest registrationRequest = new RegistrationRequest(attestationObject, clientDataJSON);
        RegistrationParameters registrationParameters = new RegistrationParameters(
                serverProperty, null, false, true);

        RegistrationData registrationData;
        try {
            registrationData = webAuthnManager.parse(registrationRequest);
            webAuthnManager.validate(registrationData, registrationParameters);
        } catch (VerificationException e) {
            throw new IllegalArgumentException("WebAuthn registration validation failed: " + e.getMessage(), e);
        }

        AttestedCredentialData attestedCredData = registrationData.getAttestationObject()
                .getAuthenticatorData().getAttestedCredentialData();
        if (attestedCredData == null) {
            throw new IllegalArgumentException("Missing attested credential data");
        }

        byte[] credentialId = attestedCredData.getCredentialId();
        byte[] publicKeyCose = credentialDataConverter.convert(attestedCredData);
        long signCount = registrationData.getAttestationObject().getAuthenticatorData().getSignCount();
        UUID aaguid = attestedCredData.getAaguid().getValue();
        String transports = response.transports() != null ? String.join(",", response.transports()) : null;

        PasskeyCredential credential = PasskeyCredential.builder()
                .account(account)
                .credentialId(credentialId)
                .publicKeyCose(publicKeyCose)
                .signCount(signCount)
                .transports(transports)
                .displayName(displayName != null && !displayName.isBlank() ? displayName : "Passkey")
                .aaguid(aaguid)
                .discoverable(true)
                .createdOn(Instant.now())
                .build();

        passkeyRepo.save(credential);
    }

    // --- Authentication (from login page, session-based) ---

    public Map<String, Object> generateAuthenticationOptions(String email, HttpSession session) {
        byte[] challenge = new byte[32];
        secureRandom.nextBytes(challenge);
        session.setAttribute(SESSION_CHALLENGE_KEY, challenge);

        List<Map<String, Object>> allowCredentials = List.of();
        if (email != null && !email.isBlank()) {
            Optional<Account> accountOpt = accountRepo.findByEmail(email.toLowerCase());
            if (accountOpt.isPresent()) {
                allowCredentials = buildCredentialDescriptors(
                        passkeyRepo.findAllByAccountId(accountOpt.get().getId()));
            }
        }

        Map<String, Object> options = new LinkedHashMap<>();
        options.put("challenge", Base64.getUrlEncoder().withoutPadding().encodeToString(challenge));
        options.put("rpId", rpId);
        options.put("timeout", 300000);
        options.put("allowCredentials", allowCredentials);
        options.put("userVerification", "preferred");

        return options;
    }

    @Transactional
    public Account verifyAuthentication(PasskeyAuthenticationResponse response, HttpSession session) {
        byte[] challenge = (byte[]) session.getAttribute(SESSION_CHALLENGE_KEY);
        session.removeAttribute(SESSION_CHALLENGE_KEY);
        if (challenge == null) {
            throw new IllegalArgumentException("No challenge in session");
        }

        byte[] credentialId = Base64.getUrlDecoder().decode(response.id());
        PasskeyCredential stored = passkeyRepo.findByCredentialId(credentialId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown credential"));

        AttestedCredentialData attestedCredentialData = credentialDataConverter.convert(stored.getPublicKeyCose());
        AuthenticatorImpl authenticator = new AuthenticatorImpl(
                attestedCredentialData, null, stored.getSignCount());

        byte[] clientDataJSON = Base64.getUrlDecoder().decode(response.clientDataJSON());
        byte[] authenticatorData = Base64.getUrlDecoder().decode(response.authenticatorData());
        byte[] signature = Base64.getUrlDecoder().decode(response.signature());

        ServerProperty serverProperty = new ServerProperty(origins, rpId, new DefaultChallenge(challenge), null);
        AuthenticationRequest authRequest = new AuthenticationRequest(
                credentialId, authenticatorData, clientDataJSON, signature);
        AuthenticationParameters authParameters = new AuthenticationParameters(
                serverProperty, authenticator, null, false, true);

        AuthenticationData authData;
        try {
            authData = webAuthnManager.parse(authRequest);
            webAuthnManager.validate(authData, authParameters);
        } catch (VerificationException e) {
            throw new IllegalArgumentException("WebAuthn authentication failed: " + e.getMessage(), e);
        }

        stored.setSignCount(authData.getAuthenticatorData().getSignCount());
        stored.setLastUsedOn(Instant.now());
        passkeyRepo.save(stored);

        // Eagerly load the Account to avoid LazyInitializationException after transaction ends
        return accountRepo.findById(stored.getAccount().getId())
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
    }

    // --- Management ---

    public List<PasskeyDto> listCredentials(UUID accountExternalId) {
        return passkeyRepo.findAllByAccountExternalId(accountExternalId).stream()
                .map(c -> new PasskeyDto(c.getId(), c.getDisplayName(), c.getCreatedOn(), c.getLastUsedOn()))
                .toList();
    }

    @Transactional
    public void deleteCredential(UUID accountExternalId, Long id) {
        Account account = accountRepo.findByExternalId(accountExternalId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        passkeyRepo.deleteByIdAndAccountId(id, account.getId());
    }

    @Transactional
    public void renameCredential(UUID accountExternalId, Long id, String newDisplayName) {
        Account account = accountRepo.findByExternalId(accountExternalId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        PasskeyCredential cred = passkeyRepo.findById(id)
                .filter(c -> c.getAccount().getId().equals(account.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Credential not found"));
        cred.setDisplayName(newDisplayName);
        passkeyRepo.save(cred);
    }

    private List<Map<String, Object>> buildCredentialDescriptors(List<PasskeyCredential> credentials) {
        return credentials.stream()
                .map(c -> {
                    Map<String, Object> cred = new LinkedHashMap<>();
                    cred.put("type", "public-key");
                    cred.put("id", Base64.getUrlEncoder().withoutPadding().encodeToString(c.getCredentialId()));
                    if (c.getTransports() != null && !c.getTransports().isEmpty()) {
                        cred.put("transports", Arrays.asList(c.getTransports().split(",")));
                    }
                    return cred;
                }).toList();
    }
}
