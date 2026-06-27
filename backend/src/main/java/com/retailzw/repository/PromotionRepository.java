package com.retailzw.repository;

import com.retailzw.model.Promotion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    @Query("SELECT p FROM Promotion p WHERE p.tenantId = :tenantId AND p.isActive = true " +
           "AND (p.branchId IS NULL OR p.branchId = :branchId) " +
           "AND (p.startsAt IS NULL OR p.startsAt <= :now) " +
           "AND (p.endsAt IS NULL OR p.endsAt >= :now) " +
           "ORDER BY p.priority DESC")
    List<Promotion> findActivePromotions(@Param("tenantId") Long tenantId,
                                        @Param("branchId") Long branchId,
                                        @Param("now") LocalDateTime now);

    Page<Promotion> findByTenantId(Long tenantId, Pageable pageable);
}

