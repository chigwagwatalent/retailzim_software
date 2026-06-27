package com.retailzw.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "gas_restocks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GasRestock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "tank_id", nullable = false)
    private Long tankId;

    @Column(name = "quantity_kg", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantityKg;

    @Column(name = "supplier_name", length = 120)
    private String supplierName;

    @Column(length = 500)
    private String notes;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
