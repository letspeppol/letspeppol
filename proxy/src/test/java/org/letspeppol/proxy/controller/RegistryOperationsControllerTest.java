package org.letspeppol.proxy.controller;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.*;
import org.letspeppol.proxy.config.SecurityConfig;
import org.letspeppol.proxy.exception.GlobalExceptionHandler;
import org.letspeppol.proxy.model.AccessPoint;
import org.letspeppol.proxy.model.Registry;
import org.letspeppol.proxy.repository.RegistryRepository;
import org.letspeppol.proxy.service.AccessPointServiceRegistry;
import org.letspeppol.proxy.service.AccessPointServiceInterface;
import org.letspeppol.proxy.service.RecommandService;
import org.letspeppol.proxy.service.ScradaService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RegistryOperationsControllerTest {
    private static final String ID = "0208:0123456789";
    private static final String MOVE_PATH = "/lapi/registry/" + ID + "/move";
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
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    @AfterAll
    static void stop() {
        if (context != null) context.close();
    }

    @BeforeEach
    void setUp() {
        reset(repository, accessPoints, scrada, recommand);
        when(accessPoints.get(AccessPoint.SCRADA)).thenReturn(scrada);
        when(accessPoints.get(AccessPoint.RECOMMAND)).thenReturn(recommand);
        doCallRealMethod().when(scrada).validateRegistration(anyString(), any());
        doCallRealMethod().when(recommand).validateRegistration(anyString(), any());
        registry = new Registry(ID, AccessPoint.SCRADA, Map.of("uuid", "original"));
        when(repository.findById(ID)).thenReturn(Optional.of(registry));
        when(repository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(recommand.register(eq(ID), any())).thenReturn(Map.of("companyId", "recommand-1"));
        when(scrada.register(eq(ID), any())).thenReturn(Map.of("uuid", "restored"));
    }

    @Test
    void operatorCanMoveWithoutAUserTokenOrCsrfToken() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(PAYLOAD))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessPoint").value("RECOMMAND"));

        assertThat(registry.getVariables()).containsEntry("companyId", "recommand-1");
        var order = inOrder(scrada, recommand);
        order.verify(recommand).validateRegistration(eq(ID), any());
        order.verify(scrada).validateRegistration(eq(ID), any());
        order.verify(scrada).unregister(ID, Map.of("uuid", "original"));
        order.verify(recommand).register(eq(ID), any());
        verify(scrada, never()).register(any(), any());
    }

    @Test
    void failedMoveRestoresScradaAndSavesItsFreshCredentials() throws Exception {
        when(recommand.register(eq(ID), any())).thenThrow(new IllegalStateException("Unavailable"));

        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(PAYLOAD))
                .andExpect(status().isFailedDependency()).andExpect(jsonPath("$.accessPoint").value("SCRADA"))
                .andExpect(jsonPath("$.peppolActive").value(true));

        assertThat(registry.getVariables()).containsEntry("uuid", "restored");
        verify(repository).save(registry);
    }

    @Test
    void failedRecoveryRecordsAnInactiveRegistration() throws Exception {
        when(recommand.register(eq(ID), any())).thenThrow(new IllegalStateException("Unavailable"));
        when(scrada.register(eq(ID), any())).thenThrow(new IllegalStateException("Unavailable"));

        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(PAYLOAD))
                .andExpect(status().isFailedDependency()).andExpect(jsonPath("$.accessPoint").value("NONE"))
                .andExpect(jsonPath("$.peppolActive").value(false));

        assertThat(registry.getVariables()).containsEntry("uuid", "original");
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
        order.verify(scrada).register(eq(ID), any());
        assertThat(registry.getVariables()).containsEntry("uuid", "restored");
    }

    @Test
    void restoresRecommandWhenMovingToScradaFails() throws Exception {
        registry.setAccessPoint(AccessPoint.RECOMMAND);
        registry.setVariables(Map.of("companyId", "original-company"));
        when(scrada.register(eq(ID), any())).thenThrow(new IllegalStateException("Unavailable"));

        mvc.perform(post(MOVE_PATH).param("accessPoint", "SCRADA").contentType(MediaType.APPLICATION_JSON).content(PAYLOAD))
                .andExpect(status().isFailedDependency()).andExpect(jsonPath("$.accessPoint").value("RECOMMAND"))
                .andExpect(jsonPath("$.peppolActive").value(true));

        assertThat(registry.getVariables()).containsEntry("companyId", "recommand-1");
    }

    @Test
    void usesOtherRegisteredProvidersWithoutRequiringARecommandContract() throws Exception {
        var provider = mock(AccessPointServiceInterface.class);
        when(accessPoints.get(AccessPoint.E_INVOICE)).thenReturn(provider);
        when(provider.register(eq(ID), any())).thenReturn(Map.of("tenantId", "tenant-1"));

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
        verify(scrada).register(eq(ID), any());
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
        order.verify(recommand).register(eq(ID), any());
        order.verify(recommand).unregister(ID, Map.of("companyId", "recommand-1"));
        order.verify(scrada).register(eq(ID), any());
    }

    @Configuration
    @EnableWebSecurity
    @EnableWebMvc
    static class LocalConfig {
        @Bean RegistryRepository repository() { return mock(RegistryRepository.class); }
        @Bean ScradaService scrada() { return mock(ScradaService.class); }
        @Bean RecommandService recommand() { return mock(RecommandService.class); }
        @Bean AccessPointServiceRegistry accessPoints() { return mock(AccessPointServiceRegistry.class); }
        @Bean JwtDecoder decoder() { return mock(JwtDecoder.class); }
        @Bean CorsConfigurationSource corsConfigurationSource() { return request -> new CorsConfiguration(); }
        @Bean GlobalExceptionHandler errors() { return new GlobalExceptionHandler(); }
        @Bean RegistryOperationsController controller(RegistryRepository repository, AccessPointServiceRegistry accessPoints) {
            return new RegistryOperationsController(repository, accessPoints);
        }
        @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
            return new SecurityConfig().securityFilterChain(http);
        }
    }
}
