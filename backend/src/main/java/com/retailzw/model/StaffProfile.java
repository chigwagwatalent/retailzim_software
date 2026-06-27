package com.retailzw.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "staff_profiles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "branch_id")
    private Long branchId;

    @Column(name = "department", length = 100)
    private String department;

    @Column(name = "job_title", length = 100)
    private String jobTitle;

    @Column(name = "hire_date")
    private LocalDate hireDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "national_id", length = 50)
    private String nationalId;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(length = 10)
    private String gender;

    @Column(name = "emergency_contact_name", length = 150)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = 20)
    private String emergencyContactPhone;

    @Column(name = "salary_usd", precision = 15, scale = 2)
    private BigDecimal salaryUsd;

    @Column(name = "salary_zwg", precision = 15, scale = 2)
    private BigDecimal salaryZwg;

    @Column(name = "annual_leave_days")
    @Builder.Default
    private Integer annualLeaveDays = 20;

    @Column(name = "leave_days_used")
    @Builder.Default
    private Integer leaveDaysUsed = 0;

    @Column(columnDefinition = "TEXT")
    private String notes;

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
}

