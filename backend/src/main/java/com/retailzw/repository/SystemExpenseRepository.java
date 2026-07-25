package com.retailzw.repository;

import com.retailzw.enums.CurrencyCode;
import com.retailzw.model.SystemExpense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface SystemExpenseRepository extends JpaRepository<SystemExpense, Long> {

    @Query("""
            SELECT expense
            FROM SystemExpense expense
            WHERE expense.incurredOn >= :from AND expense.incurredOn < :to
              AND (:currency IS NULL OR expense.currency = :currency)
              AND (:category IS NULL OR expense.category = :category)
              AND (:status IS NULL OR expense.status = :status)
              AND (:search IS NULL
                   OR LOWER(expense.description) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(COALESCE(expense.vendor, '')) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR LOWER(COALESCE(expense.paymentReference, '')) LIKE LOWER(CONCAT('%', :search, '%')))
            ORDER BY expense.incurredOn DESC, expense.id DESC
            """)
    Page<SystemExpense> search(@Param("from") LocalDate from,
                               @Param("to") LocalDate to,
                               @Param("currency") CurrencyCode currency,
                               @Param("category") SystemExpense.ExpenseCategory category,
                               @Param("status") SystemExpense.ExpenseStatus status,
                               @Param("search") String search,
                               Pageable pageable);

    @Query("""
            SELECT expense.currency, COALESCE(SUM(expense.amount), 0), COUNT(expense)
            FROM SystemExpense expense
            WHERE expense.incurredOn >= :from AND expense.incurredOn < :to
              AND expense.status = :posted
            GROUP BY expense.currency
            """)
    List<Object[]> summarizePosted(@Param("from") LocalDate from,
                                   @Param("to") LocalDate to,
                                   @Param("posted") SystemExpense.ExpenseStatus posted);

    @Query("""
            SELECT expense.category, COALESCE(SUM(expense.amount), 0)
            FROM SystemExpense expense
            WHERE expense.incurredOn >= :from AND expense.incurredOn < :to
              AND expense.currency = :currency
              AND expense.status = :posted
            GROUP BY expense.category
            ORDER BY SUM(expense.amount) DESC
            """)
    List<Object[]> categoryTotals(@Param("from") LocalDate from,
                                  @Param("to") LocalDate to,
                                  @Param("currency") CurrencyCode currency,
                                  @Param("posted") SystemExpense.ExpenseStatus posted);

    @Query("""
            SELECT FUNCTION('DATE_FORMAT', expense.incurredOn, '%Y-%m'),
                   expense.currency, COALESCE(SUM(expense.amount), 0)
            FROM SystemExpense expense
            WHERE expense.incurredOn >= :from AND expense.incurredOn < :to
              AND expense.status = :posted
            GROUP BY FUNCTION('DATE_FORMAT', expense.incurredOn, '%Y-%m'), expense.currency
            ORDER BY FUNCTION('DATE_FORMAT', expense.incurredOn, '%Y-%m')
            """)
    List<Object[]> monthlyTotals(@Param("from") LocalDate from,
                                 @Param("to") LocalDate to,
                                 @Param("posted") SystemExpense.ExpenseStatus posted);
}
