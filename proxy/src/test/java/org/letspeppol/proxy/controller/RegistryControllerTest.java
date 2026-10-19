package org.letspeppol.proxy.controller;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.letspeppol.proxy.dto.RegistryDto;
import org.letspeppol.proxy.exception.NotFoundException;
import org.letspeppol.proxy.config.SecurityConfig;
import org.letspeppol.proxy.exception.GlobalExceptionHandler;
import org.letspeppol.proxy.dto.PeppolSettings;
import org.letspeppol.proxy.model.AccessPoint;
import org.letspeppol.proxy.model.Registry;
import org.letspeppol.proxy.repository.RegistryRepository;
import org.letspeppol.proxy.service.AccessPointServiceRegistry;
import org.letspeppol.proxy.service.AppLinkService;
import org.letspeppol.proxy.service.RegistryService;
import org.letspeppol.proxy.service.AccessPointServiceInterface;
import org.letspeppol.proxy.service.RecommandService;
import org.letspeppol.proxy.service.ScradaService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Map;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RegistryControllerTest {
    private static final String ID = "0208:0123456789";
    private static final String MOVE_PATH = "/sapi/registry/" + ID + "/move";
    private static final String PATH = MOVE_PATH + "?accessPoint=RECOMMAND";
    private static final String PAYLOAD = """
            {"name":"Example BV","language":"EN","country":"BE","signedContract":"AQ=="}
            """;
    private static AnnotationConfigWebApplicationContext context;
    private static MockMvc mvc;
    private static RegistryRepository repository;
    private static AccessPointServiceRegistry accessPoints;
    private static ScradaService scrada;
    private static RecommandService recommand;
    private Registry registry;

    @BeforeAll
    static void start() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("operator-test", Map.of("proxy.access-point", "SCRADA")));
        context.register(LocalConfig.class);
        context.refresh();
        repository = context.getBean(RegistryRepository.class);
        accessPoints = context.getBean(AccessPointServiceRegistry.class);
        scrada = context.getBean(ScradaService.class);
        recommand = context.getBean(RecommandService.class);
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .defaultRequest(post("/").header(HttpHeaders.AUTHORIZATION, "Bearer service-token"))
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    @AfterAll
    static void stop() {
        if (context != null) context.close();
    }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(context.getBean(RegistryController.class), "accessPoint", AccessPoint.SCRADA);
        reset(repository, accessPoints, scrada, recommand);
        reset(context.getBean(RegistryService.class));
        when(accessPoints.get(AccessPoint.SCRADA)).thenReturn(scrada);
        when(accessPoints.get(AccessPoint.RECOMMAND)).thenReturn(recommand);
        doCallRealMethod().when(scrada).validateRegistration(anyString(), any());
        doCallRealMethod().when(recommand).validateRegistration(anyString(), any());
        registry = new Registry(ID, AccessPoint.SCRADA, Map.of("uuid", "original"));
        registry.setReceiveDocuments(true);
        when(repository.findById(ID)).thenReturn(Optional.of(registry));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        doAnswer(call -> {
            call.<Registry>getArgument(0).setVariables(Map.of("companyId", "recommand-1"));
            call.<Registry>getArgument(0).setReceiveDocuments(false);
            return null;
        }).when(recommand).register(any(), any());
        doAnswer(call -> {
            call.<Registry>getArgument(0).setVariables(Map.of("uuid", "restored"));
            call.<Registry>getArgument(0).setReceiveDocuments(true);
            return null;
        }).when(scrada).register(any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = AccessPoint.class, names = {"SCRADA", "RECOMMAND"})
    void registrationStatusIncludesTheDefaultForCompaniesThatNeverRegistered(AccessPoint defaultAccessPoint) throws Exception {
        ReflectionTestUtils.setField(context.getBean(RegistryController.class), "accessPoint", defaultAccessPoint);
        when(context.getBean(RegistryService.class).get(ID)).thenThrow(new NotFoundException("No registration"));

        mvc.perform(get("/sapi/registry/registration-status").param("peppolId", ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.peppolId").value(ID))
                .andExpect(jsonPath("$.peppolActive").value(false))
                .andExpect(jsonPath("$.accessPoint").value("NONE"))
                .andExpect(jsonPath("$.defaultAccessPoint").value(defaultAccessPoint.name()));
    }

    @Test
    void registrationStatusKeepsTheCurrentProviderSeparateFromTheDefault() throws Exception {
        var service = context.getBean(RegistryService.class);
        when(service.get(ID)).thenReturn(new RegistryDto(ID, true, "RECOMMAND"));

        mvc.perform(get("/sapi/registry/registration-status").param("peppolId", ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessPoint").value("RECOMMAND"))
                .andExpect(jsonPath("$.defaultAccessPoint").value("SCRADA"));

        when(service.get(ID)).thenReturn(new RegistryDto(ID, false, "NONE"));
        mvc.perform(get("/sapi/registry/registration-status").param("peppolId", ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.peppolActive").value(false))
                .andExpect(jsonPath("$.accessPoint").value("NONE"))
                .andExpect(jsonPath("$.defaultAccessPoint").value("SCRADA"));
    }

    @Test
    void kycServiceCanMoveWithItsServiceToken() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(PAYLOAD))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessPoint").value("RECOMMAND"));

        assertThat(registry.getVariables()).containsEntry("companyId", "recommand-1");
        assertThat(registry.getReceiveDocuments()).isFalse();
        var order = inOrder(scrada, recommand);
        order.verify(recommand).validateRegistration(eq(ID), any());
        order.verify(scrada).validateRegistration(eq(ID), any());
        order.verify(scrada).unregister(ID, Map.of("uuid", "original"));
        order.verify(recommand).register(eq(registry), any());
        verify(scrada, never()).register(any(), any());
    }

    @Test
    void serviceCanReadAndChangeCompanySettings() throws Exception {
        var service = context.getBean(RegistryService.class);
        when(service.getSettings(ID)).thenReturn(new PeppolSettings(true));
        when(service.updateSettings(ID, new PeppolSettings(false))).thenReturn(new PeppolSettings(false));

        mvc.perform(get("/sapi/registry/settings").param("peppolId", ID))
                .andExpect(status().isOk()).andExpect(jsonPath("$.receiveDocuments").value(true));
        mvc.perform(put("/sapi/registry/settings").param("peppolId", ID).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiveDocuments\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.receiveDocuments").value(false));
        verify(service).updateSettings(ID, new PeppolSettings(false));
    }

    @Test
    void onlyBackendServicesCanUseTheInternalReceivingLookup() throws Exception {
        when(recommand.getReceivingAvailability(ID)).thenReturn(new PeppolSettings(false, "https://other-smp.example"));
        mvc.perform(get("/sapi/registry/receiving-availability").param("peppolId", ID))
                .andExpect(status().isOk()).andExpect(jsonPath("$.receivingSmpUrl").value("https://other-smp.example"));

        mvc.perform(get("/sapi/registry/receiving-availability").param("peppolId", ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer user-token"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/sapi/registry/receiving-availability").param("peppolId", ID)
                        .header(HttpHeaders.AUTHORIZATION, ""))
                .andExpect(status().isUnauthorized());
        verify(recommand, times(1)).getReceivingAvailability(ID);
    }

    @Test
    void endUserCannotBypassTheAdminCheckThroughTheServiceSettingsEndpoint() throws Exception {
        mvc.perform(put("/sapi/registry/settings").param("peppolId", ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer user-token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiveDocuments\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void failedMoveRestoresScradaAndSavesItsFreshCredentials() throws Exception {
        doThrow(new IllegalStateException("Unavailable")).when(recommand).register(any(), any());

        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(PAYLOAD))
                .andExpect(status().isFailedDependency()).andExpect(jsonPath("$.accessPoint").value("SCRADA"))
                .andExpect(jsonPath("$.peppolActive").value(true));

        assertThat(registry.getVariables()).containsEntry("uuid", "restored");
        assertThat(registry.getReceiveDocuments()).isTrue();
        verify(repository).save(registry);
    }

    @Test
    void failedRecoveryRecordsAnInactiveRegistration() throws Exception {
        doThrow(new IllegalStateException("Unavailable")).when(recommand).register(any(), any());
        doThrow(new IllegalStateException("Unavailable")).when(scrada).register(any(), any());

        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(PAYLOAD))
                .andExpect(status().isFailedDependency()).andExpect(jsonPath("$.accessPoint").value("NONE"))
                .andExpect(jsonPath("$.peppolActive").value(false));

        assertThat(registry.getVariables()).containsEntry("uuid", "original");
        assertThat(registry.getReceiveDocuments()).isFalse();
    }

    @Test
    void providerRequirementsAreCheckedForBothTheMoveAndRecovery() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Example\",\"country\":\"BE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("A signed PDF contract is required for Recommand registration"));
        registry.setAccessPoint(AccessPoint.RECOMMAND);
        mvc.perform(post(MOVE_PATH).param("accessPoint", "SCRADA").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Example\",\"country\":\"BE\"}"))
                .andExpect(status().isBadRequest());
        verify(scrada, never()).unregister(any(), any());
        verify(scrada, never()).register(any(), any());
        verify(recommand, never()).unregister(any(), any());
        verify(recommand, never()).register(any(), any());
    }

    @Test
    void deregistrationFailureStopsTheMove() throws Exception {
        doThrow(new IllegalStateException("Unavailable")).when(scrada).unregister(any(), any());

        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(PAYLOAD))
                .andExpect(status().isInternalServerError());

        verify(recommand, never()).register(any(), any());
        verify(recommand, never()).unregister(any(), any());
        verify(repository, never()).save(any());
        assertThat(registry.getAccessPoint()).isEqualTo(AccessPoint.SCRADA);
    }

    @Test
    void retriesAreHarmlessAndOnlyActiveRegistrationsCanMove() throws Exception {
        registry.setAccessPoint(AccessPoint.RECOMMAND);
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(PAYLOAD)).andExpect(status().isOk());
        registry.setAccessPoint(AccessPoint.NONE);
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(PAYLOAD)).andExpect(status().isConflict());
        registry.setAccessPoint(AccessPoint.E_INVOICE);
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(PAYLOAD)).andExpect(status().isConflict());
        verifyNoInteractions(scrada, recommand);
    }

    @Test
    void movesFromRecommandToTheSelectedProvider() throws Exception {
        registry.setAccessPoint(AccessPoint.RECOMMAND);
        registry.setVariables(Map.of("companyId", "original-company"));

        mvc.perform(post(MOVE_PATH).param("accessPoint", "SCRADA").contentType(MediaType.APPLICATION_JSON).content(PAYLOAD))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessPoint").value("SCRADA"));

        var order = inOrder(recommand, scrada);
        order.verify(recommand).unregister(ID, Map.of("companyId", "original-company"));
        order.verify(scrada).register(eq(registry), any());
        assertThat(registry.getVariables()).containsEntry("uuid", "restored");
    }

    @Test
    void restoresRecommandWhenMovingToScradaFails() throws Exception {
        registry.setAccessPoint(AccessPoint.RECOMMAND);
        registry.setVariables(Map.of("companyId", "original-company"));
        doThrow(new IllegalStateException("Unavailable")).when(scrada).register(any(), any());

        mvc.perform(post(MOVE_PATH).param("accessPoint", "SCRADA").contentType(MediaType.APPLICATION_JSON).content(PAYLOAD))
                .andExpect(status().isFailedDependency()).andExpect(jsonPath("$.accessPoint").value("RECOMMAND"))
                .andExpect(jsonPath("$.peppolActive").value(true));

        assertThat(registry.getVariables()).containsEntry("companyId", "recommand-1");
    }

    @Test
    void usesOtherRegisteredProvidersWithoutRequiringARecommandContract() throws Exception {
        var provider = mock(AccessPointServiceInterface.class);
        when(accessPoints.get(AccessPoint.E_INVOICE)).thenReturn(provider);
        doAnswer(call -> {
            call.<Registry>getArgument(0).setVariables(Map.of("tenantId", "tenant-1"));
            call.<Registry>getArgument(0).setReceiveDocuments(true);
            return null;
        }).when(provider).register(any(), any());

        mvc.perform(post(MOVE_PATH).param("accessPoint", "E_INVOICE").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Example\",\"country\":\"BE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessPoint").value("E_INVOICE"));

        assertThat(registry.getVariables()).containsEntry("tenantId", "tenant-1");
    }

    @Test
    void rejectsInvalidAndUnavailableTargetsBeforeDeregistration() throws Exception {
        for (String target : new String[]{"NONE", "LOOPBACK", "UNKNOWN", "E_INVOICE", ""}) {
            mvc.perform(post(MOVE_PATH).param("accessPoint", target).contentType(MediaType.APPLICATION_JSON).content(PAYLOAD))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(scrada, recommand);
    }

    @Test
    void usesConfiguredDefaultWhenAccessPointIsOmitted() throws Exception {
        registry.setAccessPoint(AccessPoint.RECOMMAND);
        registry.setVariables(Map.of("companyId", "original-company"));

        mvc.perform(post(MOVE_PATH).contentType(MediaType.APPLICATION_JSON).content(PAYLOAD))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessPoint").value("SCRADA"));

        verify(recommand).unregister(ID, Map.of("companyId", "original-company"));
        verify(scrada).register(eq(registry), any());
    }

    @Test
    void migrationRequiresServiceCredentials() throws Exception {
        mvc.perform(post(PATH).header(HttpHeaders.AUTHORIZATION, "")
                        .contentType(MediaType.APPLICATION_JSON).content(PAYLOAD))
                .andExpect(status().is4xxClientError());
        mvc.perform(post(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON).content(PAYLOAD))
                .andExpect(status().isForbidden());
        mvc.perform(post("/lapi/registry/" + ID + "/move")
                        .contentType(MediaType.APPLICATION_JSON).content(PAYLOAD))
                .andExpect(status().isForbidden());
        verifyNoInteractions(scrada, recommand);
    }

    @Test
    void honorsRequirementsOfAnyProviderThroughTheInterface() throws Exception {
        var provider = mock(AccessPointServiceInterface.class);
        when(accessPoints.get(AccessPoint.E_INVOICE)).thenReturn(provider);
        doThrow(new IllegalArgumentException("Provider-specific requirement"))
                .when(provider).validateRegistration(eq(ID), any());

        mvc.perform(post(MOVE_PATH).param("accessPoint", "E_INVOICE").contentType(MediaType.APPLICATION_JSON).content(PAYLOAD))
                .andExpect(status().isBadRequest()).andExpect(content().string("Provider-specific requirement"));

        registry.setAccessPoint(AccessPoint.E_INVOICE);
        mvc.perform(post(MOVE_PATH).param("accessPoint", "SCRADA").contentType(MediaType.APPLICATION_JSON).content(PAYLOAD))
                .andExpect(status().isBadRequest()).andExpect(content().string("Provider-specific requirement"));

        verify(scrada, never()).unregister(any(), any());
        verify(provider, never()).unregister(any(), any());
        verify(provider, never()).register(any(), any());
    }

    @Test
    void failedSaveCleansUpRecommandBeforeRestoringScrada() throws Exception {
        when(repository.save(any())).thenThrow(new IllegalStateException("Database unavailable")).thenReturn(registry);

        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(PAYLOAD))
                .andExpect(status().isFailedDependency()).andExpect(jsonPath("$.accessPoint").value("SCRADA"));

        var order = inOrder(recommand, scrada);
        order.verify(scrada).unregister(ID, Map.of("uuid", "original"));
        order.verify(recommand).register(eq(registry), any());
        order.verify(recommand).unregister(ID, Map.of("companyId", "recommand-1"));
        order.verify(scrada).register(eq(registry), any());
    }

    @Configuration
    @EnableWebSecurity
    @EnableWebMvc
    static class LocalConfig {
        @Bean RegistryRepository repository() { return mock(RegistryRepository.class); }
        @Bean ScradaService scrada() { return mock(ScradaService.class); }
        @Bean RecommandService recommand() { return mock(RecommandService.class); }
        @Bean AccessPointServiceRegistry accessPoints() { return mock(AccessPointServiceRegistry.class); }
        @Bean RegistryService registryService() { return mock(RegistryService.class); }
        @Bean JwtDecoder decoder() {
            var decoder = mock(JwtDecoder.class);
            when(decoder.decode("service-token")).thenReturn(Jwt.withTokenValue("service-token")
                    .header("alg", "RS256").subject("kyc-service").claim("scope", List.of("service")).build());
            when(decoder.decode("user-token")).thenReturn(Jwt.withTokenValue("user-token")
                    .header("alg", "RS256").subject("user").claim("uid", "user-id").build());
            return decoder;
        }
        @Bean CorsConfigurationSource corsConfigurationSource() { return request -> new CorsConfiguration(); }
        @Bean GlobalExceptionHandler errors() { return new GlobalExceptionHandler(); }
        @Bean RegistryController controller(RegistryRepository repository, AccessPointServiceRegistry accessPoints, RegistryService registryService) {
            return new RegistryController(mock(AppLinkService.class), registryService, repository, accessPoints, recommand());
        }
        @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
            return new SecurityConfig().securityFilterChain(http);
        }
    }
}
