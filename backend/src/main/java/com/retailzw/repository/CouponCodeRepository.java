package com.retailzw.repository;

import com.retailzw.model.CouponCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface CouponCodeRepository extends JpaRepository<CouponCode, Long> {

    Optional<CouponCode> findByCodeAndTenantId(String code, Long tenantId);

    Optional<CouponCode> findByCode(String code);

    boolean existsByCode(String code);
}

