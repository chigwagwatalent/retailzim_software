package com.retailzw.repository;

import com.retailzw.model.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    Optional<AttendanceRecord> findByUserIdAndAttendanceDate(Long userId, LocalDate attendanceDate);

    List<AttendanceRecord> findByTenantIdAndBranchIdAndAttendanceDateBetweenOrderByAttendanceDateDesc(
            Long tenantId, Long branchId, LocalDate from, LocalDate to);

    @Query("SELECT ar FROM AttendanceRecord ar WHERE ar.tenantId = :tenantId " +
           "AND ar.userId = :userId AND ar.attendanceDate BETWEEN :from AND :to " +
           "ORDER BY ar.attendanceDate DESC")
    List<AttendanceRecord> findUserAttendance(@Param("tenantId") Long tenantId,
                                              @Param("userId") Long userId,
                                              @Param("from") LocalDate from,
                                              @Param("to") LocalDate to);
}

