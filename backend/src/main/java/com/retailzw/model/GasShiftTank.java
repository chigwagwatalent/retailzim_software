package com.retailzw.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "gas_shift_tanks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GasShiftTank {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @Column(name = "branch_id", nullable = false)
    private Long branchId;
    @Column(name = "gas_shift_id", nullable = false)
    private Long gasShiftId;
    @Column(name = "tank_id", nullable = false)
    private Long tankId;
    @Column(name = "starting_gross_kg", nullable = false, precision = 12, scale = 3)
    private BigDecimal startingGrossKg;
    @Column(name = "starting_net_kg", nullable = false, precision = 12, scale = 3)
    private BigDecimal startingNetKg;
    @Column(name = "expected_closing_net_kg", nullable = false, precision = 12, scale = 3)
    private BigDecimal expectedClosingNetKg;
    @Column(name = "closing_gross_kg", precision = 12, scale = 3)
    private BigDecimal closingGrossKg;
    @Column(name = "closing_net_kg", precision = 12, scale = 3)
    private BigDecimal closingNetKg;
    @Column(name = "variance_kg", precision = 12, scale = 3)
    private BigDecimal varianceKg;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.IN_USE;
    @Column(name = "selected_at", nullable = false)
    private LocalDateTime selectedAt;
    @Column(name = "closed_at")
    private LocalDateTime closedAt;
    @Version
    @Column(nullable = false)
    private long version;

    @PrePersist
    void onCreate() {
        if (selectedAt == null) selectedAt = LocalDateTime.now();
    }

    public enum Status {
        IN_USE, CLOSED
    }
}
