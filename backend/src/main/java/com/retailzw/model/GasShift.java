package com.retailzw.model;

import com.retailzw.enums.GasShiftStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "gas_shifts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GasShift {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "cashier_id", nullable = false)
    private Long cashierId;

    @Column(name = "shift_number", nullable = false, length = 50)
    private String shiftNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private GasShiftStatus status = GasShiftStatus.OPEN;

    @Column(name = "opened_at", nullable = false)
    private LocalDateTime openedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "total_kg_sold", precision = 12, scale = 3)
    @Builder.Default
    private BigDecimal totalKgSold = BigDecimal.ZERO;

    @Column(name = "total_usd", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalUsd = BigDecimal.ZERO;

    @Column(name = "total_zwg", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalZwg = BigDecimal.ZERO;

    @Column(name = "total_transactions", nullable = false)
    @Builder.Default
    private Integer totalTransactions = 0;

    @Column(name = "closing_variance_kg", precision = 12, scale = 3)
    private BigDecimal closingVarianceKg;

    @Version
    @Column(nullable = false)
    private long version;
}
