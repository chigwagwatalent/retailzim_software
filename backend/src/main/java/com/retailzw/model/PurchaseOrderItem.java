package com.retailzw.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "purchase_order_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", insertable = false, updatable = false)
    private Product product;

    @Column(nullable = false, precision = 15, scale = 4)
    private BigDecimal quantity;

    @Column(name = "quantity_received", precision = 15, scale = 4)
    @Builder.Default
    private BigDecimal quantityReceived = BigDecimal.ZERO;

    @Column(name = "unit_cost_usd", precision = 15, scale = 4)
    private BigDecimal unitCostUsd;

    @Column(name = "unit_cost_zwg", precision = 15, scale = 4)
    private BigDecimal unitCostZwg;

    @Column(name = "tax_rate", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal taxRate = BigDecimal.ZERO;

    @Column(name = "line_total_usd", precision = 15, scale = 2)
    private BigDecimal lineTotalUsd;

    @Column(name = "line_total_zwg", precision = 15, scale = 2)
    private BigDecimal lineTotalZwg;

    @Column(length = 255)
    private String notes;
}

