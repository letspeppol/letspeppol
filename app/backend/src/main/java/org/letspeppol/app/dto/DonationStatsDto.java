package org.letspeppol.app.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Builder
@Data
public class DonationStatsDto {
    private Integer totalContributions;
    private Integer totalProcessed;
    private Integer processedToday;
    private Integer maxProcessedLastWeek;
    private Integer invoicesRemaining;
    private List<OpenCollectiveAccountDto.Transaction> transactions;
}
