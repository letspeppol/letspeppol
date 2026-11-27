/* Local integration-test helper only. Not part of any production artifact. */
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.w3c.dom.*;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.*;
import java.util.*;

@SuppressWarnings("deprecation")
public class LabSupport {
    static final ObjectMapper JSON = new ObjectMapper();
    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    static final String CBC = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";
    static final String INVOICE = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2";
    static final String SBD = "http://www.unece.org/cefact/namespaces/StandardBusinessDocumentHeader";
    static final String EB = "http://docs.oasis-open.org/ebxml-msg/ebms/v3.0/ns/core/200704/";
    static final String ISSUER = "https://kyc.phoss.test";

    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "serve" -> serve();
            case "smoke" -> smoke(Path.of(args[1]), args[2], args[3]);
            default -> throw new IllegalArgumentException("serve | smoke <evidence-dir> <sender> <receiver>");
        }
    }

    static void serve() throws Exception {
        // Ephemeral JWT fixture key, unrelated to the existing PHOSS AP/SMP certificates.
        RSAKey key = new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/jwks", x -> {
            byte[] bytes = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            x.getResponseHeaders().set("Content-Type", "application/json");
            x.sendResponseHeaders(200, bytes.length); x.getResponseBody().write(bytes); x.close();
        });
        // Deliberately unrestricted token minting ONLY inside the isolated test network.
        server.createContext("/token", x -> {
            try {
                String participant = URLDecoder.decode(Objects.toString(x.getRequestURI().getRawQuery(), "9915:phoss-ap-test-sender"), StandardCharsets.UTF_8);
                Instant now = Instant.now();
                JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer(ISSUER).audience("letspeppol-api")
                        .subject("local-phoss-test").issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(900)))
                        .claim("scope", List.of("service", "openid")).claim("accountType", "ADMIN")
                        .claim("uid", "37e7096d-00e1-4a92-9179-b0afcd2057d4").claim("peppolId", participant).build();
                SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
                jwt.sign(new RSASSASigner(key));
                byte[] bytes = jwt.serialize().getBytes(StandardCharsets.UTF_8);
                x.sendResponseHeaders(200, bytes.length); x.getResponseBody().write(bytes); x.close();
            } catch (Exception e) { x.sendResponseHeaders(500, -1); x.close(); }
        });
        server.start();
        System.out.println("LOCAL TEST ONLY: JWT fixture listening on internal network port 8080");
    }

    static void smoke(Path evidence, String sender, String receiver) throws Exception {
        require(!sender.equals(receiver), "Distinct participants required: otherwise Proxy bypasses the AP");
        require(sender.matches("9915:[A-Za-z0-9._-]+") && receiver.matches("9915:[A-Za-z0-9._-]+"), "Only local 9915 test participants are supported");
        require(!Files.exists(evidence), "Use a fresh evidence directory; never overwrite previous results");
        Files.createDirectories(evidence);
        String documentId = "busdox-docid-qns::" + INVOICE + "::Invoice##urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:billing:3.0::2.1";
        String metadata = request("GET", "http://smp:8080/" + encode("iso6523-actorid-upis::" + receiver)
                + "/services/" + encode(documentId), null, null, null);
        require(metadata.contains("http://ap:8080/as4"), "Receiver must advertise the existing local AP");
        Files.writeString(evidence.resolve("smp-metadata.xml"), metadata);
        String token = request("GET", "http://auth:8080/token?" + encode(sender), null, null, null);
        String proxy = "http://localhost:8086";
        String availability = request("GET", proxy + "/sapi/registry/receiving-availability?peppolId=" + encode(receiver), token, null, null);
        require("Configured PHOSS SMP".equals(JSON.readTree(availability).path("receivingSmpUrl").asText()),
                "Proxy must look up the existing receiver directly in local SMP");
        Files.writeString(evidence.resolve("proxy-smp-availability.json"), availability);
        // Production security chain stays enabled; anonymous registry access must be refused.
        var anonymous = HTTP.send(HttpRequest.newBuilder(URI.create(proxy + "/sapi/registry?peppolId=" + encode(sender))).GET().build(), HttpResponse.BodyHandlers.ofString());
        require(anonymous.statusCode() == 401, "Proxy must reject anonymous registry requests");
        String registration = request("POST", proxy + "/sapi/registry?peppolId=" + encode(sender), token,
                "application/json", "{\"name\":\"Local PHOSS integration sender\",\"country\":\"GB\",\"language\":\"en\"}");
        Files.writeString(evidence.resolve("registration.json"), registration);
        require("PHOSS".equals(JSON.readTree(registration).path("accessPoint").asText()), "Sender must use PHOSS");

        Document sample = parse(Files.readString(Path.of("/support/sample.xml")));
        Document invoice = parse("<Invoice xmlns=\"" + INVOICE + "\"/>");
        invoice.replaceChild(invoice.importNode(sample.getElementsByTagNameNS(INVOICE, "Invoice").item(0), true), invoice.getDocumentElement());
        NodeList endpoints = invoice.getElementsByTagNameNS(CBC, "EndpointID");
        setEndpoint((Element) endpoints.item(0), sender);
        setEndpoint((Element) endpoints.item(1), receiver);
        String invoiceId = "LP-PHOSS-" + UUID.randomUUID();
        child(invoice.getDocumentElement(), CBC, "ID").setTextContent(invoiceId);
        child(invoice.getDocumentElement(), CBC, "IssueDate").setTextContent(LocalDate.now(ZoneOffset.UTC).toString());
        child(invoice.getDocumentElement(), CBC, "DueDate").setTextContent(LocalDate.now(ZoneOffset.UTC).plusDays(30).toString());
        String ubl = xml(invoice);
        Files.writeString(evidence.resolve("submitted-ubl.xml"), ubl);
        String uuid = UUID.randomUUID().toString();
        String payload = JSON.writeValueAsString(Map.of("id", uuid, "type", "INVOICE", "direction", "OUTGOING",
                "ownerPeppolId", sender, "partnerPeppolId", receiver, "ubl", ubl));
        String created = request("POST", proxy + "/sapi/document", token, "application/json", payload);
        Files.writeString(evidence.resolve("proxy-created.json"), created);
        JsonNode status = null;
        for (int i = 0; i < 120; i++) {
            status = JSON.readTree(request("GET", proxy + "/sapi/document/" + uuid, token, null, null));
            if (!status.path("processedOn").isNull() && !status.path("processedOn").isMissingNode()) break;
            Thread.sleep(1000);
        }
        Files.writeString(evidence.resolve("proxy-status.json"), JSON.writeValueAsString(status));
        require(status != null && !status.path("processedOn").isNull(), "Proxy document must complete");
        require(status.path("processedStatus").isNull(), "Proxy document failed: " + status.path("processedStatus"));
        String details = request("GET", proxy + "/sapi/document/" + uuid + "/details", token, null, null);
        Files.writeString(evidence.resolve("proxy-details.json"), details);
        JsonNode delivery = JSON.readTree(details);
        require("PHOSS".equals(delivery.path("accessPoint").asText()), "Must use PHOSS, not Proxy LOOPBACK");
        String instanceId = delivery.path("accessPointId").asText();
        String apToken = Files.readString(Path.of("/run/secrets/phoss-token")).strip();
        var apRequest = HttpRequest.newBuilder(URI.create("http://ap:8080/api/outbound/status/" + encode(instanceId)))
                .header("X-Token", apToken).GET().build();
        var apResult = HTTP.send(apRequest, HttpResponse.BodyHandlers.ofString());
        require(apResult.statusCode() == 200, "AP status must be available");
        Files.writeString(evidence.resolve("ap-status.json"), apResult.body());
        JsonNode apStatus = JSON.readTree(apResult.body());
        require("sent".equals(apStatus.path("status").asText()), "AP status must be sent");
        require(instanceId.equals(apStatus.path("sbdhInstanceID").asText()), "AP status identity must match");
        JsonNode report;
        try (Connection connection = DriverManager.getConnection("jdbc:postgresql://db:5432/proxy", "proxy", "local-phoss-test-only");
             PreparedStatement query = connection.prepareStatement("SELECT report FROM proxy.phoss_submission WHERE document_id = ?")) {
            query.setObject(1, UUID.fromString(uuid));
            try (ResultSet rows = query.executeQuery()) { require(rows.next(), "Durable submission must exist"); report = JSON.readTree(rows.getString(1)); }
        }
        Files.writeString(evidence.resolve("submission-report.json"), JSON.writeValueAsString(report));
        String messageId = report.path("as4MessageId").asText();
        String signal = report.path("as4ReceivedSignalMsg").asText();
        Files.writeString(evidence.resolve("receipt.xml"), signal);
        Document receipt = parse(signal);
        require(receipt.getElementsByTagNameNS(EB, "Receipt").getLength() == 1, "AS4 Receipt must exist");
        require(messageId.equals(receipt.getElementsByTagNameNS(EB, "RefToMessageId").item(0).getTextContent()), "Receipt must refer to sent AS4 message");
        JsonNode documents = JSON.readTree(request("GET", "http://testbackend:8888/api/documents", null, null, null));
        Files.writeString(evidence.resolve("backend-documents.json"), JSON.writeValueAsString(documents));
        JsonNode list = documents.isArray() ? documents : documents.path("documents");
        String matched = null;
        for (JsonNode candidate : list) {
            String content = request("GET", "http://testbackend:8888/api/documents/" + candidate.path("id").asText() + "/content", null, null, null);
            Document forwarded = parse(content);
            Node id = forwarded.getElementsByTagNameNS(SBD, "InstanceIdentifier").item(0);
            if (id != null && instanceId.equals(id.getTextContent())) {
                require(canonical(invoice.getDocumentElement()).equals(canonical(forwarded.getElementsByTagNameNS(INVOICE, "Invoice").item(0))), "Forwarded UBL tree must match submitted invoice");
                matched = candidate.path("id").asText();
                Files.writeString(evidence.resolve("forwarded-sbd.xml"), content);
                break;
            }
        }
        require(matched != null, "Matching forwarded SBD must exist in backend");
        require(!details.contains("as4ReceivedSignalMsg") && !details.contains(apToken), "Details must not expose raw reports or AP credentials");
        String result = "PASS: authenticated Proxy -> PHOSS -> real AS4 Receipt -> matching backend UBL\n"
                + "sender=" + sender + " receiver=" + receiver + "\nproxy=" + uuid + "\nsbdh=" + instanceId
                + "\nas4=" + messageId + "\nbackend=" + matched + "\n";
        Files.writeString(evidence.resolve("result.txt"), result);
        System.out.println(result);
    }

    static String request(String method, String url, String token, String contentType, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(3));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (contentType != null) builder.header("Content-Type", contentType);
        var response = HTTP.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        require(response.statusCode() / 100 == 2, method + " " + URI.create(url).getPath() + " failed HTTP " + response.statusCode() + ": " + response.body());
        return response.body();
    }

    static void setEndpoint(Element endpoint, String participant) {
        String[] parts = participant.split(":", 2); endpoint.setAttribute("schemeID", parts[0]); endpoint.setTextContent(parts[1]);
    }
    static String encode(String text) { return URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20"); }
    static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    static Element child(Element parent, String ns, String name) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) if (n instanceof Element e && ns.equals(e.getNamespaceURI()) && name.equals(e.getLocalName())) return e;
        throw new IllegalArgumentException("Missing " + name);
    }
    static Document parse(String xml) throws Exception {
        var f = DocumentBuilderFactory.newInstance(); f.setNamespaceAware(true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return f.newDocumentBuilder().parse(new org.xml.sax.InputSource(new StringReader(xml)));
    }
    static String xml(Document d) throws Exception {
        StringWriter out = new StringWriter(); TransformerFactory.newInstance().newTransformer().transform(new DOMSource(d), new StreamResult(out)); return out.toString();
    }
    static String canonical(Node n) {
        if (n instanceof Element e) {
            StringBuilder b = new StringBuilder("{").append(e.getNamespaceURI()).append('}').append(e.getLocalName());
            List<String> attributes = new ArrayList<>();
            for (int i = 0; i < e.getAttributes().getLength(); i++) {
                Node a = e.getAttributes().item(i);
                if (!XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(a.getNamespaceURI())) attributes.add("{" + a.getNamespaceURI() + "}" + a.getLocalName() + "=" + a.getNodeValue());
            }
            Collections.sort(attributes); b.append(attributes).append('[');
            for (Node c = e.getFirstChild(); c != null; c = c.getNextSibling()) b.append(canonical(c));
            return b.append(']').toString();
        }
        return (n.getNodeType() == Node.TEXT_NODE || n.getNodeType() == Node.CDATA_SECTION_NODE) && !n.getNodeValue().isBlank() ? "text(" + n.getNodeValue() + ")" : "";
    }
}
