package com.retailzw.repository;

import com.retailzw.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("SELECT al FROM AuditLog al WHERE al.tenantId = :tenantId " +
           "AND (:branchId IS NULL OR al.branchId = :branchId) " +
           "AND (:userId IS NULL OR al.userId = :userId) " +
           "AND (:action IS NULL OR LOWER(al.action) LIKE LOWER(CONCAT('%', :action, '%'))) " +
           "AND (:entityType IS NULL OR al.entityType = :entityType) " +
           "AND al.createdAt BETWEEN :from AND :to " +
           "ORDER BY al.createdAt DESC")
    Page<AuditLog> searchAuditLogs(@Param("tenantId") Long tenantId,
                                    @Param("branchId") Long branchId,
                                    @Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to,
                                    @Param("userId") Long userId,
                                    @Param("action") String action,
                                    @Param("entityType") String entityType,
                                    Pageable pageable);
}

