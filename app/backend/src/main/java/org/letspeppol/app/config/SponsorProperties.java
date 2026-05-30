package org.letspeppol.app.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "sponsors")
@Getter
@Setter
public class SponsorProperties {

    private String emailText = "We are supported by";
    private String baseUrl = "https://letspeppol.org/img/supporters/";
    private List<Sponsor> list = new ArrayList<>();

    @Getter
    @Setter
    public static class Sponsor {
        private String name;
        private String logo;
        private String url;
    }
}

