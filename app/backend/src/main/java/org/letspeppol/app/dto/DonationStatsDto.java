package org.letspeppol.app.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Builder
@Data
public class DonationStatsDto {
    private Long totalContributions;
    private Long totalProcessed;
    private Long processedToday;
    private Long maxProcessedLastWeek;
    private Long invoicesRemaining;
    private List<OpenCollectiveAccountDto.Transaction> transactions;
}
