package com.retailzw.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "sale_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", nullable = false)
    @JsonIgnore
    private Sale sale;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "product_sku", length = 100)
    private String productSku;

    @Column(name = "product_barcode", length = 100)
    private String productBarcode;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "cost_price", precision = 15, scale = 4)
    @Builder.Default
    private BigDecimal costPrice = BigDecimal.ZERO;

    @Column(name = "discount_amount", precision = 15, scale = 4)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "tax_rate", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal taxRate = BigDecimal.ZERO;

    @Column(name = "tax_amount", precision = 15, scale = 4)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "line_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal lineTotal;

    @Column(name = "promotion_id")
    private Long promotionId;

    @Column(name = "promotion_name", length = 200)
    private String promotionName;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_tier", nullable = false, length = 20)
    @Builder.Default
    private WholesalePricingTier pricingTier = WholesalePricingTier.RETAIL;

    @Column(name = "retail_unit_price", precision = 15, scale = 4)
    private BigDecimal retailUnitPrice;

    @Column(name = "wholesale_minimum_quantity", precision = 15, scale = 4)
    private BigDecimal wholesaleMinimumQuantity;

    @Column(name = "pricing_version")
    private Long pricingVersion;

    @Column(name = "pricing_source", nullable = false, length = 30)
    @Builder.Default
    private String pricingSource = "LEGACY_RETAIL";

    @Column(name = "pricing_exchange_rate_id")
    private Long pricingExchangeRateId;

    public enum WholesalePricingTier {
        RETAIL, WHOLESALE
    }
}

