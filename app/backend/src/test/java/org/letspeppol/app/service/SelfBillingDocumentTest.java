package org.letspeppol.app.service;

import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.Counter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.letspeppol.app.dto.UblDocumentDto;
import org.letspeppol.app.model.Company;
import org.letspeppol.app.model.Document;
import org.letspeppol.app.model.DocumentDirection;
import org.letspeppol.app.model.DocumentType;
import org.letspeppol.app.repository.CompanyRepository;
import org.letspeppol.app.repository.DocumentRepository;
import org.letspeppol.app.util.UblParser;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SelfBillingDocumentTest {
    @ParameterizedTest
    @CsvSource({"invoice.xml, SELF_BILLING_INVOICE, Self-billed invoice", "credit-note.xml, SELF_BILLING_CREDIT_NOTE, Self-billed credit note"})
    void receivesUploadsSendsAndRendersSelfBilledDocuments(String fixture, DocumentType type, String title) throws Exception {
        String xml;
        try (var input = getClass().getResourceAsStream("/self-billing/" + fixture)) {
            xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        ValidationService validator = new ValidationService();
        var validation = validator.validateUblXml(xml);
        assertThat(validation.vesId()).contains(type.isInvoice() ? "ArtifactID=invoice-self-billing;" : "ArtifactID=creditnote-self-billing;");
        assertThat(validation.isValid()).as(validation.errors().toString()).isTrue();

        var parties = UblParser.parsePeppolParties(xml);
        CompanyRepository companies = mock(CompanyRepository.class);
        DocumentRepository documents = mock(DocumentRepository.class);
        Company company = mock(Company.class);
        when(companies.findByPeppolId(parties.receiver())).thenReturn(Optional.of(company));
        when(documents.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var pdfService = new UblInvoicePdfService(null);
        var service = new DocumentService(companies, documents, validator, mock(NotificationService.class),
                pdfService, new JsonMapper(), mock(WebClient.class), mock(WebClient.class),
                mock(Counter.class), mock(Counter.class), mock(Counter.class));
        service.create(new UblDocumentDto(UUID.randomUUID(), DocumentDirection.INCOMING, type,
                parties.receiver(), parties.sender(), Instant.now(), null, Instant.now(), null, xml));
        var saved = org.mockito.ArgumentCaptor.forClass(Document.class);
        verify(documents).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo(type);
        assertThat(saved.getValue().getPartnerName()).isEqualTo("BuyerTradingName AS");
        assertThat(saved.getValue().getOwnerPeppolId()).isEqualTo(parties.receiver());
        assertThat(saved.getValue().getPartnerPeppolId()).isEqualTo(parties.sender());
        assertThat(saved.getValue().getUbl()).isEqualTo(xml);

        try (var pdf = PDDocument.load(pdfService.toPdf(xml))) {
            String text = new PDFTextStripper().getText(pdf);
            assertThat(text).contains(title, "SupplierTradingName Ltd.", "BuyerTradingName AS", "Snippet1", "item name 2", "1656.25");
        }

        when(companies.findByPeppolId(parties.sender())).thenReturn(Optional.of(company));
        var uploaded = service.createFromUbl(parties.sender(), xml, true, null, true, null);
        assertThat(uploaded.type()).isEqualTo(type);
        assertThat(uploaded.ubl()).isEqualTo(xml);
        assertThat(uploaded.direction()).isEqualTo(DocumentDirection.OUTGOING);
        assertThat(uploaded.ownerPeppolId()).isEqualTo(parties.sender());
        assertThat(uploaded.partnerPeppolId()).isEqualTo(parties.receiver());
        assertThat(uploaded.createdExternally()).isTrue();

        var uploadedDocument = org.mockito.ArgumentCaptor.forClass(Document.class);
        verify(documents, times(2)).save(uploadedDocument.capture());
        Document draft = uploadedDocument.getValue();
        draft.setId(UUID.randomUUID());
        when(documents.findById(draft.getId())).thenReturn(Optional.of(draft));
        var updated = service.update(parties.sender(), draft.getId(), xml, true, null, null);
        assertThat(updated.type()).isEqualTo(type);
        assertThat(updated.ubl()).isEqualTo(xml);
        assertThat(updated.draftedOn()).isNotNull();

        var submitted = new AtomicReference<UblDocumentDto>();
        var mapper = new JsonMapper();
        var proxyResponse = new UblDocumentDto(draft.getId(), DocumentDirection.OUTGOING, type,
                parties.sender(), parties.receiver(), Instant.now(), null, null, null, xml);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/sapi/document", exchange -> {
            submitted.set(mapper.readValue(exchange.getRequestBody().readAllBytes(), UblDocumentDto.class));
            byte[] response = mapper.writeValueAsBytes(proxyResponse);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var sendingService = new DocumentService(companies, documents, validator, mock(NotificationService.class),
                    pdfService, mapper, WebClient.create("http://127.0.0.1:" + server.getAddress().getPort()),
                    mock(WebClient.class), mock(Counter.class), mock(Counter.class), mock(Counter.class));
            var sent = sendingService.send(parties.sender(), draft.getId(), null, "test-token");
            assertThat(submitted.get().type()).isEqualTo(type);
            assertThat(submitted.get().ubl()).isEqualTo(xml);
            assertThat(sent.ubl()).isEqualTo(xml);
            assertThat(sent.draftedOn()).isNull();
            verify(company, never()).setLastInvoiceReference(any());
            verify(company, never()).setLastCreditNoteReference(any());
        } finally {
            server.stop(0);
        }
    }
}
