package com.retailzw.model;

import com.retailzw.enums.CurrencyCode;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "borrower_transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BorrowerTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @Column(name = "branch_id")
    private Long branchId;
    @Column(name = "borrower_id", nullable = false)
    private Long borrowerId;
    @Column(name = "sale_id")
    private Long saleId;
    @Column(name = "cash_session_id")
    private Long cashSessionId;
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private TransactionType transactionType;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private CurrencyCode currency;
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;
    @Column(name = "balance_before", nullable = false, precision = 15, scale = 2)
    private BigDecimal balanceBefore;
    @Column(name = "balance_after", nullable = false, precision = 15, scale = 2)
    private BigDecimal balanceAfter;
    @Column(name = "offline_reference", length = 100)
    private String offlineReference;
    @Column(columnDefinition = "TEXT")
    private String notes;
    @Column(name = "created_by")
    private Long createdBy;
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum TransactionType {
        BORROW, REPAYMENT, ADJUSTMENT
    }
}
