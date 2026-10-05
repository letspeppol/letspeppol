package org.letspeppol.app.config;

import org.junit.jupiter.api.Test;
import org.letspeppol.app.service.AccountantService;
import org.letspeppol.app.service.CompanyService;
import org.letspeppol.app.service.DocumentService;
import org.letspeppol.app.service.PartnerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SapiPermissionIntegrationTest {

    private static final String PEPPOL_ID = "0208:0123456789";
    private static final String DOCUMENT = "/sapi/document/" + UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private DocumentService documentService;

    @MockitoBean
    private CompanyService companyService;

    @MockitoBean
    private PartnerService partnerService;

    @MockitoBean
    private AccountantService accountantService;

    @Test
    void requestWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get(DOCUMENT)).andExpect(status().isUnauthorized());
    }

    @Test
    void readOnlyUserReadsButCannotSend() throws Exception {
        String token = token("USER", 1, true);

        perform(get(DOCUMENT), token).andExpect(status().isOk());
        expectMissingPermission(perform(put(DOCUMENT + "/send"), token));
        expectMissingPermission(perform(put(DOCUMENT + "/paid"), token));
    }

    @Test
    void draftingUserSavesDraftsButCannotSendThroughTheDraftFlag() throws Exception {
        String token = token("USER", 3, true);

        perform(post("/sapi/document").param("draft", "true").content("<Invoice/>"), token).andExpect(status().isOk());
        expectMissingPermission(perform(post("/sapi/document").param("draft", "false").content("<Invoice/>"), token));
        expectMissingPermission(perform(post("/sapi/document").content("<Invoice/>"), token));
        expectMissingPermission(perform(put(DOCUMENT).param("draft", "false").content("<Invoice/>"), token));
    }

    @Test
    void updateTellsTheServiceWhetherTheCallerMaySend() throws Exception {
        perform(put(DOCUMENT).param("draft", "true").content("<Invoice/>"), token("USER", 3, true)).andExpect(status().isOk());
        verify(documentService).update(anyString(), any(), anyString(), eq(true), any(), anyString(), eq(false));

        perform(put(DOCUMENT).param("draft", "true").content("<Invoice/>"), token("USER", 7, true)).andExpect(status().isOk());
        verify(documentService).update(anyString(), any(), anyString(), eq(true), any(), anyString(), eq(true));
    }

    @Test
    void draftingUserOfAnInactiveCompanyIsForcedIntoDraftInsteadOfRefused() throws Exception {
        String token = token("USER", 3, false);

        perform(post("/sapi/document").param("draft", "false").content("<Invoice/>"), token).andExpect(status().isOk());
    }

    @Test
    void sendingUserSends() throws Exception {
        String token = token("USER", 7, true);

        perform(post("/sapi/document").param("draft", "false").content("<Invoice/>"), token).andExpect(status().isOk());
        perform(put(DOCUMENT + "/send"), token).andExpect(status().isOk());
    }

    @Test
    void memberWithoutPermissionsSeesTheCompanyButNoMasterData() throws Exception {
        String token = token("USER", 0, true);

        perform(get("/sapi/company"), token).andExpect(status().isOk());
        expectMissingPermission(perform(get("/sapi/partner"), token));
        expectMissingPermission(perform(get(DOCUMENT), token));
    }

    @Test
    void adminTokenMintedBeforeTheClaimExistedKeepsFullAccess() throws Exception {
        String token = token("ADMIN", null, true);

        perform(put(DOCUMENT + "/send"), token).andExpect(status().isOk());
        perform(get("/sapi/partner"), token).andExpect(status().isOk());
        perform(post("/sapi/accountant/confirm-customer-link").param("token", "link-token"), token).andExpect(status().isOk());
    }

    @Test
    void userWithEveryPermissionStillCannotUseAdminRoutes() throws Exception {
        String token = token("USER", 255, true);

        expectMissingPermission(perform(post("/sapi/accountant/confirm-customer-link").param("token", "link-token"), token));
    }

    @Test
    void refusalRaisedInsideAServiceIsForbiddenToo() throws Exception {
        String token = token("USER", 128, true);
        when(companyService.update(any(), anyBoolean(), anyString(), eq(false)))
                .thenThrow(new AccessDeniedException("Only an administrator can change email notifications"));

        expectMissingPermission(perform(put("/sapi/company")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"peppolId":"%s","peppolActive":true,"enableEmailNotification":true,
                         "addAttachmentToNotification":false,"addPdfToSendingInvoice":false}
                        """.formatted(PEPPOL_ID)), token));
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private static void expectMissingPermission(ResultActions result) throws Exception {
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("MISSING_PERMISSION"));
    }

    private String token(String accountType, Integer permissionMask, boolean peppolActive) {
        String token = UUID.randomUUID().toString();
        Jwt.Builder jwt = Jwt.withTokenValue(token).header("alg", "RS256").subject("user")
                .claim(SecurityConfig.UID, UUID.randomUUID().toString())
                .claim(SecurityConfig.ACCOUNT_TYPE, accountType)
                .claim(SecurityConfig.PEPPOL_ID, PEPPOL_ID)
                .claim(SecurityConfig.PEPPOL_ACTIVE, peppolActive);
        if (permissionMask != null) {
            jwt.claim(SecurityConfig.PERMISSION_MASK, permissionMask);
        }
        when(jwtDecoder.decode(token)).thenReturn(jwt.build());
        return token;
    }
}
