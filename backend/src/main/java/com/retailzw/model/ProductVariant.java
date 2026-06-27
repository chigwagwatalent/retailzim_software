package com.retailzw.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_variants")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", insertable = false, updatable = false)
    private Product product;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 100)
    private String sku;

    @Column(length = 100)
    private String barcode;

    @Column(name = "price_modifier_usd", precision = 15, scale = 4)
    @Builder.Default
    private BigDecimal priceModifierUsd = BigDecimal.ZERO;

    @Column(name = "price_modifier_zwg", precision = 15, scale = 4)
    @Builder.Default
    private BigDecimal priceModifierZwg = BigDecimal.ZERO;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

