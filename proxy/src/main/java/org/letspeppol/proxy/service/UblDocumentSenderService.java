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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Transactional
@Service
public class UblDocumentSenderService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Brussels");
    private static final int MAXIMUM_RATE_PER_DAY = 4;
    private static final int MAXIMUM_RATE_BETWEEN_PARTIES = 2;

    private final UblDocumentRepository ublDocumentRepository;
    private final BackupService backupService;
    private final BalanceService balanceService;
    private final Counter documentRescheduleCounter;

    public UblDocumentDto createToSend(UblDocumentDto ublDocumentDto, boolean noArchive) {
        String hash = HashUtil.sha256(ublDocumentDto.ubl()); //TODO : should we use HMAC ?
        UUID uuid = ublDocumentDto.id() == null ? UUID.randomUUID() : ublDocumentDto.id();
        if (ublDocumentRepository.findById(uuid).isPresent()) {
            throw new DuplicateRequestException("UblDocument " + uuid + " is already created, please use the update call");
        }
        if (!ublDocumentRepository.findAllByHash(hash).isEmpty()) {
            throw new DuplicateRequestException("UblDocument seems to already send with hash " + hash + " content might be not unique");
        }
        UblDocument ublDocument = new UblDocument(
                uuid, //App can generate the uuid, because they might have used this for drafts
                DocumentDirection.OUTGOING, //user can not overwrite this value : ublDocumentDto.direction(),
                ublDocumentDto.type(),
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
        if (balanceService.isPositive() && (ublDocumentDto.scheduledOn() == null || ublDocumentDto.scheduledOn().isBefore(Instant.now().plus(1, ChronoUnit.HOURS)))) {
            return Instant.now();
        }
        LocalDate day = Optional.ofNullable(ublDocumentDto.scheduledOn())
                .orElseGet(Instant::now)
                .atZone(ZONE)
                .toLocalDate();
        if (day.isBefore(LocalDate.now(ZONE).plusDays(1))) {
            day = day.plusDays(1);
        }
        while (overMaximumRatePerDay(ublDocumentDto.ownerPeppolId(), day) ||
                overMaximumRateBetweenParties(ublDocumentDto.ownerPeppolId(), ublDocumentDto.partnerPeppolId(), day)) {
            day = day.plusDays(1);
        }
        return day.atStartOfDay(ZONE).toInstant();
    }

    private boolean overMaximumRatePerDay(String ownerPeppolId, LocalDate day) {
        return ublDocumentRepository.countByOwnerPeppolIdAndDirectionAndProcessedOnIsNullAndAccessPointIsNullAndScheduledOnBetween(
                ownerPeppolId,
                DocumentDirection.OUTGOING,
                day.atStartOfDay(ZONE).toInstant(),
                day.plusDays(1).atStartOfDay(ZONE).toInstant()
        ) >= MAXIMUM_RATE_PER_DAY;
    }

    private boolean overMaximumRateBetweenParties(String ownerPeppolId, String partnerPeppolId, LocalDate day) {
        return ublDocumentRepository.countByOwnerPeppolIdAndPartnerPeppolIdAndDirectionAndProcessedOnIsNullAndAccessPointIsNullAndScheduledOnBetween(
                ownerPeppolId,
                partnerPeppolId,
                DocumentDirection.OUTGOING,
                day.atStartOfDay(ZONE).toInstant(),
                day.plusDays(1).atStartOfDay(ZONE).toInstant()
        ) >= MAXIMUM_RATE_BETWEEN_PARTIES;
    }

}
