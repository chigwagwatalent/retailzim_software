package com.retailzw.repository;

import com.retailzw.enums.CurrencyCode;
import com.retailzw.model.RetailExpense;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public interface RetailExpenseRepository extends JpaRepository<RetailExpense,Long> {
    Optional<RetailExpense> findByIdAndTenantId(Long id,Long tenantId);
    @Query("select e from RetailExpense e where e.tenantId=:tenant and (:branch is null or e.branchId=:branch) and e.incurredOn>=:from and e.incurredOn<:to " +
      "and (:currency is null or e.currency=:currency) and (:category is null or e.category=:category) and (:status is null or e.status=:status) " +
      "and (:search is null or lower(e.description) like lower(concat('%',:search,'%')) or lower(coalesce(e.vendor,'')) like lower(concat('%',:search,'%')) or lower(e.expenseNumber) like lower(concat('%',:search,'%'))) order by e.incurredOn desc,e.id desc")
    Page<RetailExpense> search(@Param("tenant")Long tenant,@Param("branch")Long branch,@Param("from")LocalDate from,@Param("to")LocalDate to,
      @Param("currency")CurrencyCode currency,@Param("category")RetailExpense.Category category,@Param("status")RetailExpense.Status status,@Param("search")String search,Pageable pageable);
    @Query("select e.currency,coalesce(sum(e.amount),0),count(e) from RetailExpense e where e.tenantId=:tenant and (:branch is null or e.branchId=:branch) and e.incurredOn>=:from and e.incurredOn<:to and e.status='POSTED' and (:currency is null or e.currency=:currency) and (:category is null or e.category=:category) and (:search is null or lower(e.description) like lower(concat('%',:search,'%')) or lower(coalesce(e.vendor,'')) like lower(concat('%',:search,'%')) or lower(e.expenseNumber) like lower(concat('%',:search,'%'))) group by e.currency")
    List<Object[]> summary(@Param("tenant")Long tenant,@Param("branch")Long branch,@Param("from")LocalDate from,@Param("to")LocalDate to,@Param("currency")CurrencyCode currency,@Param("category")RetailExpense.Category category,@Param("search")String search);
    @Query("select coalesce(sum(e.amount),0) from RetailExpense e where e.tenantId=:tenant and (:branch is null or e.branchId=:branch) and e.currency=:currency and e.status='POSTED' and e.incurredOn>=:from and e.incurredOn<:to")
    BigDecimal totalPosted(@Param("tenant")Long tenant,@Param("branch")Long branch,@Param("currency")CurrencyCode currency,@Param("from")LocalDate from,@Param("to")LocalDate to);
    @Query("select coalesce(sum(e.amount),0) from RetailExpense e where e.tenantId=:tenant and (:branch is null or e.branchId=:branch) and e.currency=:currency and e.paymentMethod=:method and e.status='POSTED' and e.incurredOn>=:from and e.incurredOn<:to")
    BigDecimal totalPostedByPayment(@Param("tenant")Long tenant,@Param("branch")Long branch,@Param("currency")CurrencyCode currency,@Param("method")String method,@Param("from")LocalDate from,@Param("to")LocalDate to);
    @Query("select e.category,e.currency,sum(e.amount) from RetailExpense e where e.tenantId=:tenant and (:branch is null or e.branchId=:branch) and e.status='POSTED' and e.incurredOn>=:from and e.incurredOn<:to and (:currency is null or e.currency=:currency) and (:category is null or e.category=:category) and (:search is null or lower(e.description) like lower(concat('%',:search,'%')) or lower(coalesce(e.vendor,'')) like lower(concat('%',:search,'%')) or lower(e.expenseNumber) like lower(concat('%',:search,'%'))) group by e.category,e.currency order by sum(e.amount) desc")
    List<Object[]> categoryTotals(@Param("tenant")Long tenant,@Param("branch")Long branch,@Param("from")LocalDate from,@Param("to")LocalDate to,@Param("currency")CurrencyCode currency,@Param("category")RetailExpense.Category category,@Param("search")String search);
    @Query("select e.branchId,e.currency,sum(e.amount) from RetailExpense e where e.tenantId=:tenant and (:branch is null or e.branchId=:branch) and e.status='POSTED' and e.incurredOn>=:from and e.incurredOn<:to and (:currency is null or e.currency=:currency) and (:category is null or e.category=:category) and (:search is null or lower(e.description) like lower(concat('%',:search,'%')) or lower(coalesce(e.vendor,'')) like lower(concat('%',:search,'%')) or lower(e.expenseNumber) like lower(concat('%',:search,'%'))) group by e.branchId,e.currency order by sum(e.amount) desc")
    List<Object[]> branchTotals(@Param("tenant")Long tenant,@Param("branch")Long branch,@Param("from")LocalDate from,@Param("to")LocalDate to,@Param("currency")CurrencyCode currency,@Param("category")RetailExpense.Category category,@Param("search")String search);
}
