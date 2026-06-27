package com.retailzw.repository;

import com.retailzw.model.SaleItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {

    List<SaleItem> findBySaleId(Long saleId);

    @Query("SELECT si FROM SaleItem si JOIN si.sale s WHERE s.tenantId = :tenantId " +
           "AND s.branchId = :branchId AND si.productId = :productId " +
           "AND s.createdAt BETWEEN :from AND :to AND s.status = 'COMPLETED'")
    List<SaleItem> findProductSalesInPeriod(@Param("tenantId") Long tenantId,
                                            @Param("branchId") Long branchId,
                                            @Param("productId") Long productId,
                                            @Param("from") LocalDateTime from,
                                            @Param("to") LocalDateTime to);

    @Query("SELECT si.productId, si.productName, SUM(si.quantity) as totalQty, " +
           "SUM(si.lineTotal) as totalRevenue FROM SaleItem si JOIN si.sale s " +
           "WHERE s.tenantId = :tenantId AND s.branchId = :branchId " +
           "AND s.status = 'COMPLETED' AND s.createdAt BETWEEN :from AND :to " +
           "GROUP BY si.productId, si.productName ORDER BY totalRevenue DESC")
    List<Object[]> getTopSellingProducts(@Param("tenantId") Long tenantId,
                                         @Param("branchId") Long branchId,
                                         @Param("from") LocalDateTime from,
                                         @Param("to") LocalDateTime to);
}

