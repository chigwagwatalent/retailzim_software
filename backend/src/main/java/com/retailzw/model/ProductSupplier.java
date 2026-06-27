package com.retailzw.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_suppliers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSupplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(name = "supplier_sku", length = 100)
    private String supplierSku;

    @Column(name = "supplier_product_name", length = 200)
    private String supplierProductName;

    @Column(name = "cost_price_usd", precision = 15, scale = 4)
    private BigDecimal costPriceUsd;

    @Column(name = "cost_price_zwg", precision = 15, scale = 4)
    private BigDecimal costPriceZwg;

    @Column(name = "minimum_order_qty", precision = 15, scale = 4)
    private BigDecimal minimumOrderQty;

    @Column(name = "lead_time_days")
    private Integer leadTimeDays;

    @Column(name = "is_preferred")
    @Builder.Default
    private Boolean isPreferred = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

