package org.letspeppol.app.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.app.dto.MaxProcessedDto;
import org.letspeppol.app.dto.TotalProcessedDto;
import org.letspeppol.app.dto.TotalsDto;
import org.letspeppol.app.repository.DocumentRepository;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@RequiredArgsConstructor
@Service
public class StatisticsService {

    static final ZoneId ZONE = ZoneId.of("Europe/Brussels");

    private final DocumentRepository documentRepository;

    public TotalsDto getTotals(String peppolId) {
        return documentRepository.totalsByOwner(peppolId);
    }

    public TotalProcessedDto totalsProcessed() {
        Instant start = LocalDate.now(ZONE).atStartOfDay(ZONE).toInstant();
        Instant endExclusive = LocalDate.now(ZONE).plusDays(1).atStartOfDay(ZONE).toInstant();

        return new TotalProcessedDto(
                documentRepository.countByProcessedOnIsNotNull(),
                documentRepository.countByProcessedOnIsNotNullAndIssueDateGreaterThanEqualAndIssueDateLessThan(start, endExclusive)
        );
    }

    public MaxProcessedDto maxProcessed() {
        Instant startInclusive = LocalDate.now(ZONE).minusDays(7).atStartOfDay(ZONE).toInstant();
        Instant endExclusive = LocalDate.now(ZONE).atStartOfDay(ZONE).toInstant();
        return new MaxProcessedDto(
                documentRepository.maxDailyTotal(startInclusive, endExclusive)
        );
    }
}
