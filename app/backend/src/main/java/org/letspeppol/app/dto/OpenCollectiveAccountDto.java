package org.letspeppol.app.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OpenCollectiveAccountDto {
    private String name;
    private String slug;
    private Transactions transactions;
    private AccountStats stats;

    @Data
    public static class Transactions {
        private Integer totalCount;
        private List<Transaction> nodes;
    }

    @Data
    public static class Transaction {
        private String type;
        private FromAccount fromAccount;
        private Amount amount;
        private String createdAt;
    }

    @Data
    public static class FromAccount {
        private String name;
        private String slug;
    }

    @Data
    public static class Amount {
        private BigDecimal value;
        private String currency;
    }

    @Data
    public static class AccountStats {
        private Amount balance;
        private Amount totalAmountSpent;
        private Amount totalAmountReceived;
        private Integer contributionsCount;
        private Integer contributorsCount;
    }
}


