package com.retailzw.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "attendance_records")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Column(name = "clock_in_at")
    private LocalDateTime clockInAt;

    @Column(name = "clock_out_at")
    private LocalDateTime clockOutAt;

    @Column(name = "hours_worked", precision = 5, scale = 2)
    private BigDecimal hoursWorked;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private AttendanceStatus status = AttendanceStatus.PRESENT;

    @Column(length = 255)
    private String notes;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum AttendanceStatus {
        PRESENT, ABSENT, LATE, HALF_DAY, ON_LEAVE, HOLIDAY
    }
}

