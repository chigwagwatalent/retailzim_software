package com.retailzw.repository;

import com.retailzw.model.Sale;
import com.retailzw.enums.CurrencyCode;
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
public interface SaleRepository extends JpaRepository<Sale, Long> {

    @EntityGraph(attributePaths = {"items"})
    Optional<Sale> findByReceiptNumberAndTenantId(String receiptNumber, Long tenantId);

    Optional<Sale> findByReceiptNumber(String receiptNumber);

    @EntityGraph(attributePaths = {"items"})
    Optional<Sale> findByTenantIdAndOfflineReceiptNumber(Long tenantId, String offlineReceiptNumber);

    @EntityGraph(attributePaths = {"items"})
    Page<Sale> findByTenantIdAndBranchId(Long tenantId, Long branchId, Pageable pageable);

    List<Sale> findByTenantIdAndBranchIdAndCreatedAtBetween(Long tenantId, Long branchId,
                                                             LocalDateTime from, LocalDateTime to);

    @EntityGraph(attributePaths = {"items"})
    Page<Sale> findByTenantIdAndBranchIdAndCreatedAtBetween(Long tenantId, Long branchId,
                                                              LocalDateTime from, LocalDateTime to,
                                                              Pageable pageable);

    @EntityGraph(attributePaths = {"items"})
    @Query("SELECT s FROM Sale s WHERE s.tenantId = :tenantId AND s.branchId = :branchId " +
           "AND (:from IS NULL OR s.createdAt >= :from) " +
           "AND (:to IS NULL OR s.createdAt < :to) " +
           "AND (:status IS NULL OR s.status = :status) " +
           "AND (:cashierId IS NULL OR s.cashierId = :cashierId) " +
           "AND (:search IS NULL OR LOWER(s.receiptNumber) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "ORDER BY s.createdAt DESC")
    Page<Sale> searchSales(@Param("tenantId") Long tenantId,
                           @Param("branchId") Long branchId,
                           @Param("from") LocalDateTime from,
                           @Param("to") LocalDateTime to,
                           @Param("status") Sale.SaleStatus status,
                           @Param("cashierId") Long cashierId,
                           @Param("search") String search,
                           Pageable pageable);

    @EntityGraph(attributePaths = {"items"})
    @Query("SELECT s FROM Sale s WHERE s.tenantId = :tenantId AND s.branchId = :branchId " +
           "AND (:from IS NULL OR s.createdAt >= :from) " +
           "AND (:to IS NULL OR s.createdAt < :to) " +
           "AND (:status IS NULL OR s.status = :status) " +
           "AND (:cashierId IS NULL OR s.cashierId = :cashierId) " +
           "AND (:search IS NULL OR LOWER(s.receiptNumber) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "ORDER BY s.createdAt DESC")
    List<Sale> searchSalesList(@Param("tenantId") Long tenantId,
                               @Param("branchId") Long branchId,
                               @Param("from") LocalDateTime from,
                               @Param("to") LocalDateTime to,
                               @Param("status") Sale.SaleStatus status,
                               @Param("cashierId") Long cashierId,
                               @Param("search") String search);

    long countByTenantIdAndBranchIdAndCreatedAtBetween(Long tenantId, Long branchId,
                                                         LocalDateTime from, LocalDateTime to);

    long countByTenantIdAndCreatedAtBetween(Long tenantId, LocalDateTime from, LocalDateTime to);

    long countByTenantIdAndStatusAndCreatedAtBetween(Long tenantId, Sale.SaleStatus status, LocalDateTime from, LocalDateTime to);

    List<Sale> findTop10ByTenantIdOrderByCreatedAtDesc(Long tenantId);

    @Query("SELECT COALESCE(SUM(s.grandTotal), 0) FROM Sale s WHERE s.tenantId = :tenantId " +
           "AND s.branchId = :branchId AND s.status = 'COMPLETED' " +
           "AND s.createdAt BETWEEN :from AND :to AND s.currency = :currency")
    BigDecimal sumGrandTotalByBranchAndDate(@Param("tenantId") Long tenantId,
                                            @Param("branchId") Long branchId,
                                            @Param("from") LocalDateTime from,
                                            @Param("to") LocalDateTime to,
                                            @Param("currency") String currency);

    @Query("SELECT COALESCE(SUM(s.grandTotal), 0) FROM Sale s WHERE s.tenantId = :tenantId " +
           "AND s.branchId = :branchId AND s.status = 'COMPLETED' " +
           "AND s.createdAt BETWEEN :from AND :to")
    BigDecimal sumGrandTotal(@Param("tenantId") Long tenantId,
                             @Param("branchId") Long branchId,
                             @Param("from") LocalDateTime from,
                             @Param("to") LocalDateTime to);

    @Query("SELECT COALESCE(SUM(s.grandTotal), 0) FROM Sale s WHERE s.tenantId = :tenantId " +
           "AND s.status = 'COMPLETED' AND s.createdAt BETWEEN :from AND :to AND s.currency = :currency")
    BigDecimal sumTenantGrandTotalByCurrency(@Param("tenantId") Long tenantId,
                                             @Param("from") LocalDateTime from,
                                             @Param("to") LocalDateTime to,
                                             @Param("currency") CurrencyCode currency);

    @Query("SELECT COALESCE(SUM(s.grossProfit), 0) FROM Sale s WHERE s.tenantId = :tenantId " +
           "AND s.branchId = :branchId AND s.status = 'COMPLETED' " +
           "AND s.createdAt BETWEEN :from AND :to")
    BigDecimal sumGrossProfit(@Param("tenantId") Long tenantId,
                              @Param("branchId") Long branchId,
                              @Param("from") LocalDateTime from,
                              @Param("to") LocalDateTime to);

    @Query("SELECT COALESCE(SUM(s.grossProfit), 0) FROM Sale s WHERE s.tenantId = :tenantId " +
           "AND s.status = 'COMPLETED' AND s.createdAt BETWEEN :from AND :to")
    BigDecimal sumTenantGrossProfit(@Param("tenantId") Long tenantId,
                                    @Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to);

    List<Sale> findByTenantIdAndBranchIdAndCashierId(Long tenantId, Long branchId, Long cashierId);

    List<Sale> findByTenantIdAndBranchIdAndCashierId(Long tenantId, Long branchId, Long cashierId,
                                                      Pageable pageable);

    @Query("SELECT s FROM Sale s WHERE s.tenantId = :tenantId AND s.cashSessionId = :sessionId")
    List<Sale> findByCashSessionId(@Param("tenantId") Long tenantId, @Param("sessionId") Long sessionId);

    @EntityGraph(attributePaths = {"items"})
    @Query("SELECT s FROM Sale s WHERE s.tenantId = :tenantId AND s.branchId = :branchId AND s.cashierId = :cashierId AND s.cashSessionId = :sessionId ORDER BY s.createdAt DESC")
    List<Sale> findShiftSales(@Param("tenantId") Long tenantId,
                              @Param("branchId") Long branchId,
                              @Param("cashierId") Long cashierId,
                              @Param("sessionId") Long sessionId);

    long countByTenantIdAndBranchIdAndStatusAndCreatedAtBetween(Long tenantId, Long branchId,
                                                                   Sale.SaleStatus status,
                                                                   LocalDateTime from, LocalDateTime to);
}

