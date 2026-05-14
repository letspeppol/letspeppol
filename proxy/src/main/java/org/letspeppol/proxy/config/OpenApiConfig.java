package org.letspeppol.proxy.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI proxyOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Let’s Peppol Proxy API")
                        .version("v1")
                        .description("Proxy, registry, monitoring, and document transport endpoints.")
                        .contact(new Contact().name("Let’s Peppol")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
