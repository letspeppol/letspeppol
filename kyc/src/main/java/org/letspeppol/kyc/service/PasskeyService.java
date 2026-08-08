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
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
            Environment environment,
            @Value("${webauthn.rp.id}") String rpId,
            @Value("${webauthn.rp.name}") String rpName,
            @Value("${webauthn.rp.origins}") String originsStr) {
        this.passkeyRepo = passkeyRepo;
        this.accountRepo = accountRepo;
        this.challengeStore = challengeStore;
        this.rpId = rpId == null ? "" : rpId.trim();
        this.rpName = rpName;
        this.origins = new HashSet<>();
        if (originsStr != null) {
            for (String o : originsStr.split("[,;\\s]+")) {
                if (!o.isBlank()) origins.add(new Origin(o.trim()));
            }
        }
        validateRelyingPartyConfig(environment);
        // Non-strict only skips attestation-certificate trust-anchor validation, which is moot here:
        // we request attestation "none" (consumer passwordless — we don't verify which authenticator
        // make/model the user enrolled). All security-critical checks (challenge, origin, RP ID,
        // signature, user verification, sign-count) are still enforced. Strict mode would only add
        // value with attestation "direct" plus a configured trust-anchor repository / FIDO MDS.
        this.webAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager();
        this.credentialDataConverter = new AttestedCredentialDataConverter(new ObjectConverter());
    }

    /**
     * Fail fast at startup on a misconfigured relying party (passkeys are origin-bound, so a wrong
     * rp id/origin rejects every ceremony). Deployed profiles must set rp id and origins explicitly,
     * and each origin host must equal the rp id or be a subdomain of it (WebAuthn suffix rule).
     */
    private void validateRelyingPartyConfig(Environment environment) {
        if (environment.matchesProfiles("postgres")) {
            requireExplicit(environment, "WEBAUTHN_RP_ID");
            requireExplicit(environment, "WEBAUTHN_RP_ORIGINS");
        }
        if (rpId.isBlank()) {
            throw new IllegalStateException("webauthn.rp.id must be configured");
        }
        if (origins.isEmpty()) {
            throw new IllegalStateException("webauthn.rp.origins must list at least one origin");
        }
        for (Origin origin : origins) {
            String host = origin.getHost();
            if (host == null || !(host.equals(rpId) || host.endsWith("." + rpId))) {
                throw new IllegalStateException("webauthn origin '" + origin + "' is not valid for rp id '"
                        + rpId + "': the origin host must equal the rp id or be a subdomain of it");
            }
        }
    }

    private static void requireExplicit(Environment environment, String envVar) {
        String value = environment.getProperty(envVar);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(envVar + " must be set in deployed environments");
        }
    }

    public Map<String, Object> generateRegistrationOptions(UUID accountExternalId) {
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
        // Require a resident (discoverable) credential so username-less login can find it via the
        // authenticator's account picker — no allowCredentials hint needed.
        options.put("authenticatorSelection", Map.of(
                "residentKey", "required",
                "requireResidentKey", true,
                "userVerification", "required"
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
        // userVerificationRequired = true: passkeys act as multi-factor (possession + PIN/biometric),
        // which is what allows passkey login to bypass the separate TOTP step safely.
        RegistrationParameters registrationParameters = new RegistrationParameters(
                serverProperty, null, true, true);

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

    public Map<String, Object> generateAuthenticationOptions(HttpSession session) {
        byte[] challenge = new byte[32];
        secureRandom.nextBytes(challenge);
        session.setAttribute(SESSION_CHALLENGE_KEY, challenge);

        Map<String, Object> options = new LinkedHashMap<>();
        options.put("challenge", Base64.getUrlEncoder().withoutPadding().encodeToString(challenge));
        options.put("rpId", rpId);
        options.put("timeout", 300000);
        // Discoverable-credential login: the authenticator presents its own account picker, so we
        // never return an allowCredentials list derived from the email. This keeps the response
        // shape constant and removes the account-enumeration oracle (email-with-passkey vs not).
        options.put("allowCredentials", List.of());
        options.put("userVerification", "required");

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
        // userVerificationRequired = true: enforce that the authenticator verified the user
        // (PIN/biometric), so a passkey is a true multi-factor credential, not possession-only.
        AuthenticationParameters authParameters = new AuthenticationParameters(
                serverProperty, authenticator, null, true, true);

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

        return stored.getAccount();
    }

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
