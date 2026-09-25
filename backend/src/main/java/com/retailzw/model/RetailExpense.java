package com.retailzw.model;

import com.retailzw.enums.CurrencyCode;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name="retail_expenses")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class RetailExpense {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="tenant_id",nullable=false) private Long tenantId;
    @Column(name="branch_id",nullable=false) private Long branchId;
    @Column(name="expense_number",nullable=false,length=50) private String expenseNumber;
    @Column(nullable=false,length=180) private String description;
    @Column(length=150) private String vendor;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=40,columnDefinition="varchar(40)") private Category category;
    @Column(nullable=false,precision=15,scale=2) private BigDecimal amount;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=5,columnDefinition="varchar(5)") private CurrencyCode currency;
    @Column(name="payment_method",nullable=false,length=30) private String paymentMethod;
    @Column(name="payment_reference",length=120) private String paymentReference;
    @Column(name="incurred_on",nullable=false) private LocalDate incurredOn;
    @Column(columnDefinition="TEXT") private String notes;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20,columnDefinition="varchar(20)") @Builder.Default private Status status=Status.POSTED;
    @Column(name="created_by",nullable=false) private Long createdBy;
    @Column(name="updated_by",nullable=false) private Long updatedBy;
    @Column(name="voided_by") private Long voidedBy;
    @Column(name="void_reason",length=300) private String voidReason;
    @Column(name="voided_at") private LocalDateTime voidedAt;
    @Column(name="created_at",nullable=false,updatable=false) private LocalDateTime createdAt;
    @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt;
    @Version @Column(nullable=false) private long version;
    @PrePersist void create(){var now=LocalDateTime.now();createdAt=now;updatedAt=now;}
    @PreUpdate void update(){updatedAt=LocalDateTime.now();}

    public enum Status { POSTED, VOIDED }
    public enum Category {
        RENT("Rent & occupancy"),UTILITIES("Utilities"),PAYROLL("Payroll & wages"),TRANSPORT("Transport & delivery"),
        MARKETING("Marketing"),MAINTENANCE("Repairs & maintenance"),BANK_FEES("Bank & payment fees"),
        OFFICE("Office & supplies"),SECURITY("Security"),TAXES("Taxes & licences"),OTHER("Other");
        private final String label; Category(String label){this.label=label;} public String getLabel(){return label;}
    }
}
