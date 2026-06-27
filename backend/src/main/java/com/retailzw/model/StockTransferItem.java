package com.retailzw.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "stock_transfer_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockTransferItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfer_id", nullable = false)
    private StockTransfer stockTransfer;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "quantity_sent", nullable = false, precision = 15, scale = 4)
    private BigDecimal quantitySent;

    @Column(name = "quantity_received", precision = 15, scale = 4)
    @Builder.Default
    private BigDecimal quantityReceived = BigDecimal.ZERO;

    @Column(name = "unit_cost_usd", precision = 15, scale = 4)
    private BigDecimal unitCostUsd;

    @Column(name = "unit_cost_zwg", precision = 15, scale = 4)
    private BigDecimal unitCostZwg;

    @Column(length = 255)
    private String notes;
}

