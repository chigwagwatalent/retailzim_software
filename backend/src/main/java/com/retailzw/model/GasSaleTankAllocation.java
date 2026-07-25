package com.retailzw.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "gas_sale_tank_allocations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GasSaleTankAllocation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;
    @Column(name = "branch_id", nullable = false)
    private Long branchId;
    @Column(name = "gas_sale_id", nullable = false)
    private Long gasSaleId;
    @Column(name = "gas_shift_id", nullable = false)
    private Long gasShiftId;
    @Column(name = "gas_shift_tank_id")
    private Long gasShiftTankId;
    @Column(name = "tank_id", nullable = false)
    private Long tankId;
    @Column(name = "quantity_kg", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantityKg;
    @Column(name = "stock_before_kg", nullable = false, precision = 12, scale = 3)
    private BigDecimal stockBeforeKg;
    @Column(name = "stock_after_kg", nullable = false, precision = 12, scale = 3)
    private BigDecimal stockAfterKg;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
