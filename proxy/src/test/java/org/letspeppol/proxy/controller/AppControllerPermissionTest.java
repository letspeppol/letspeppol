package org.letspeppol.proxy.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.letspeppol.proxy.config.SecurityConfig;
import org.letspeppol.proxy.exception.GlobalExceptionHandler;
import org.letspeppol.proxy.model.AccountType;
import org.letspeppol.proxy.service.RegistryService;
import org.letspeppol.proxy.service.UblDocumentReceiverService;
import org.letspeppol.proxy.service.UblDocumentSenderService;
import org.letspeppol.proxy.service.UblDocumentService;
import org.letspeppol.proxy.service.ValidationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.core.annotation.AnnotatedElementUtils.hasAnnotation;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitConfig(AppControllerPermissionTest.Config.class)
class AppControllerPermissionTest {

    private static final String PEPPOL_ID = "0208:0123456789";
    private static final String READ = "hasAuthority('INVOICE_READ')";
    private static final String SEND = "hasAuthority('INVOICE_SEND')";

    @Configuration
    @EnableMethodSecurity
    @Import(AppController.class)
    static class Config {
        @Bean UblDocumentService ublDocumentService() { return mock(UblDocumentService.class); }
        @Bean UblDocumentSenderService ublDocumentSenderService() { return mock(UblDocumentSenderService.class); }
        @Bean UblDocumentReceiverService ublDocumentReceiverService() { return mock(UblDocumentReceiverService.class); }
        @Bean RegistryService registryService() { return mock(RegistryService.class); }
        @Bean ValidationService validationService() { return mock(ValidationService.class); }
        @Bean JwtDecoder jwtDecoder() { return mock(JwtDecoder.class); }
    }

    @Autowired
    private AppController controller;

    @Autowired
    private UblDocumentReceiverService receiverService;

    @Autowired
    private UblDocumentSenderService senderService;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void readOnlyUserPollsButCannotCancel() {
        Jwt jwt = authenticate(AccountType.USER, 1);
        UUID id = UUID.randomUUID();

        controller.getAllNew(jwt, 10);
        verify(receiverService).findAllNew(PEPPOL_ID, 10);

        assertThatThrownBy(() -> controller.delete(jwt, id, false)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void affiliateReachesTheDocumentsOfItsCompanyWithinItsPermissions() {
        Jwt jwt = authenticate(AccountType.AFFILIATE, 1);

        controller.getAllNew(jwt, 5);
        verify(receiverService).findAllNew(PEPPOL_ID, 5);

        assertThatThrownBy(() -> controller.delete(jwt, UUID.randomUUID(), false)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void userWithoutPermissionsCannotAcknowledge() {
        Jwt jwt = authenticate(AccountType.USER, 0);

        assertThatThrownBy(() -> controller.downloadedBatch(jwt, List.of(UUID.randomUUID()), true))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.getAllNew(jwt, 10)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void sendingUserCancels() {
        Jwt jwt = authenticate(AccountType.USER, 7);
        UUID id = UUID.randomUUID();

        controller.delete(jwt, id, false);

        verify(senderService).cancel(id, PEPPOL_ID, false);
    }

    @Test
    void appServiceTokenPollsItsLinkedCompanies() {
        UUID appUid = UUID.randomUUID();
        Jwt jwt = authenticate(jwt(AccountType.APP, appUid).claim("scope", List.of("service")).build());

        controller.getAllNew(jwt, 10);

        verify(receiverService).findAllNewByAppLink(appUid, 10);
    }

    @Test
    void deniedRequestIsForbiddenWithErrorCode() throws Exception {
        authenticate(AccountType.USER, 1);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        mockMvc.perform(get("/sapi/document")).andExpect(status().isOk());
        mockMvc.perform(delete("/sapi/document/{id}", UUID.randomUUID()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("MISSING_PERMISSION"));
    }

    @Test
    void everyDocumentRouteDeclaresItsPermission() {
        Map<String, String> rules = Arrays.stream(AppController.class.getDeclaredMethods())
                .filter(method -> hasAnnotation(method, RequestMapping.class))
                .collect(Collectors.toMap(Method::getName, AppControllerPermissionTest::rule));

        assertThat(rules).containsOnly(
                Map.entry("getAllNew", READ),
                Map.entry("getStatusUpdates", READ),
                Map.entry("getById", READ),
                Map.entry("getDetails", READ),
                Map.entry("downloaded", READ),
                Map.entry("downloadedBatch", READ),
                Map.entry("createToSend", SEND),
                Map.entry("update", SEND),
                Map.entry("reschedule", SEND),
                Map.entry("delete", SEND)
        );
    }

    private static String rule(Method method) {
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        return preAuthorize == null ? "" : preAuthorize.value();
    }

    private static Jwt authenticate(AccountType accountType, int permissionMask) {
        return authenticate(jwt(accountType, UUID.randomUUID()).claim(SecurityConfig.PERMISSION_MASK, permissionMask).build());
    }

    private static Jwt authenticate(Jwt jwt) {
        SecurityContextHolder.getContext().setAuthentication(new SecurityConfig().jwtAuthenticationConverter().convert(jwt));
        return jwt;
    }

    private static Jwt.Builder jwt(AccountType accountType, UUID uid) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim(SecurityConfig.ACCOUNT_TYPE, accountType.name())
                .claim(SecurityConfig.PEPPOL_ID, PEPPOL_ID)
                .claim(SecurityConfig.UID, uid.toString());
    }
}
