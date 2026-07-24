package com.retailzw.repository;

import com.retailzw.model.SalePayment;
import com.retailzw.enums.CurrencyCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SalePaymentRepository extends JpaRepository<SalePayment, Long> {

    List<SalePayment> findBySaleId(Long saleId);

    @Query("SELECT COALESCE(SUM(sp.amount), 0) FROM SalePayment sp " +
           "WHERE sp.sale.id = :saleId AND sp.paymentMethod = 'CASH' AND sp.currency = :currency")
    BigDecimal sumCashBySaleAndCurrency(@Param("saleId") Long saleId,
                                        @Param("currency") CurrencyCode currency);

    @Query("SELECT sp.paymentMethod, sp.currency, SUM(sp.amount) FROM SalePayment sp JOIN sp.sale s " +
           "WHERE s.tenantId = :tenantId AND s.branchId = :branchId " +
           "AND s.status IN ('COMPLETED', 'PARTIAL_REFUND', 'REFUNDED') " +
           "AND s.createdAt >= :from AND s.createdAt < :to " +
           "GROUP BY sp.paymentMethod, sp.currency")
    List<Object[]> sumByPaymentMethod(@Param("tenantId") Long tenantId,
                                      @Param("branchId") Long branchId,
                                      @Param("from") LocalDateTime from,
                                      @Param("to") LocalDateTime to);

    @Query("SELECT COALESCE(SUM(sp.amount), 0) FROM SalePayment sp JOIN sp.sale s " +
           "WHERE s.tenantId = :tenantId AND s.branchId = :branchId " +
           "AND sp.paymentMethod = 'CASH' AND sp.currency = :currency " +
           "AND s.status IN ('COMPLETED', 'PARTIAL_REFUND', 'REFUNDED') " +
           "AND s.createdAt >= :from AND s.createdAt < :to")
    BigDecimal sumCashCollected(@Param("tenantId") Long tenantId,
                                @Param("branchId") Long branchId,
                                @Param("currency") CurrencyCode currency,
                                @Param("from") LocalDateTime from,
                                @Param("to") LocalDateTime to);

    @Query("SELECT COALESCE(SUM(sp.amount), 0) FROM SalePayment sp JOIN sp.sale s " +
           "WHERE s.tenantId = :tenantId AND s.branchId = :branchId " +
           "AND sp.currency = :currency AND s.status = 'COMPLETED' " +
           "AND s.createdAt >= :from AND s.createdAt < :to")
    BigDecimal sumCompletedPaymentsByCurrency(@Param("tenantId") Long tenantId,
                                              @Param("branchId") Long branchId,
                                              @Param("currency") CurrencyCode currency,
                                              @Param("from") LocalDateTime from,
                                              @Param("to") LocalDateTime to);
}

