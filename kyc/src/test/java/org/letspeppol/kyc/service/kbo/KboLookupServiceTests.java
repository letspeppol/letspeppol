package org.letspeppol.kyc.service.kbo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class KboLookupServiceTests {

    private final KboLookupService service = new KboLookupService();

    @Test
    void getVatNumberFromHtmlReturnsBelgianVatNumberWhenVatQualificationExists() {
        String html = """
                <html><body>
                <div class="kader" id="qualificationKader"><p></p><div class="I">Hoedanigheden</div><p></p><p><strong>Onderworpen aan btw</strong><br>sinds&nbsp;2 september 2019</p><p><strong>Inschrijvingsplichtige onderneming</strong><br>sinds&nbsp;2 september 2019</p></div>
                </body></html>
                """;

        assertEquals("BE0123456789", service.getVatNumberFromHtml(html, "0123456789"));
    }

    @Test
    void getVatNumberFromHtmlReturnsNullWhenVatQualificationIsOutsideQualificationKader() {
        String html = """
                <html><body>
                <div id="otherKader"><strong>Onderworpen aan btw</strong></div>
                <div class="kader" id="qualificationKader"><p></p><div class="I">Hoedanigheden</div><p></p><p><strong>Inschrijvingsplichtige onderneming</strong><br>sinds&nbsp;2 september 2019</p></div>
                </body></html>
                """;

        assertNull(service.getVatNumberFromHtml(html, "0123456789"));
    }
}
