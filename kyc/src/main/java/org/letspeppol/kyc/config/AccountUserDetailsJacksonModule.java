package org.letspeppol.kyc.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.module.SimpleModule;

/**
 * Jackson module that registers custom types with Spring Security's ObjectMapper allowlist,
 * required for JdbcOAuth2AuthorizationService serialization.
 */
public class AccountUserDetailsJacksonModule extends SimpleModule {

    @Override
    public void setupModule(SetupContext context) {
        context.setMixInAnnotations(AccountUserDetails.class, AccountUserDetailsMixin.class);
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
    @JsonAutoDetect(
            fieldVisibility = JsonAutoDetect.Visibility.ANY,
            getterVisibility = JsonAutoDetect.Visibility.NONE,
            isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    private static abstract class AccountUserDetailsMixin {}
}
