package com.retailzw.repository;

import com.retailzw.model.GasSalePayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface GasSalePaymentRepository extends JpaRepository<GasSalePayment, Long> {
    List<GasSalePayment> findByGasSaleIdOrderById(Long gasSaleId);

    @Query("""
            select payment.paymentMethod, sale.currency, coalesce(sum(payment.amount), 0)
            from GasSalePayment payment, GasSale sale
            where payment.tenantId = :tenantId
              and payment.branchId = :branchId
              and sale.id = payment.gasSaleId
              and payment.createdAt >= :from
              and payment.createdAt < :to
            group by payment.paymentMethod, sale.currency
            order by sum(payment.amount) desc
            """)
    List<Object[]> paymentMix(@Param("tenantId") Long tenantId,
                              @Param("branchId") Long branchId,
                              @Param("from") LocalDateTime from,
                              @Param("to") LocalDateTime to);
}
