package com.retailzw.model;

import com.retailzw.enums.GasTankStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "gas_tanks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GasTank {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "product_name", nullable = false, length = 120)
    private String productName;

    @Column(name = "tare_weight_kg", nullable = false, precision = 12, scale = 3)
    @Builder.Default
    private BigDecimal tareWeightKg = BigDecimal.ZERO;

    @Column(name = "capacity_kg", precision = 12, scale = 3)
    private BigDecimal capacityKg;

    @Column(name = "full_gross_weight_kg", precision = 12, scale = 3)
    private BigDecimal fullGrossWeightKg;

    @Column(name = "current_kg", nullable = false, precision = 12, scale = 3)
    @Builder.Default
    private BigDecimal currentKg = BigDecimal.ZERO;

    @Column(name = "reorder_level_kg", precision = 12, scale = 3)
    @Builder.Default
    private BigDecimal reorderLevelKg = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private GasTankStatus status = GasTankStatus.ACTIVE;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @Transient
    public BigDecimal getCurrentGrossWeightKg() {
        return (tareWeightKg == null ? BigDecimal.ZERO : tareWeightKg)
                .add(currentKg == null ? BigDecimal.ZERO : currentKg);
    }
}
