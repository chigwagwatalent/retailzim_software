package com.retailzw.model;

import com.retailzw.enums.CurrencyCode;
import com.retailzw.enums.GasSaleStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "gas_sales")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GasSale {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "gas_shift_id", nullable = false)
    private Long gasShiftId;

    @Column(name = "tank_id", nullable = false)
    private Long tankId;

    @Column(name = "cashier_id", nullable = false)
    private Long cashierId;

    @Column(name = "receipt_number", nullable = false, length = 80)
    private String receiptNumber;

    @Column(name = "customer_name", length = 120)
    private String customerName;

    @Column(name = "customer_phone", length = 30)
    private String customerPhone;

    @Column(name = "quantity_kg", nullable = false, precision = 12, scale = 3)
    private BigDecimal quantityKg;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 4)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private CurrencyCode currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private GasSaleStatus status = GasSaleStatus.COMPLETED;

    @Column(name = "offline_receipt_number", length = 100)
    private String offlineReceiptNumber;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
