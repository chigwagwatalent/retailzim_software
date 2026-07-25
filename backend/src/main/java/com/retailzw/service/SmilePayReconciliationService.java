package com.retailzw.service;

import com.retailzw.model.SmilePayCheckout;
import com.retailzw.repository.SmilePayCheckoutRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmilePayReconciliationService {

    private static final List<SmilePayCheckout.CheckoutStatus> RECONCILABLE = List.of(
            SmilePayCheckout.CheckoutStatus.PROCESSING);

    private final SmilePayCheckoutRepository checkouts;
    private final SmilePayCheckoutService checkoutService;

    @Scheduled(
            fixedDelayString = "${smilepay.reconciliation-delay-ms:5000}",
            initialDelayString = "${smilepay.reconciliation-initial-delay-ms:10000}")
    public void reconcileDuePayments() {
        LocalDateTime now = LocalDateTime.now();
        List<SmilePayCheckout> due = checkouts.findDueForReconciliation(
                RECONCILABLE,
                now,
                PageRequest.of(0, 100));
        for (SmilePayCheckout checkout : due) {
            int claimed = checkouts.claimForReconciliation(
                    checkout.getId(),
                    now,
                    now.plusSeconds(30));
            if (claimed == 0) {
                continue;
            }
            try {
                checkoutService.verifyAndApply(checkout.getOrderReference());
            } catch (IllegalArgumentException | IllegalStateException ex) {
                log.warn("Smile & Pay reconciliation deferred order={} reason={}",
                        checkout.getOrderReference(), ex.getMessage());
            }
        }
    }
}
