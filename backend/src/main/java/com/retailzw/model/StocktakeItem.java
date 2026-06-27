package com.retailzw.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "stocktake_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StocktakeItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private StocktakeSession stocktakeSession;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "system_quantity", precision = 15, scale = 4)
    private BigDecimal systemQuantity;

    @Column(name = "counted_quantity", precision = 15, scale = 4)
    private BigDecimal countedQuantity;

    @Column(name = "variance", precision = 15, scale = 4, insertable = false, updatable = false)
    private BigDecimal variance;

    @Column(name = "variance_value_usd", precision = 15, scale = 2)
    private BigDecimal varianceValueUsd;

    @Column(name = "variance_value_zwg", precision = 15, scale = 2)
    private BigDecimal varianceValueZwg;

    @Column(name = "is_counted")
    @Builder.Default
    private Boolean isCounted = false;

    @Column(name = "counted_by")
    private Long countedBy;

    @Column(name = "counted_at")
    private LocalDateTime countedAt;

    @Column(length = 255)
    private String notes;
}

