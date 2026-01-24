package org.letspeppol.proxy.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.proxy.dto.PeppolParties;
import org.letspeppol.proxy.dto.UblDocumentDto;
import org.letspeppol.proxy.exception.SecurityException;
import org.letspeppol.proxy.model.AccessPoint;
import org.letspeppol.proxy.service.*;
import org.letspeppol.proxy.util.JwtUtil;
import org.letspeppol.proxy.util.UblParser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.xml.sax.SAXException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/sapi/document")
public class AppController {

    public static final String DEFAULT_SIZE = "100";

    private final UblDocumentService ublDocumentService;
    private final UblDocumentSenderService ublDocumentSenderService;
    private final UblDocumentReceiverService ublDocumentReceiverService;
    private final RegistryService registryService;
    private final ValidationService validationService;

    @GetMapping()
    public List<UblDocumentDto> getAllNew(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = DEFAULT_SIZE) int size) {
        String peppolId = JwtUtil.getUserPeppolId(jwt);
        return ublDocumentReceiverService.findAllNew(peppolId, size);
    }

    @PostMapping("status")
    public List<UblDocumentDto> getStatusUpdates(@AuthenticationPrincipal Jwt jwt, @RequestBody List<UUID> ids) {
        String peppolId = JwtUtil.getUserPeppolId(jwt);
        return ublDocumentService.findByIds(ids, peppolId);
    }

    /* *
    Need filtering and quering like
      userId: string;
      // paging:
      page: number;
      pageSize: number;
      // filters:
      counterPartyId?: string | undefined;
      counterPartyNameLike?: string | undefined;
      docType: 'invoice' | 'credit-note' | undefined;
      direction: 'incoming' | 'outgoing' | undefined;
      docId?: string | undefined;
      // sorting:
      sortBy?: 'amountAsc' | 'amountDesc' | 'createdAtAsc' | 'createdAtDesc';
    * */

    @GetMapping("{id}")
    public UblDocumentDto getById(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        String peppolId = JwtUtil.getUserPeppolId(jwt);
        return ublDocumentService.findById(id, peppolId);
    }

    @PostMapping()
    public ResponseEntity<UblDocumentDto> createToSend(@AuthenticationPrincipal Jwt jwt, @RequestBody UblDocumentDto ublDocumentDto, @RequestParam(defaultValue = "false") boolean noArchive) {
        validateSender(jwt, ublDocumentDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ublDocumentSenderService.createToSend(ublDocumentDto, noArchive));
    }

    @PutMapping("{id}")
    public ResponseEntity<UblDocumentDto> update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestBody UblDocumentDto ublDocumentDto, @RequestParam(defaultValue = "false") boolean noArchive) {
        validateSender(jwt, ublDocumentDto);
        return ResponseEntity.status(HttpStatus.OK).body(ublDocumentSenderService.update(id, ublDocumentDto, noArchive));
    }

    @PutMapping("{id}/send")
    public ResponseEntity<UblDocumentDto> reschedule(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestBody UblDocumentDto ublDocumentDto) {
        validateSender(jwt, ublDocumentDto);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ublDocumentSenderService.reschedule(id, ublDocumentDto));
    }

    @PutMapping("{id}/downloaded")
    public ResponseEntity<Object> downloaded(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestParam(defaultValue = "false") boolean noArchive) {
        String peppolId = JwtUtil.getUserPeppolId(jwt);
        ublDocumentReceiverService.downloaded(List.of(id), peppolId, noArchive);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @PutMapping("downloaded")
    public ResponseEntity<Object> downloadedBatch(@AuthenticationPrincipal Jwt jwt, @RequestBody List<UUID> ids, @RequestParam(defaultValue = "false") boolean noArchive) {
        String peppolId = JwtUtil.getUserPeppolId(jwt);
        ublDocumentReceiverService.downloaded(ids, peppolId, noArchive);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @DeleteMapping("{id}")
    public ResponseEntity<Object> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestParam(defaultValue = "false") boolean noArchive) {
        String peppolId = JwtUtil.getUserPeppolId(jwt);
        ublDocumentSenderService.cancel(id, peppolId, noArchive);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private void validateSender(Jwt jwt, UblDocumentDto ublDocumentDto) throws SecurityException {
        String peppolId = JwtUtil.getUserPeppolId(jwt);
        if (!ublDocumentDto.ownerPeppolId().equals(peppolId)) {
            log.error("Peppol ID {} not the owner {} of document {}", peppolId, ublDocumentDto.ownerPeppolId(), ublDocumentDto.id());
            throw new SecurityException("Peppol ID not the owner");
        }
        if (ublDocumentDto.ubl() == null || ublDocumentDto.ubl().isBlank()) {
            log.error("Peppol ID {} sending empty UBL of document {}", peppolId, ublDocumentDto.id());
            throw new RuntimeException("Missing UBL content");
        }
        if (!validationService.validateUblXml(ublDocumentDto.ubl()).isValid()) {
            log.error("Peppol ID {} sending invalid UBL of document {}", peppolId, ublDocumentDto.id());
            throw new RuntimeException("Invalid UBL content");
        }
        try {
            PeppolParties peppolParties = UblParser.parsePeppolParties(ublDocumentDto.ubl());
            if (!peppolParties.sender().equals(peppolId)) {
                log.error("Peppol ID {} not the sender {} of document {}", peppolId, peppolParties.sender(), ublDocumentDto.id());
                throw new SecurityException("Peppol ID not the owner");
            }
        } catch (ParserConfigurationException | IOException | SAXException | XPathExpressionException e) {
            log.error("Peppol ID {} send bad data of document {}", peppolId, ublDocumentDto.id(), e);
            throw new RuntimeException(e);
        }
        if (registryService.getAccessPoint(peppolId) == AccessPoint.NONE) {
            log.error("Peppol ID {} not activated during send of document {}", peppolId, ublDocumentDto.id());
            throw new SecurityException("Peppol ID not activated to send");
        }
    }

}
