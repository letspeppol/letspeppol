package org.letspeppol.proxy.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.proxy.dto.PeppolParties;
import org.letspeppol.proxy.dto.UblDocumentDto;
import org.letspeppol.proxy.exception.SecurityException;
import org.letspeppol.proxy.model.AccessPoint;
import org.letspeppol.proxy.model.AccountType;
import org.letspeppol.proxy.service.*;
import org.letspeppol.proxy.util.JwtUtil;
import org.letspeppol.proxy.util.UblParser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
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
    public static final String ACTING_USER_AUTHORIZATION_HEADER = "X-Acting-User-Authorization";

    private final UblDocumentService ublDocumentService;
    private final UblDocumentSenderService ublDocumentSenderService;
    private final UblDocumentReceiverService ublDocumentReceiverService;
    private final RegistryService registryService;
    private final ValidationService validationService;
    private final JwtDecoder jwtDecoder;

    @GetMapping()
    public List<UblDocumentDto> getAllNew(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = DEFAULT_SIZE) int size) {
        AccountType accountType = JwtUtil.getAccountType(jwt);
        if (accountType.isUser()) {
            return ublDocumentReceiverService.findAllNew(JwtUtil.getUserPeppolId(jwt), size);
        } else if (accountType.isApp()) {
            return ublDocumentReceiverService.findAllNewByAppLink(JwtUtil.getAppUid(jwt), size);
        } else {
            throw new SecurityException("Not correct account type");
        }
    }

    @PostMapping("status")
    public List<UblDocumentDto> getStatusUpdates(@AuthenticationPrincipal Jwt jwt, @RequestBody List<UUID> ids) {
        String peppolId = JwtUtil.getUserPeppolId(jwt);
        return ublDocumentService.findByIds(ids, peppolId); //TODO : map to statusDto !
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
    public ResponseEntity<UblDocumentDto> createToSend(@AuthenticationPrincipal Jwt jwt,
                                                       @RequestBody UblDocumentDto ublDocumentDto,
                                                       @RequestHeader(name = ACTING_USER_AUTHORIZATION_HEADER, required = false) String actingUserAuthorization,
                                                       @RequestParam(defaultValue = "false") boolean noArchive) {
        validateSender(jwt, ublDocumentDto, actingUserAuthorization);
        return ResponseEntity.status(HttpStatus.CREATED).body(ublDocumentSenderService.createToSend(ublDocumentDto, noArchive));
    }

    @PutMapping("{id}")
    public ResponseEntity<UblDocumentDto> update(@AuthenticationPrincipal Jwt jwt,
                                                 @PathVariable UUID id,
                                                 @RequestBody UblDocumentDto ublDocumentDto,
                                                 @RequestHeader(name = ACTING_USER_AUTHORIZATION_HEADER, required = false) String actingUserAuthorization,
                                                 @RequestParam(defaultValue = "false") boolean noArchive) {
        validateSender(jwt, ublDocumentDto, actingUserAuthorization);
        return ResponseEntity.status(HttpStatus.OK).body(ublDocumentSenderService.update(id, ublDocumentDto, noArchive));
    }

    @PutMapping("{id}/reschedule")
    public ResponseEntity<UblDocumentDto> reschedule(@AuthenticationPrincipal Jwt jwt,
                                                     @PathVariable UUID id,
                                                     @RequestBody UblDocumentDto ublDocumentDto,
                                                     @RequestHeader(name = ACTING_USER_AUTHORIZATION_HEADER, required = false) String actingUserAuthorization) {
        validateSender(jwt, ublDocumentDto, actingUserAuthorization);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ublDocumentSenderService.reschedule(id, ublDocumentDto));
    }

    @PutMapping("{id}/downloaded")
    public ResponseEntity<Object> downloaded(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestParam(defaultValue = "false") boolean noArchive) {
        AccountType accountType = JwtUtil.getAccountType(jwt);
        if (accountType.isUser()) {
            ublDocumentReceiverService.downloaded(List.of(id), JwtUtil.getUserPeppolId(jwt), noArchive);
        } else if (accountType.isApp()) {
            ublDocumentReceiverService.downloaded(List.of(id), JwtUtil.getAppUid(jwt), noArchive);
        } else {
            throw new SecurityException("Not correct account type");
        }
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @PutMapping("downloaded")
    public ResponseEntity<Object> downloadedBatch(@AuthenticationPrincipal Jwt jwt, @RequestBody List<UUID> ids, @RequestParam(defaultValue = "false") boolean noArchive) {
        AccountType accountType = JwtUtil.getAccountType(jwt);
        if (accountType.isUser()) {
            ublDocumentReceiverService.downloaded(ids, JwtUtil.getUserPeppolId(jwt), noArchive);
        } else if (accountType.isApp()) {
            ublDocumentReceiverService.downloaded(ids, JwtUtil.getAppUid(jwt), noArchive);
        } else {
            throw new SecurityException("Not correct account type");
        }
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @DeleteMapping("{id}")
    public ResponseEntity<Object> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestParam(defaultValue = "false") boolean noArchive) {
        String peppolId = JwtUtil.getUserPeppolId(jwt);
        ublDocumentSenderService.cancel(id, peppolId, noArchive);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private void validateSender(Jwt jwt, UblDocumentDto ublDocumentDto, String actingUserAuthorization) throws SecurityException {
        SenderValidation senderValidation = validateSenderAccount(jwt, ublDocumentDto, actingUserAuthorization);
        if (!ublDocumentDto.ownerPeppolId().equals(senderValidation.senderPeppolId())) {
            log.error("Peppol ID {} not the owner {} of document {}", senderValidation.senderPeppolId(), ublDocumentDto.ownerPeppolId(), ublDocumentDto.id());
            throw new SecurityException("Peppol ID not the owner");
        }
        if (ublDocumentDto.ubl() == null || ublDocumentDto.ubl().isBlank()) {
            log.error("Peppol ID {} sending empty UBL of document {}", senderValidation.senderPeppolId(), ublDocumentDto.id());
            throw new RuntimeException("Missing UBL content");
        }
        if (!validationService.validateUblXml(ublDocumentDto.ubl()).isValid()) {
            log.error("Peppol ID {} sending invalid UBL of document {}", senderValidation.senderPeppolId(), ublDocumentDto.id());
            throw new RuntimeException("Invalid UBL content");
        }
        try {
            PeppolParties peppolParties = UblParser.parsePeppolParties(ublDocumentDto.ubl());
            if (!peppolParties.sender().equals(senderValidation.senderPeppolId())) {
                log.error("Peppol ID {} not the sender {} of document {}", senderValidation.senderPeppolId(), peppolParties.sender(), ublDocumentDto.id());
                throw new SecurityException("Peppol ID not the owner");
            }
            if (senderValidation.actingUserPeppolId() != null) {
                if (!senderValidation.actingUserPeppolId().equals(ublDocumentDto.partnerPeppolId())) {
                    log.error("Acting user {} not the partner {} of document {}", senderValidation.actingUserPeppolId(), ublDocumentDto.partnerPeppolId(), ublDocumentDto.id());
                    throw new SecurityException("Acting user not the receiver");
                }
                if (!senderValidation.actingUserPeppolId().equals(peppolParties.receiver())) {
                    log.error("Acting user {} not the receiver {} of document {}", senderValidation.actingUserPeppolId(), peppolParties.receiver(), ublDocumentDto.id());
                    throw new SecurityException("Acting user not the receiver");
                }
            }
        } catch (ParserConfigurationException | IOException | SAXException | XPathExpressionException e) {
            log.error("Peppol ID {} send bad data of document {}", senderValidation.senderPeppolId(), ublDocumentDto.id(), e);
            throw new RuntimeException(e);
        }
        if (registryService.getAccessPoint(senderValidation.senderPeppolId()) == AccessPoint.NONE) {
            log.error("Peppol ID {} not activated during send of document {}", senderValidation.senderPeppolId(), ublDocumentDto.id());
            throw new SecurityException("Peppol ID not activated to send");
        }
    }

    private SenderValidation validateSenderAccount(Jwt jwt, UblDocumentDto ublDocumentDto, String actingUserAuthorization) {
        AccountType accountType = JwtUtil.getAccountType(jwt);
        if (accountType.isUser()) {
            return new SenderValidation(JwtUtil.getUserPeppolId(jwt), null);
        }
        if (accountType.isApp()) {
            Jwt actingUserJwt = decodeActingUserJwt(actingUserAuthorization);
            return new SenderValidation(JwtUtil.getPeppolId(jwt), JwtUtil.getUserPeppolId(actingUserJwt));
        }
        throw new SecurityException("Not correct account type");
    }

    private Jwt decodeActingUserJwt(String actingUserAuthorization) {
        if (actingUserAuthorization == null || actingUserAuthorization.isBlank()) {
            throw new SecurityException("Missing acting user token");
        }
        String token = actingUserAuthorization.trim();
        if (token.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
            token = token.substring("Bearer ".length()).trim();
        }
        try {
            return jwtDecoder.decode(token);
        } catch (JwtException e) {
            log.error("Invalid acting user token", e);
            throw new SecurityException("Invalid acting user token");
        }
    }

    private record SenderValidation(String senderPeppolId, String actingUserPeppolId) {
    }

}
