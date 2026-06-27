package com.retailzw.repository;

import com.retailzw.model.Return;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReturnRepository extends JpaRepository<Return, Long> {

    @EntityGraph(attributePaths = {"items"})
    Optional<Return> findByReturnNumber(String returnNumber);

    @EntityGraph(attributePaths = {"items"})
    Page<Return> findByTenantIdAndBranchIdAndCreatedAtBetween(Long tenantId, Long branchId,
                                                               LocalDateTime from, LocalDateTime to,
                                                               Pageable pageable);

    @EntityGraph(attributePaths = {"items"})
    Page<Return> findByTenantIdAndBranchId(Long tenantId, Long branchId, Pageable pageable);

    @EntityGraph(attributePaths = {"items"})
    List<Return> findByOriginalSaleIdAndTenantId(Long originalSaleId, Long tenantId);

    @EntityGraph(attributePaths = {"items"})
    @Query("SELECT r FROM Return r WHERE r.tenantId = :tenantId AND r.branchId = :branchId " +
           "AND (:from IS NULL OR r.createdAt >= :from) " +
           "AND (:to IS NULL OR r.createdAt < :to) " +
           "AND (:search IS NULL OR LOWER(r.returnNumber) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(r.originalReceiptNumber) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "ORDER BY r.createdAt DESC")
    Page<Return> searchReturns(@Param("tenantId") Long tenantId,
                               @Param("branchId") Long branchId,
                               @Param("from") LocalDateTime from,
                               @Param("to") LocalDateTime to,
                               @Param("search") String search,
                               Pageable pageable);

    @Query("SELECT COALESCE(SUM(r.totalRefund), 0) FROM Return r WHERE r.tenantId = :tenantId " +
           "AND r.branchId = :branchId AND r.createdAt BETWEEN :from AND :to")
    BigDecimal sumRefunds(@Param("tenantId") Long tenantId,
                          @Param("branchId") Long branchId,
                          @Param("from") LocalDateTime from,
                          @Param("to") LocalDateTime to);
}

