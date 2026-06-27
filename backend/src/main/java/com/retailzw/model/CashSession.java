package com.retailzw.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cash_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "drawer_id", nullable = false)
    private Long drawerId;

    @Column(name = "cashier_id", nullable = false)
    private Long cashierId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private SessionStatus status = SessionStatus.OPEN;

    @Column(name = "opening_float_usd", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal openingFloatUsd = BigDecimal.ZERO;

    @Column(name = "opening_float_zwg", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal openingFloatZwg = BigDecimal.ZERO;

    @Column(name = "closing_float_usd", precision = 15, scale = 2)
    private BigDecimal closingFloatUsd;

    @Column(name = "closing_float_zwg", precision = 15, scale = 2)
    private BigDecimal closingFloatZwg;

    @Column(name = "actual_cash_usd", precision = 15, scale = 2)
    private BigDecimal actualCashUsd;

    @Column(name = "actual_cash_zwg", precision = 15, scale = 2)
    private BigDecimal actualCashZwg;

    @Column(name = "expected_cash_usd", precision = 15, scale = 2)
    private BigDecimal expectedCashUsd;

    @Column(name = "expected_cash_zwg", precision = 15, scale = 2)
    private BigDecimal expectedCashZwg;

    @Column(name = "variance_usd", precision = 15, scale = 2)
    private BigDecimal varianceUsd;

    @Column(name = "variance_zwg", precision = 15, scale = 2)
    private BigDecimal varianceZwg;

    @Column(name = "total_sales_usd", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalSalesUsd = BigDecimal.ZERO;

    @Column(name = "total_sales_zwg", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalSalesZwg = BigDecimal.ZERO;

    @Column(name = "total_transactions")
    @Builder.Default
    private Integer totalTransactions = 0;

    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "closing_notes", columnDefinition = "TEXT")
    private String closingNotes;

    @PrePersist
    protected void onCreate() {
        if (openedAt == null) openedAt = LocalDateTime.now();
    }

    public enum SessionStatus {
        OPEN, CLOSED
    }
}

