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
}

