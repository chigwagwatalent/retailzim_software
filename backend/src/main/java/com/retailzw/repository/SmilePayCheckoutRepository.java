package com.retailzw.repository;

import com.retailzw.model.SmilePayCheckout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Collection;

@Repository
public interface SmilePayCheckoutRepository extends JpaRepository<SmilePayCheckout, Long> {
    Optional<SmilePayCheckout> findByOrderReference(String orderReference);

    Optional<SmilePayCheckout> findFirstByTenantIdAndPlanIdAndStatusInOrderByCreatedAtDesc(
            Long tenantId,
            Long planId,
            Collection<SmilePayCheckout.CheckoutStatus> statuses);
}
