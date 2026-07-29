package com.retailzw.repository;

import com.retailzw.model.GasRestock;
import com.retailzw.enums.CurrencyCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface GasRestockRepository extends JpaRepository<GasRestock, Long> {
    List<GasRestock> findByTenantIdAndBranchIdOrderByCreatedAtDesc(Long tenantId, Long branchId);
    List<GasRestock> findByTenantIdAndBranchIdAndCurrencyAndCreatedAtBetween(Long tenantId, Long branchId, CurrencyCode currency, LocalDateTime start, LocalDateTime end);

    @Query("""
            select receipt from GasRestock receipt
            where receipt.tenantId = :tenantId
              and receipt.branchId = :branchId
              and receipt.createdAt >= :from
              and receipt.createdAt < :to
              and (:query is null
                   or lower(receipt.supplierName) like lower(concat('%', :query, '%'))
                   or lower(receipt.supplierInvoice) like lower(concat('%', :query, '%')))
              and (:tankId is null or receipt.tankId = :tankId)
              and (:currency is null or receipt.currency = :currency)
            order by receipt.createdAt desc, receipt.id desc
            """)
    Page<GasRestock> search(@Param("tenantId") Long tenantId,
                            @Param("branchId") Long branchId,
                            @Param("from") LocalDateTime from,
                            @Param("to") LocalDateTime to,
                            @Param("query") String query,
                            @Param("tankId") Long tankId,
                            @Param("currency") CurrencyCode currency,
                            Pageable pageable);

    @Query("""
            select coalesce(sum(receipt.quantityKg), 0), count(receipt), max(receipt.createdAt)
            from GasRestock receipt
            where receipt.tenantId = :tenantId
              and receipt.branchId = :branchId
              and receipt.createdAt >= :from
              and receipt.createdAt < :to
            """)
    List<Object[]> summarize(@Param("tenantId") Long tenantId,
                             @Param("branchId") Long branchId,
                             @Param("from") LocalDateTime from,
                             @Param("to") LocalDateTime to);
}
