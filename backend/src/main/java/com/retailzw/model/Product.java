package com.retailzw.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 100)
    private String sku;

    @Column(length = 100)
    private String barcode;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private ProductCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uom_id")
    private UnitOfMeasure unitOfMeasure;

    @Column(name = "cost_price_usd", precision = 15, scale = 4)
    @Builder.Default
    private BigDecimal costPriceUsd = BigDecimal.ZERO;

    @Column(name = "selling_price_usd", precision = 15, scale = 4)
    @Builder.Default
    private BigDecimal sellingPriceUsd = BigDecimal.ZERO;

    @Column(name = "cost_price_zwg", precision = 15, scale = 4)
    @Builder.Default
    private BigDecimal costPriceZwg = BigDecimal.ZERO;

    @Column(name = "selling_price_zwg", precision = 15, scale = 4)
    @Builder.Default
    private BigDecimal sellingPriceZwg = BigDecimal.ZERO;

    @Column(name = "tax_rate", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal taxRate = BigDecimal.ZERO;

    @Column(name = "is_taxable")
    @Builder.Default
    private Boolean isTaxable = true;

    @Column(name = "reorder_level", precision = 15, scale = 4)
    @Builder.Default
    private BigDecimal reorderLevel = BigDecimal.ZERO;

    @Column(name = "max_stock_level", precision = 15, scale = 4)
    private BigDecimal maxStockLevel;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "has_variants")
    @Builder.Default
    private Boolean hasVariants = false;

    @Column(name = "is_service")
    @Builder.Default
    private Boolean isService = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "tracking_mode", nullable = false, length = 20)
    @Builder.Default
    private TrackingMode trackingMode = TrackingMode.NONE;

    @Column(name = "expiry_tracking", nullable = false)
    @Builder.Default
    private Boolean expiryTracking = false;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum TrackingMode {
        NONE, BATCH, SERIAL
    }
}

