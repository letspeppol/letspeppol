package org.letspeppol.proxy.service;

import io.micrometer.core.instrument.Counter;
import lombok.RequiredArgsConstructor;
import org.letspeppol.proxy.dto.UblDocumentDto;
import org.letspeppol.proxy.exception.BadRequestException;
import org.letspeppol.proxy.exception.DuplicateRequestException;
import org.letspeppol.proxy.exception.NotFoundException;
import org.letspeppol.proxy.mapper.UblDocumentMapper;
import org.letspeppol.proxy.model.AccessPoint;
import org.letspeppol.proxy.model.DocumentDirection;
import org.letspeppol.proxy.model.UblDocument;
import org.letspeppol.proxy.repository.UblDocumentRepository;
import org.letspeppol.proxy.util.HashUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
@Transactional
@Service
public class UblDocumentSenderService {

    private final UblDocumentRepository ublDocumentRepository;
    private final BackupService backupService;
    private final Counter documentRescheduleCounter;

    public UblDocumentDto createToSend(UblDocumentDto ublDocumentDto, boolean noArchive) {
        String hash = HashUtil.sha256(ublDocumentDto.ubl()); //TODO : should we use HMAC ?
        UUID uuid = ublDocumentDto.id() == null ? UUID.randomUUID() : ublDocumentDto.id();
        if (ublDocumentRepository.findById(uuid).isPresent()) {
            throw new DuplicateRequestException("UblDocument " + uuid + " is already created, please use the update call");
        }
        if (!ublDocumentRepository.findAllByHash(hash).isEmpty()) { //TODO : should we add timeframe based on ublDocumentDto.createdOn() ?
            throw new DuplicateRequestException("UblDocument seems to already send with hash " + hash + " content might be not unique");
        }
        UblDocument ublDocument = new UblDocument( //TODO : do we set default values here ?
                uuid, //App can generate the uuid, because they might have used this for drafts
                DocumentDirection.OUTGOING, //user can not overwrite this value : ublDocumentDto.direction(),
                ublDocumentDto.ownerPeppolId(),
                ublDocumentDto.partnerPeppolId(),
                Instant.now(), //setting manual because we need this value to return and @Transactional will postpone it and the return dto has null
                calculateSchedule(ublDocumentDto),
                null,
                null,
                ublDocumentDto.ubl(),
                hash,
                noArchive?-1:0,
                null,
                null,
                null
        );
        ublDocument = ublDocumentRepository.save(ublDocument); //This is needed as it is a new
        backupService.backupFile(ublDocument);
        return UblDocumentMapper.toDto(ublDocument);
    }

    public UblDocumentDto update(UUID id, UblDocumentDto ublDocumentDto, boolean noArchive) {
        String hash = HashUtil.sha256(ublDocumentDto.ubl());
        UblDocument ublDocument = ublDocumentRepository.findById(id).orElseThrow(() -> new NotFoundException("UblDocument " + id + " does not exist"));
        ublDocument.setOwnerPeppolId(ublDocumentDto.ownerPeppolId());
        ublDocument.setPartnerPeppolId(ublDocumentDto.partnerPeppolId());
        ublDocument.setScheduledOn(calculateSchedule(ublDocumentDto));
        ublDocument.setUbl(ublDocumentDto.ubl());
        ublDocument.setHash(hash);
        if (noArchive) {
            ublDocument.setDownloadCount(-1);
        } else if (ublDocument.getDownloadCount() < 0) {
            ublDocument.setDownloadCount(0);
        }
        // ublDocument = ublDocumentRepository.save(ublDocument); //This can be remove due to @Transactional
        backupService.backupFile(ublDocument);
        return UblDocumentMapper.toDto(ublDocument);
    }

    public UblDocumentDto reschedule(UUID id, UblDocumentDto ublDocumentDto) {
        UblDocument ublDocument = ublDocumentRepository.findByIdAndOwnerPeppolId(id, ublDocumentDto.ownerPeppolId()).orElseThrow(() -> new NotFoundException("UblDocument "+id+" does not exist"));
        if (ublDocument.getAccessPoint() != null) {
            throw new BadRequestException("UblDocument "+id+" is already picked up by AP");
        }
        Instant calculatedSchedule = calculateSchedule(ublDocumentDto);
        if (!calculatedSchedule.equals(ublDocument.getScheduledOn())) {
            ublDocument.setScheduledOn(calculatedSchedule);
        }
        documentRescheduleCounter.increment();
        // ublDocument = ublDocumentRepository.save(ublDocument); //This can be remove due to @Transactional
        return UblDocumentMapper.toDto(ublDocument);
    }

    public void cancel(UUID id, String ownerPeppolId, boolean noArchive) {
        UblDocument ublDocument = ublDocumentRepository.findByIdAndOwnerPeppolId(id, ownerPeppolId).orElseThrow(() -> new NotFoundException("UblDocument "+id+" does not exist"));
        if (ublDocument.getAccessPoint() != null) {
            throw new BadRequestException("UblDocument "+id+" is already picked up by AP");
        }
        ublDocument.setAccessPoint(AccessPoint.NONE);
        if (noArchive || ublDocument.getDownloadCount() < 0) { //Set to No-Archive
            ublDocument.setUbl(null);
            ublDocument.setDownloadCount(0);
            backupService.clearBackupFile(ublDocument);
        }
        // ublDocument = ublDocumentRepository.save(ublDocument); //This can be remove due to @Transactional
    }

    private Instant calculateSchedule(UblDocumentDto ublDocumentDto) {
        //TODO : find number that are scheduled on that moment
        return ublDocumentDto.scheduledOn() == null ? Instant.now() : ublDocumentDto.scheduledOn();
    }

}
