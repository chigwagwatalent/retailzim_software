package com.retailzw.repository;

import com.retailzw.enums.CurrencyCode;
import com.retailzw.model.GasExpense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface GasExpenseRepository extends JpaRepository<GasExpense, Long> {
    List<GasExpense> findByTenantIdAndBranchIdOrderByCreatedAtDesc(Long tenantId, Long branchId);
    List<GasExpense> findByTenantIdAndBranchIdAndCurrencyAndCreatedAtBetween(Long tenantId, Long branchId, CurrencyCode currency, LocalDateTime start, LocalDateTime end);

    @Query("""
            select expense from GasExpense expense
            where expense.tenantId = :tenantId
              and expense.branchId = :branchId
              and expense.createdAt >= :from
              and expense.createdAt < :to
              and (:query is null
                   or lower(expense.description) like lower(concat('%', :query, '%'))
                   or lower(expense.reference) like lower(concat('%', :query, '%')))
              and (:category is null or lower(expense.category) = lower(:category))
              and (:paymentMethod is null or expense.paymentMethod = :paymentMethod)
            order by expense.createdAt desc, expense.id desc
            """)
    Page<GasExpense> search(@Param("tenantId") Long tenantId,
                            @Param("branchId") Long branchId,
                            @Param("from") LocalDateTime from,
                            @Param("to") LocalDateTime to,
                            @Param("query") String query,
                            @Param("category") String category,
                            @Param("paymentMethod") String paymentMethod,
                            Pageable pageable);

    @Query("""
            select expense.currency, coalesce(sum(expense.amount), 0), count(expense), max(expense.createdAt)
            from GasExpense expense
            where expense.tenantId = :tenantId
              and expense.branchId = :branchId
              and expense.createdAt >= :from
              and expense.createdAt < :to
            group by expense.currency
            """)
    List<Object[]> summarizeByCurrency(@Param("tenantId") Long tenantId,
                                       @Param("branchId") Long branchId,
                                       @Param("from") LocalDateTime from,
                                       @Param("to") LocalDateTime to);

    @Query("""
            select expense.category, expense.currency, coalesce(sum(expense.amount), 0), count(expense)
            from GasExpense expense
            where expense.tenantId = :tenantId
              and expense.branchId = :branchId
              and expense.createdAt >= :from
              and expense.createdAt < :to
            group by expense.category, expense.currency
            order by sum(expense.amount) desc
            """)
    List<Object[]> summarizeByCategory(@Param("tenantId") Long tenantId,
                                       @Param("branchId") Long branchId,
                                       @Param("from") LocalDateTime from,
                                       @Param("to") LocalDateTime to);

    @Query("""
            select function('date', expense.createdAt), expense.currency,
                   coalesce(sum(expense.amount), 0), count(expense)
            from GasExpense expense
            where expense.tenantId = :tenantId
              and expense.branchId = :branchId
              and expense.createdAt >= :from
              and expense.createdAt < :to
            group by function('date', expense.createdAt), expense.currency
            order by function('date', expense.createdAt)
            """)
    List<Object[]> summarizeByDay(@Param("tenantId") Long tenantId,
                                  @Param("branchId") Long branchId,
                                  @Param("from") LocalDateTime from,
                                  @Param("to") LocalDateTime to);

    @Query("""
            select distinct expense.category
            from GasExpense expense
            where expense.tenantId = :tenantId and expense.branchId = :branchId
            order by expense.category
            """)
    List<String> distinctCategories(@Param("tenantId") Long tenantId,
                                    @Param("branchId") Long branchId);
}
