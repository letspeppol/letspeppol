package org.letspeppol.kyc.service;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.letspeppol.kyc.dto.CompanyResponse;
import org.letspeppol.kyc.dto.DirectorDto;
import org.letspeppol.kyc.dto.PeppolIdDto;
import org.letspeppol.kyc.exception.KycErrorCodes;
import org.letspeppol.kyc.exception.KycException;
import org.letspeppol.kyc.util.CompanyNumberUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class KboLookupService {

    @Autowired
    @Qualifier("KboWebClient")
    private WebClient kboWebClient;

    public final static String EAS_ONDERNEMINGSNUMMER = "0208";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    public Optional<CompanyResponse> findCompany(String peppolId) {
        PeppolIdDto peppolIdDto = PeppolIdDto.parse(peppolId);
        if (!EAS_ONDERNEMINGSNUMMER.equals(peppolIdDto.scheme())) { //TODO : split for other countries
            throw new IllegalArgumentException("Only Belgian (0208) companies are implemented");
        }
        String normalizedVat = CompanyNumberUtil.normalizeVat(peppolIdDto.value());
        String html;
        try {
            html = fetchEnterpriseHtml(normalizedVat);
        } catch (Exception e) {
            log.error(e.getMessage());
            return Optional.empty();
        }

        String name = getCompanyNameFromHtml(html);
        if (name == null || name.isEmpty()) {
            return Optional.empty();
        }
        Optional<Address> address = parseAddressFromHtml(html);
        if (address.isEmpty()) {
            log.error("Could not parse address from html for company {} {}", normalizedVat, name);
            throw new KycException(KycErrorCodes.KBO_PARSE_ADDRESS_FAILED);
        }
        List<DirectorDto> directors = parseDirectorsFromHtml(html);
        if (directors.isEmpty()) {
            log.error("Could not parse functions from html for company {} {}", normalizedVat, name);
            throw new KycException(KycErrorCodes.KBO_PARSE_DIRECTORS_FAILED);
        }

        CompanyResponse companyResponse = new CompanyResponse(
                null,
                peppolId,
                "BE"+normalizedVat,
                name,
                address.get().street,
                address.get().houseNumber,
                address.get().city,
                address.get().postalCode,
                directors
        );
        return Optional.of(companyResponse);
    }

    private String fetchEnterpriseHtml(String vat) {
        try {
            return kboWebClient.get()
                    .uri("/{vat}?s=ent&lang=nl", vat)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, resp -> Mono.error(new KycException(KycErrorCodes.KBO_NOT_FOUND)))
                    .onStatus(HttpStatusCode::is5xxServerError, resp -> Mono.error(new KycException(KycErrorCodes.KBO_SERVICE_ERROR)))
                    .bodyToMono(String.class)
                    .block(TIMEOUT);
        } catch (WebClientResponseException ex) {
            throw new IllegalStateException("Failed to retrieve KBO page: " + ex.getStatusCode(), ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to retrieve KBO page", ex);
        }
    }

    public String getCompanyNameFromHtml(String html) {
        if (html == null || html.isEmpty()) {
            return null;
        }
        Document doc = Jsoup.parse(html);
        Element kader = doc.getElementById("benamingenKader");
        if (kader == null) {
            return null;
        }
        Element strong = kader.selectFirst("strong");
        return strong != null ? strong.text().trim() : null;
    }

    List<DirectorDto> parseDirectorsFromHtml(String html) {
        List<String> result = new ArrayList<>();
        if (html == null || html.isEmpty()) {
            return List.of();
        }
        Document doc = Jsoup.parse(html);
        Element functieKader = doc.getElementById("functieKader");
        if (functieKader == null) {
            return List.of();
        }
        Element table = functieKader.selectFirst("table");
        if (table == null) {
            return List.of();
        }
        Elements rows = table.select("tr");
        for (Element row : rows) {
            Elements tds = row.select("> td");
            if (tds.size() < 2) continue;
            String raw = tds.get(1).text(); // collapse text
            String name = cleanPersonName(raw);
            if (!name.isBlank()) {
                result.add(name);
            }
        }
        // De-duplicate while preserving order
        return result.stream().distinct().map(s -> new DirectorDto(null, s)).toList();
    }

    private String cleanPersonName(String raw) {
        if (raw == null) {
            return "";
        }
        String name = raw.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
        int commaIdx = name.indexOf(',');
        if (commaIdx > 0) {
            String last = name.substring(0, commaIdx).trim();
            String first = name.substring(commaIdx + 1).trim();
            if (!last.isEmpty() && !first.isEmpty()) {
                return last + " " + first;
            }
        }
        return name;
    }

    public Optional<Address> parseAddressFromHtml(String html) {
        if (html == null || html.isEmpty()) {
            return null;
        }
        Document doc = Jsoup.parse(html);
        Element addressElem = doc.getElementById("adrescontactstraatnr");
        if (addressElem == null) {
            return Optional.empty();
        }
        String rawHtml = addressElem.html();
        String[] lines = rawHtml.split("<br>");
        String street = "";
        String houseNumber = "";
        String postalCode = "";
        String city = "";
        String extraInfo = "";
        if (lines.length > 0) {
            String streetLine = Jsoup.parse(lines[0]).text().trim();
            Matcher m = Pattern.compile("^(.*?)(\\s+)([0-9]+[A-Za-z0-9\\-]*)$").matcher(streetLine);
            if (m.find()) {
                street = m.group(1).trim();
                houseNumber = m.group(3).trim();
            } else {
                street = streetLine;
            }
        }
        if (lines.length > 1) {
            String[] pcCity = Jsoup.parse(lines[1]).text().trim().split(" ", 2);
            if (pcCity.length == 2) {
                postalCode = pcCity[0];
                city = pcCity[1];
            }
        }
        if (rawHtml.contains("Extra adresinfo:")) {
            int idx = rawHtml.indexOf("Extra adresinfo:");
            extraInfo = Jsoup.parse(rawHtml.substring(idx)).text().replace("Extra adresinfo:", "").trim();
        }
        return Optional.of(new Address(street, houseNumber, postalCode, city, extraInfo));
    }

    record Address(
        String street,
        String houseNumber,
        String postalCode,
        String city,
        String extraInfo
    ){}

}
