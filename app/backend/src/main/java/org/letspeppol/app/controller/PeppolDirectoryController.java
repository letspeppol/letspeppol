package org.letspeppol.app.controller;

import org.letspeppol.app.exception.AppErrorCodes;
import org.letspeppol.app.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Slf4j
@RestController
@RequestMapping("/api/peppol-directory")
public class PeppolDirectoryController {

    @Autowired
    @Qualifier("PeppolDirectoryWebClient")
    private WebClient webClient;

    @GetMapping
    public ResponseEntity find(@RequestParam(name = "name", required = false) String name, @RequestParam(name = "participant", required = false) String participant) {

        WebClient.RequestHeadersSpec<?> requestSpec;
        if (name != null) {
            requestSpec = webClient.get().uri("/search/1.0/json?name={name}", name);
        } else {
            requestSpec = webClient.get().uri("/search/1.0/json?q={participant}", participant);
        }

        String json = requestSpec
                .retrieve()
                .onStatus(
                        status -> status.value() == 429,
                        resp -> {
                            log.warn("Peppol directory rate limit reached (429).");
                            return Mono.error(new AppException(AppErrorCodes.PEPPOL_DIR_RATE_LIMIT_ERROR));
                        })
                .onStatus(
                        HttpStatusCode::is4xxClientError,
                        resp -> Mono.error(new AppException(AppErrorCodes.PEPPOL_DIR_400_ERROR)))
                .onStatus(
                        HttpStatusCode::is5xxServerError,
                        resp -> Mono.error(new AppException(AppErrorCodes.PEPPOL_DIR_500_ERROR)))
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(10));
        return ResponseEntity.ok(json);
    }

    private String encodeValue(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

}
