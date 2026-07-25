package com.retailzw.repository;

import com.retailzw.model.GasSale;
import com.retailzw.enums.CurrencyCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface GasSaleRepository extends JpaRepository<GasSale, Long> {
    List<GasSale> findByTenantIdAndBranchIdAndGasShiftIdOrderByCreatedAtDesc(Long tenantId, Long branchId, Long gasShiftId);
    List<GasSale> findByTenantIdAndBranchIdOrderByCreatedAtDesc(Long tenantId, Long branchId);
    List<GasSale> findByTenantIdAndBranchIdAndCurrencyAndCreatedAtBetween(Long tenantId, Long branchId, CurrencyCode currency, LocalDateTime start, LocalDateTime end);
    Optional<GasSale> findByTenantIdAndOfflineReceiptNumber(Long tenantId, String offlineReceiptNumber);

    @Query("""
            select sale.currency, coalesce(sum(sale.quantityKg), 0),
                   coalesce(sum(sale.total), 0), count(sale)
            from GasSale sale
            where sale.tenantId = :tenantId
              and sale.branchId = :branchId
              and sale.createdAt >= :from
              and sale.createdAt < :to
              and sale.status = com.retailzw.enums.GasSaleStatus.COMPLETED
            group by sale.currency
            """)
    List<Object[]> dailySummary(@Param("tenantId") Long tenantId,
                                @Param("branchId") Long branchId,
                                @Param("from") LocalDateTime from,
                                @Param("to") LocalDateTime to);

    @Query("""
            select function('HOUR', sale.createdAt), sale.currency, coalesce(sum(sale.total), 0)
            from GasSale sale
            where sale.tenantId = :tenantId
              and sale.branchId = :branchId
              and sale.createdAt >= :from
              and sale.createdAt < :to
              and sale.status = com.retailzw.enums.GasSaleStatus.COMPLETED
            group by function('HOUR', sale.createdAt), sale.currency
            order by function('HOUR', sale.createdAt)
            """)
    List<Object[]> hourlyRevenue(@Param("tenantId") Long tenantId,
                                 @Param("branchId") Long branchId,
                                 @Param("from") LocalDateTime from,
                                 @Param("to") LocalDateTime to);
}
