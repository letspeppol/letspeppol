package org.letspeppol.kyc.config;

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

    @com.fasterxml.jackson.annotation.JsonTypeInfo(use = com.fasterxml.jackson.annotation.JsonTypeInfo.Id.CLASS)
    @com.fasterxml.jackson.annotation.JsonAutoDetect(
            fieldVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY,
            getterVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE,
            isGetterVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE)
    private static abstract class AccountUserDetailsMixin {}
}
