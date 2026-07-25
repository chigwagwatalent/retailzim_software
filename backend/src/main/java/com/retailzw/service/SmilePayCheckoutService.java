package com.retailzw.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailzw.enums.CurrencyCode;
import com.retailzw.model.SaasPlan;
import com.retailzw.model.SmilePayCheckout;
import com.retailzw.model.PaymentNotificationOutbox;
import com.retailzw.model.SubscriptionPayment;
import com.retailzw.model.Tenant;
import com.retailzw.model.TenantSubscription;
import com.retailzw.repository.SaasPlanRepository;
import com.retailzw.repository.SmilePayCheckoutRepository;
import com.retailzw.repository.PaymentNotificationOutboxRepository;
import com.retailzw.repository.SubscriptionPaymentRepository;
import com.retailzw.repository.TenantRepository;
import com.retailzw.repository.TenantSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmilePayCheckoutService {

    private static final String USD_NUMERIC_CODE = "840";
    private static final Duration PROVIDER_FAILURE_GRACE = Duration.ofMinutes(3);
    private static final String INITIATION_PENDING = "INITIATION_PENDING";
    private static final Collection<SmilePayCheckout.CheckoutStatus> REUSABLE_STATUSES =
            java.util.List.of(
                    SmilePayCheckout.CheckoutStatus.PENDING,
                    SmilePayCheckout.CheckoutStatus.AWAITING_OTP,
                    SmilePayCheckout.CheckoutStatus.PROCESSING);

    private final SmilePayCheckoutRepository checkouts;
    private final TenantRepository tenants;
    private final SaasPlanRepository plans;
    private final TenantSubscriptionRepository tenantSubscriptions;
    private final SubscriptionPaymentRepository subscriptionPayments;
    private final PaymentNotificationOutboxRepository paymentNotifications;
    private final PackageModuleAccessService packageModuleAccess;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Value("${app.base-url:https://retailzw.co.zw}")
    private String appBaseUrl;

    @Value("${smilepay.base-url:https://zbnet.zb.co.zw/wallet_gateway/payments-gateway}")
    private String baseUrl;

    @Value("${smilepay.api-key:}")
    private String apiKey;

    @Value("${smilepay.api-secret:}")
    private String apiSecret;

    @Transactional
    public SmilePayCheckout createCheckout(Long tenantId) {
        return createSignupCheckout(tenantId);
    }

    @Transactional
    public SmilePayCheckout createSignupCheckout(Long tenantId) {
        Tenant tenant = tenants.findById(tenantId).orElseThrow();
        if (tenant.getPlanId() == null) {
            throw new IllegalArgumentException("Choose a package before checkout.");
        }
        return createSignupCheckout(tenantId, tenant.getPlanId());
    }

    @Transactional
    public SmilePayCheckout createCheckout(Long tenantId, Long planId) {
        return createSignupCheckout(tenantId, planId);
    }

    @Transactional
    public SmilePayCheckout createSignupCheckout(Long tenantId, Long planId) {
        Tenant tenant = tenants.findById(tenantId).orElseThrow();
        SaasPlan plan = activePlan(planId);
        return createCheckout(
                tenant,
                plan,
                SmilePayCheckout.CheckoutPurpose.SIGNUP_ACTIVATION,
                cycleMonths(plan),
                null);
    }

    @Transactional
    public SmilePayCheckout createRenewalCheckout(
            Long tenantId,
            Long planId,
            int billingMonths,
            Long createdByUserId) {
        Tenant tenant = tenants.findById(tenantId).orElseThrow();
        SaasPlan plan = activePlan(planId);
        validateRenewalMonths(plan, billingMonths);
        SmilePayCheckout.CheckoutPurpose purpose = plan.getId().equals(tenant.getPlanId())
                ? SmilePayCheckout.CheckoutPurpose.SUBSCRIPTION_RENEWAL
                : SmilePayCheckout.CheckoutPurpose.PLAN_CHANGE;
        return createCheckout(tenant, plan, purpose, billingMonths, createdByUserId);
    }

    @Transactional
    public SmilePayCheckout createRenewalCheckout(Long tenantId) {
        Tenant tenant = tenants.findById(tenantId).orElseThrow();
        if (tenant.getPlanId() == null) {
            throw new IllegalArgumentException("Choose a package before checkout.");
        }
        SaasPlan plan = activePlan(tenant.getPlanId());
        return createRenewalCheckout(tenantId, plan.getId(), cycleMonths(plan), null);
    }

    public List<Integer> allowedRenewalMonths(SaasPlan plan) {
        int cycleMonths = cycleMonths(plan);
        return List.of(1, 3, 6, 12, 24).stream()
                .filter(months -> months >= cycleMonths && months % cycleMonths == 0)
                .toList();
    }

    @Transactional(readOnly = true)
    public RenewalQuote quoteRenewal(Long tenantId, Long planId, int billingMonths) {
        Tenant tenant = tenants.findById(tenantId).orElseThrow();
        SaasPlan plan = activePlan(planId);
        validateRenewalMonths(plan, billingMonths);
        BigDecimal unitPrice = safeMoney(plan.getPriceUsd());
        BigDecimal total = renewalAmount(plan, billingMonths);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime previousEnd = tenantSubscriptions
                .findByTenantIdAndStatus(tenantId, TenantSubscription.SubscriptionStatus.ACTIVE)
                .map(TenantSubscription::getEndsAt)
                .orElse(null);
        boolean samePlan = plan.getId().equals(tenant.getPlanId());
        LocalDateTime startsAt = samePlan && previousEnd != null && previousEnd.isAfter(now)
                ? previousEnd
                : now;
        return new RenewalQuote(
                plan,
                billingMonths,
                unitPrice,
                total,
                previousEnd,
                startsAt.plusMonths(billingMonths),
                samePlan
                        ? SmilePayCheckout.CheckoutPurpose.SUBSCRIPTION_RENEWAL
                        : SmilePayCheckout.CheckoutPurpose.PLAN_CHANGE);
    }

    public BigDecimal renewalAmount(SaasPlan plan, int billingMonths) {
        validateRenewalMonths(plan, billingMonths);
        return safeMoney(plan.getPriceUsd())
                .multiply(BigDecimal.valueOf(billingMonths))
                .divide(BigDecimal.valueOf(cycleMonths(plan)), 2, RoundingMode.HALF_UP);
    }

    private SmilePayCheckout createCheckout(
            Tenant tenant,
            SaasPlan plan,
            SmilePayCheckout.CheckoutPurpose purpose,
            int billingMonths,
            Long createdByUserId) {
        requireConfigured();
        BigDecimal unitPrice = safeMoney(plan.getPriceUsd());
        BigDecimal total = renewalAmount(plan, billingMonths);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime currentEnd = tenantSubscriptions
                .findByTenantIdAndStatus(tenant.getId(), TenantSubscription.SubscriptionStatus.ACTIVE)
                .map(TenantSubscription::getEndsAt)
                .orElse(null);
        LocalDateTime projectedStart = SmilePayCheckout.CheckoutPurpose.SUBSCRIPTION_RENEWAL.equals(purpose)
                && currentEnd != null
                && currentEnd.isAfter(now)
                ? currentEnd
                : now;

        return checkouts
                .findFirstByTenantIdAndPlanIdAndCheckoutPurposeAndBillingMonthsAndStatusInOrderByCreatedAtDesc(
                        tenant.getId(), plan.getId(), purpose, billingMonths, REUSABLE_STATUSES)
                .filter(existing -> existing.getExpiresAt() == null
                        || existing.getExpiresAt().isAfter(LocalDateTime.now()))
                .orElseGet(() -> checkouts.save(SmilePayCheckout.builder()
                        .tenantId(tenant.getId())
                        .planId(plan.getId())
                        .checkoutPurpose(purpose)
                        .billingMonths(billingMonths)
                        .unitPrice(unitPrice)
                        .previousPeriodEnd(currentEnd)
                        .newPeriodEnd(projectedStart.plusMonths(billingMonths))
                        .createdByUserId(createdByUserId)
                        .orderReference(newOrderReference(tenant.getId()))
                        .amount(total)
                        .currency(CurrencyCode.USD)
                        .status(SmilePayCheckout.CheckoutStatus.PENDING)
                        .expiresAt(LocalDateTime.now().plusHours(24))
                        .build()));
    }

    @Transactional(readOnly = true)
    public CheckoutView checkoutView(String orderReference) {
        SmilePayCheckout checkout = checkout(orderReference);
        Tenant tenant = tenants.findById(checkout.getTenantId()).orElseThrow();
        SaasPlan plan = plans.findById(checkout.getPlanId()).orElseThrow();
        return new CheckoutView(checkout, tenant, plan);
    }

    @Transactional(readOnly = true)
    public CheckoutView signupCheckoutView(String accessToken) {
        SmilePayCheckout checkout = checkouts.findByAccessToken(accessToken)
                .orElseThrow(() -> new IllegalArgumentException("Checkout not found."));
        requirePurpose(checkout, SmilePayCheckout.CheckoutPurpose.SIGNUP_ACTIVATION);
        return checkoutView(checkout.getOrderReference());
    }

    @Transactional(readOnly = true)
    public CheckoutView renewalCheckoutView(String orderReference, Long tenantId) {
        SmilePayCheckout checkout = checkout(orderReference);
        requireTenant(checkout, tenantId);
        if (SmilePayCheckout.CheckoutPurpose.SIGNUP_ACTIVATION.equals(checkout.getCheckoutPurpose())) {
            throw new IllegalArgumentException("This is an account activation checkout.");
        }
        return checkoutView(orderReference);
    }

    @Transactional(readOnly = true)
    public SmilePayCheckout requireSignupCheckout(String accessToken) {
        SmilePayCheckout checkout = checkouts.findByAccessToken(accessToken)
                .orElseThrow(() -> new IllegalArgumentException("Checkout not found."));
        requirePurpose(checkout, SmilePayCheckout.CheckoutPurpose.SIGNUP_ACTIVATION);
        return checkout;
    }

    @Transactional(readOnly = true)
    public SmilePayCheckout requireSignupOrderReference(String orderReference) {
        SmilePayCheckout checkout = checkout(orderReference);
        requirePurpose(checkout, SmilePayCheckout.CheckoutPurpose.SIGNUP_ACTIVATION);
        return checkout;
    }

    @Transactional(readOnly = true)
    public SmilePayCheckout requireRenewalCheckout(String orderReference, Long tenantId) {
        SmilePayCheckout checkout = checkout(orderReference);
        requireTenant(checkout, tenantId);
        if (SmilePayCheckout.CheckoutPurpose.SIGNUP_ACTIVATION.equals(checkout.getCheckoutPurpose())) {
            throw new IllegalArgumentException("This is an account activation checkout.");
        }
        return checkout;
    }

    @Transactional
    public ExpressResult initiateExpress(
            String orderReference,
            SmilePayCheckout.PaymentMethod method,
            String mobile,
            Map<String, String> card) {
        requireConfigured();
        SmilePayCheckout checkout = checkout(orderReference);
        ensurePayable(checkout);
        Tenant tenant = tenants.findById(checkout.getTenantId()).orElseThrow();
        SaasPlan plan = plans.findById(checkout.getPlanId()).orElseThrow();

        String cleanMobile = clean(mobile);
        if (method.isMobileRequired() && cleanMobile == null) {
            throw new IllegalArgumentException("Enter the mobile number registered with " + method.getLabel() + ".");
        }

        Map<String, Object> payload = commonPayload(checkout, tenant, plan);
        switch (method) {
            case ECOCASH -> payload.put("ecocashMobile", cleanMobile);
            case SMILECASH -> payload.put("zbWalletMobile", cleanMobile);
            case OMARI -> payload.put("omariMobile", cleanMobile);
            case ONEMONEY -> payload.put("oneMoneyMobile", cleanMobile);
            case INNBUCKS -> {
                // InnBucks completes authorization in the customer's InnBucks application.
            }
            case CARD -> addCardPayload(payload, tenant, card);
        }

        checkout.setPaymentMethod(method);
        checkout.setCustomerMobile(cleanMobile);
        checkout.setInitiatedAt(LocalDateTime.now());
        Map<String, Object> response;
        try {
            response = post("/payments/express-checkout/" + method.getEndpoint(), payload);
        } catch (ProviderRequestException ex) {
            if (!ex.retryable()) {
                throw ex;
            }
            checkout.setRawResponse(ex.safeResponse());
            checkout.setProviderStatus(INITIATION_PENDING);
            checkout.setStatus(SmilePayCheckout.CheckoutStatus.PROCESSING);
            checkout.setNextCheckAt(LocalDateTime.now().plusSeconds(10));
            SmilePayCheckout saved = checkouts.save(checkout);
            log.warn("Smile & Pay initiation needs reconciliation order={} method={} status={}",
                    orderReference, method, ex.statusCode());
            return new ExpressResult(
                    saved,
                    null,
                    false,
                    "Payment request sent for verification. Check your phone and keep this page open.");
        }
        checkout.setRawResponse(toJson(response));
        checkout.setProviderReference(firstString(response, "transactionReference", "reference", "id"));
        checkout.setProviderStatus(firstString(response, "status", "transactionStatus", "responseMessage", "message"));
        checkout.setPaymentUrl(firstString(response, "paymentUrl", "checkoutUrl", "redirectUrl", "url"));
        checkout.setStatus(method.isOtpRequired()
                ? SmilePayCheckout.CheckoutStatus.AWAITING_OTP
                : SmilePayCheckout.CheckoutStatus.PROCESSING);
        checkout.setNextCheckAt(LocalDateTime.now().plusSeconds(5));
        SmilePayCheckout saved = checkouts.save(checkout);

        String redirectHtml = method == SmilePayCheckout.PaymentMethod.CARD
                ? firstString(response, "redirectHtml")
                : null;
        return new ExpressResult(
                saved,
                redirectHtml,
                method.isOtpRequired(),
                firstString(response, "responseMessage", "message", "rawMessage"));
    }

    @Transactional
    public ExpressResult confirmOtp(String orderReference, String otp) {
        requireConfigured();
        SmilePayCheckout checkout = checkout(orderReference);
        if (!SmilePayCheckout.CheckoutStatus.AWAITING_OTP.equals(checkout.getStatus())
                || checkout.getPaymentMethod() == null
                || !checkout.getPaymentMethod().isOtpRequired()) {
            throw new IllegalStateException("This payment is not waiting for an OTP.");
        }
        if (clean(otp) == null) {
            throw new IllegalArgumentException("Enter the OTP sent to the customer.");
        }
        if (clean(checkout.getProviderReference()) == null) {
            throw new IllegalStateException("Smile & Pay did not return a transaction reference.");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transactionReference", checkout.getProviderReference());
        payload.put("otp", otp.trim());
        if (SmilePayCheckout.PaymentMethod.OMARI.equals(checkout.getPaymentMethod())) {
            payload.put("omariMobile", checkout.getCustomerMobile());
        }
        Map<String, Object> response = post(
                "/payments/express-checkout/" + checkout.getPaymentMethod().getEndpoint() + "/confirmation",
                payload);
        checkout.setRawResponse(toJson(response));
        checkout.setProviderStatus(firstString(response, "status", "transactionStatus", "responseMessage", "message"));
        checkout.setStatus(SmilePayCheckout.CheckoutStatus.PROCESSING);
        checkout.setNextCheckAt(LocalDateTime.now().plusSeconds(3));
        checkouts.save(checkout);

        SmilePayCheckout verified = verifyAndApply(orderReference);
        return new ExpressResult(
                verified,
                null,
                false,
                firstString(response, "responseMessage", "message", "rawMessage"));
    }

    @Transactional
    public SmilePayCheckout verifyAndApply(String orderReference) {
        SmilePayCheckout checkout = checkout(orderReference);
        if (SmilePayCheckout.CheckoutStatus.PAID.equals(checkout.getStatus())) {
            queueInvoiceIfNeeded(checkout, null);
            return checkout;
        }
        requireConfigured();
        Map<String, Object> response;
        try {
            response = get("/payments/transaction/" + orderReference + "/status/check");
        } catch (ProviderRequestException ex) {
            if (!ex.retryable()) {
                throw ex;
            }
            checkout.setLastCheckedAt(LocalDateTime.now());
            checkout.setNextCheckAt(LocalDateTime.now().plusSeconds(30));
            checkout.setRawResponse(ex.safeResponse());
            if (!SmilePayCheckout.CheckoutStatus.AWAITING_OTP.equals(checkout.getStatus())) {
                checkout.setStatus(SmilePayCheckout.CheckoutStatus.PROCESSING);
            }
            return checkouts.save(checkout);
        }
        String previousProviderStatus = checkout.getProviderStatus();
        checkout.setRawResponse(toJson(response));
        checkout.setProviderReference(valueOrCurrent(
                firstString(response, "transactionReference", "reference", "id"),
                checkout.getProviderReference()));
        checkout.setProviderStatus(firstString(response, "status", "transactionStatus", "paymentStatus", "message"));
        checkout.setLastCheckedAt(LocalDateTime.now());
        if (isSuccessful(response)) {
            return activatePaidCheckout(checkout.getOrderReference(), checkout.getProviderReference());
        }
        if (isFailed(response)) {
            if (INITIATION_PENDING.equals(previousProviderStatus) && isWithinFailureGrace(checkout)) {
                checkout.setProviderStatus(INITIATION_PENDING);
                checkout.setStatus(SmilePayCheckout.CheckoutStatus.PROCESSING);
            } else {
                checkout.setStatus(SmilePayCheckout.CheckoutStatus.FAILED);
                checkout.setNextCheckAt(null);
            }
        } else if (!SmilePayCheckout.CheckoutStatus.AWAITING_OTP.equals(checkout.getStatus())) {
            checkout.setStatus(SmilePayCheckout.CheckoutStatus.PROCESSING);
            checkout.setNextCheckAt(LocalDateTime.now().plusSeconds(15));
        } else {
            checkout.setNextCheckAt(LocalDateTime.now().plusSeconds(15));
        }
        return checkouts.save(checkout);
    }

    @Transactional
    public SmilePayCheckout applyWebhook(Map<String, Object> payload) {
        String orderReference = firstString(payload, "orderReference", "order_ref", "orderNo");
        if (orderReference == null || orderReference.isBlank()) {
            throw new IllegalArgumentException("Missing order reference.");
        }
        SmilePayCheckout checkout = checkout(orderReference);
        checkout.setProviderStatus(firstString(payload, "status", "transactionStatus", "message"));
        checkout.setProviderReference(valueOrCurrent(
                firstString(payload, "transactionReference", "tradeNo", "reference", "id"),
                checkout.getProviderReference()));
        checkout.setRawResponse(toJson(payload));
        checkouts.save(checkout);

        // A callback is only a signal to verify. Account activation always uses
        // the authenticated Smile & Pay status endpoint.
        return verifyAndApply(orderReference);
    }

    @Transactional
    public SmilePayCheckout cancel(String orderReference) {
        SmilePayCheckout checkout = checkout(orderReference);
        if (!SmilePayCheckout.CheckoutStatus.PAID.equals(checkout.getStatus())) {
            checkout.setStatus(SmilePayCheckout.CheckoutStatus.CANCELLED);
            checkout.setNextCheckAt(null);
        }
        return checkouts.save(checkout);
    }

    public String checkoutUrl(SmilePayCheckout checkout) {
        if (SmilePayCheckout.CheckoutPurpose.SIGNUP_ACTIVATION.equals(checkout.getCheckoutPurpose())) {
            return cleanBaseUrl() + "/checkout/smilepay/" + checkout.getAccessToken();
        }
        return cleanBaseUrl() + "/shop/billing/renew/" + checkout.getOrderReference();
    }

    private SmilePayCheckout activatePaidCheckout(String orderReference, String providerReference) {
        SmilePayCheckout checkout = checkouts.findLockedByOrderReference(orderReference)
                .orElseThrow(() -> new IllegalArgumentException("Checkout not found."));
        if (SmilePayCheckout.CheckoutStatus.PAID.equals(checkout.getStatus())) {
            queueInvoiceIfNeeded(checkout, null);
            return checkout;
        }
        Tenant tenant = tenants.findLockedById(checkout.getTenantId()).orElseThrow();
        SaasPlan plan = plans.findById(checkout.getPlanId()).orElseThrow();
        LocalDateTime paidAt = LocalDateTime.now();
        LocalDateTime periodStart = paidAt;
        LocalDateTime previousPeriodEnd = null;
        int billingMonths = checkout.getBillingMonths() == null
                ? cycleMonths(plan)
                : checkout.getBillingMonths();
        TenantSubscription subscription;

        var active = tenantSubscriptions.findByTenantIdAndStatus(
                tenant.getId(), TenantSubscription.SubscriptionStatus.ACTIVE);
        if (active.isPresent() && active.get().getPlanId().equals(plan.getId())) {
            subscription = active.get();
            previousPeriodEnd = subscription.getEndsAt();
            periodStart = subscription.getEndsAt() != null && subscription.getEndsAt().isAfter(paidAt)
                    ? subscription.getEndsAt()
                    : paidAt;
            subscription.setEndsAt(periodStart.plusMonths(billingMonths));
            subscription.setCurrency(checkout.getCurrency());
            subscription.setAmountPaid(checkout.getAmount());
            subscription.setPaymentReference(paymentReference(checkout, providerReference));
            subscription.setNotes("Renewed by Smile & Pay " + checkout.getOrderReference());
            subscription = tenantSubscriptions.save(subscription);
        } else {
            active.ifPresent(existing -> {
                existing.setStatus(TenantSubscription.SubscriptionStatus.EXPIRED);
                existing.setEndsAt(paidAt.minusSeconds(1));
                tenantSubscriptions.save(existing);
            });
            subscription = tenantSubscriptions.save(TenantSubscription.builder()
                    .tenantId(tenant.getId())
                    .planId(plan.getId())
                    .status(TenantSubscription.SubscriptionStatus.ACTIVE)
                    .startsAt(periodStart)
                    .endsAt(periodStart.plusMonths(billingMonths))
                    .currency(checkout.getCurrency())
                    .amountPaid(checkout.getAmount())
                    .paymentReference(paymentReference(checkout, providerReference))
                    .notes("Activated by Smile & Pay " + checkout.getOrderReference())
                    .build());
        }

        tenant.setStatus(Tenant.TenantStatus.ACTIVE);
        tenant.setPlanId(plan.getId());
        tenant.setSubscriptionStart(subscription.getStartsAt());
        tenant.setSubscriptionEnd(subscription.getEndsAt());
        tenants.save(tenant);
        packageModuleAccess.syncAndGetEnabledModules(tenant.getId());

        checkout.setStatus(SmilePayCheckout.CheckoutStatus.PAID);
        checkout.setPaidAt(paidAt);
        checkout.setProviderStatus("PAID");
        checkout.setNextCheckAt(null);
        checkout.setPreviousPeriodEnd(previousPeriodEnd);
        checkout.setNewPeriodEnd(subscription.getEndsAt());
        SmilePayCheckout saved = checkouts.save(checkout);
        if (!subscriptionPayments.existsByCheckoutId(saved.getId())) {
            subscriptionPayments.save(SubscriptionPayment.builder()
                    .checkoutId(saved.getId())
                    .tenantId(saved.getTenantId())
                    .planId(saved.getPlanId())
                    .checkoutPurpose(saved.getCheckoutPurpose())
                    .billingMonths(billingMonths)
                    .unitPrice(saved.getUnitPrice())
                    .totalAmount(saved.getAmount())
                    .currency(saved.getCurrency())
                    .paymentMethod(saved.getPaymentMethod())
                    .orderReference(saved.getOrderReference())
                    .providerReference(paymentReference(saved, providerReference))
                    .previousPeriodEnd(previousPeriodEnd)
                    .newPeriodEnd(subscription.getEndsAt())
                    .confirmedAt(paidAt)
                    .build());
        }
        queueInvoiceIfNeeded(saved, subscription);
        return saved;
    }

    private void queueInvoiceIfNeeded(
            SmilePayCheckout checkout,
            TenantSubscription paidSubscription) {
        if (checkout.getInvoiceSentAt() != null
                || paymentNotifications.existsByCheckoutId(checkout.getId())) {
            return;
        }
        TenantSubscription subscription = paidSubscription != null
                ? paidSubscription
                : tenantSubscriptions.findByTenantIdAndStatus(
                        checkout.getTenantId(),
                        TenantSubscription.SubscriptionStatus.ACTIVE)
                .orElse(null);
        if (subscription != null) {
            paymentNotifications.save(PaymentNotificationOutbox.builder()
                    .checkoutId(checkout.getId())
                    .subscriptionId(subscription.getId())
                    .status(PaymentNotificationOutbox.DeliveryStatus.PENDING)
                    .attempts(0)
                    .nextAttemptAt(LocalDateTime.now())
                    .build());
        }
    }

    private Map<String, Object> commonPayload(
            SmilePayCheckout checkout,
            Tenant tenant,
            SaasPlan plan) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderReference", checkout.getOrderReference());
        payload.put("amount", checkout.getAmount());
        payload.put("resultUrl", cleanBaseUrl() + "/checkout/smilepay/webhook");
        payload.put("returnUrl", paymentReturnUrl(checkout));
        payload.put("cancelUrl", paymentCancelUrl(checkout));
        payload.put("failureUrl", paymentReturnUrl(checkout));
        payload.put("itemName", "RetailZW " + plan.getName());
        payload.put("itemDescription", "RetailZW subscription for " + tenant.getCompanyName());
        payload.put("currencyCode", USD_NUMERIC_CODE);
        return payload;
    }

    private void addCardPayload(Map<String, Object> payload, Tenant tenant, Map<String, String> card) {
        String pan = required(card, "pan", "Enter the card number.").replaceAll("\\s+", "");
        String expMonth = required(card, "expMonth", "Enter the expiry month.");
        String expYear = required(card, "expYear", "Enter the expiry year.");
        String securityCode = required(card, "securityCode", "Enter the card security code.");
        String firstName = required(card, "firstName", "Enter the cardholder first name.");
        String lastName = required(card, "lastName", "Enter the cardholder last name.");

        if (!pan.matches("\\d{13,19}")) {
            throw new IllegalArgumentException("Enter a valid card number.");
        }
        if (!securityCode.matches("\\d{3,4}")) {
            throw new IllegalArgumentException("Enter a valid card security code.");
        }
        payload.put("firstName", firstName);
        payload.put("lastName", lastName);
        payload.put("mobilePhoneNumber", clean(tenant.getPhone()));
        payload.put("email", tenant.getEmail());
        payload.put("paymentMethod", "CARD");
        payload.put("pan", pan);
        payload.put("expMonth", expMonth);
        payload.put("expYear", expYear);
        payload.put("securityCode", securityCode);
    }

    private Map<String, Object> post(String path, Map<String, Object> body) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(cleanProviderUrl(path)))
                    .timeout(Duration.ofSeconds(35))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("x-api-secret", apiSecret)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            return decode(httpClient.send(request, HttpResponse.BodyHandlers.ofString()));
        } catch (ProviderRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ProviderRequestException(
                    0,
                    true,
                    "Smile & Pay request could not be confirmed.",
                    "{\"error\":\"provider_connection_uncertain\"}");
        }
    }

    private Map<String, Object> get(String path) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(cleanProviderUrl(path)))
                    .timeout(Duration.ofSeconds(30))
                    .header("x-api-key", apiKey)
                    .header("x-api-secret", apiSecret)
                    .GET()
                    .build();
            return decode(httpClient.send(request, HttpResponse.BodyHandlers.ofString()));
        } catch (ProviderRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ProviderRequestException(
                    0,
                    true,
                    "Smile & Pay status could not be confirmed.",
                    "{\"error\":\"provider_status_unavailable\"}");
        }
    }

    private Map<String, Object> decode(HttpResponse<String> response) throws Exception {
        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(response.body(), new TypeReference<>() {});
        } catch (Exception ex) {
            payload = new LinkedHashMap<>();
            payload.put("rawMessage", response.body());
        }
        if (response.statusCode() >= 400) {
            String message = firstString(payload, "message", "responseMessage", "error");
            throw new ProviderRequestException(
                    response.statusCode(),
                    response.statusCode() >= 500,
                    "Smile & Pay returned HTTP " + response.statusCode()
                            + (message == null ? "" : ": " + message),
                    toJson(payload));
        }
        Object data = payload.get("data");
        if (data instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, value) -> copy.put(String.valueOf(key), value));
            copy.putIfAbsent("rawSuccess", payload.get("success"));
            copy.putIfAbsent("rawMessage", payload.get("message"));
            return copy;
        }
        return payload;
    }

    private SmilePayCheckout checkout(String reference) {
        return checkouts.findByOrderReference(reference)
                .orElseThrow(() -> new IllegalArgumentException("Checkout not found."));
    }

    private void ensurePayable(SmilePayCheckout checkout) {
        if (SmilePayCheckout.CheckoutStatus.PAID.equals(checkout.getStatus())) {
            throw new IllegalStateException("This subscription has already been paid.");
        }
        if (SmilePayCheckout.CheckoutStatus.CANCELLED.equals(checkout.getStatus())) {
            throw new IllegalStateException("This checkout was cancelled. Start a new payment.");
        }
        if (checkout.getExpiresAt() != null && checkout.getExpiresAt().isBefore(LocalDateTime.now())) {
            checkout.setStatus(SmilePayCheckout.CheckoutStatus.CANCELLED);
            checkouts.save(checkout);
            throw new IllegalStateException("This checkout link has expired. Start a new payment.");
        }
    }

    @Transactional(readOnly = true)
    public SmilePayCheckout checkoutState(String orderReference) {
        return checkout(orderReference);
    }

    private SaasPlan activePlan(Long planId) {
        return plans.findById(planId)
                .filter(candidate -> Boolean.TRUE.equals(candidate.getIsActive()))
                .orElseThrow(() -> new IllegalArgumentException("Choose an active package before checkout."));
    }

    private int cycleMonths(SaasPlan plan) {
        if (SaasPlan.BillingCycle.ANNUALLY.equals(plan.getBillingCycle())) {
            return 12;
        }
        if (SaasPlan.BillingCycle.QUARTERLY.equals(plan.getBillingCycle())) {
            return 3;
        }
        return 1;
    }

    private void validateRenewalMonths(SaasPlan plan, int billingMonths) {
        if (!allowedRenewalMonths(plan).contains(billingMonths)) {
            throw new IllegalArgumentException(
                    "Choose a supported renewal period for this package.");
        }
    }

    private void requirePurpose(
            SmilePayCheckout checkout,
            SmilePayCheckout.CheckoutPurpose expected) {
        if (!expected.equals(checkout.getCheckoutPurpose())) {
            throw new IllegalArgumentException("This checkout cannot be opened from this payment flow.");
        }
    }

    private void requireTenant(SmilePayCheckout checkout, Long tenantId) {
        if (tenantId == null || !tenantId.equals(checkout.getTenantId())) {
            throw new IllegalArgumentException("Checkout not found.");
        }
    }

    private String paymentReturnUrl(SmilePayCheckout checkout) {
        if (SmilePayCheckout.CheckoutPurpose.SIGNUP_ACTIVATION.equals(checkout.getCheckoutPurpose())) {
            return cleanBaseUrl() + "/checkout/smilepay/return?orderReference="
                    + checkout.getOrderReference();
        }
        return cleanBaseUrl() + "/shop/billing/renew/return?orderReference="
                + checkout.getOrderReference();
    }

    private String paymentCancelUrl(SmilePayCheckout checkout) {
        if (SmilePayCheckout.CheckoutPurpose.SIGNUP_ACTIVATION.equals(checkout.getCheckoutPurpose())) {
            return cleanBaseUrl() + "/checkout/smilepay/cancel?orderReference="
                    + checkout.getOrderReference();
        }
        return cleanBaseUrl() + "/shop/billing/renew/cancel?orderReference="
                + checkout.getOrderReference();
    }

    private String paymentReference(SmilePayCheckout checkout, String providerReference) {
        return clean(providerReference) == null ? checkout.getOrderReference() : providerReference;
    }

    private String cleanProviderUrl(String path) {
        String root = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return root + (path.startsWith("/") ? path : "/" + path);
    }

    private String cleanBaseUrl() {
        return appBaseUrl.endsWith("/") ? appBaseUrl.substring(0, appBaseUrl.length() - 1) : appBaseUrl;
    }

    private void requireConfigured() {
        if (clean(apiKey) == null || clean(apiSecret) == null) {
            throw new IllegalStateException("Smile & Pay credentials are not configured.");
        }
    }

    private String newOrderReference(Long tenantId) {
        String suffix = UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toUpperCase(Locale.ROOT);
        return "RZW" + tenantId + System.currentTimeMillis() + suffix;
    }

    private String firstString(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object direct = payload.get(key);
            if (direct != null) return String.valueOf(direct);
            for (Object value : payload.values()) {
                if (value instanceof Map<?, ?> nested) {
                    Object nestedValue = nested.get(key);
                    if (nestedValue != null) return String.valueOf(nestedValue);
                }
            }
        }
        return null;
    }

    private boolean isSuccessful(Map<String, Object> payload) {
        String status = firstString(payload, "status", "transactionStatus", "paymentStatus", "rawMessage");
        if (status == null) return Boolean.TRUE.equals(payload.get("success"));
        String normalized = status.toUpperCase(Locale.ROOT);
        return normalized.equals("SUCCESS")
                || normalized.equals("PAID")
                || normalized.equals("APPROVED")
                || normalized.equals("COMPLETED");
    }

    private boolean isFailed(Map<String, Object> payload) {
        String status = firstString(payload, "status", "transactionStatus", "paymentStatus", "rawMessage");
        if (status == null) return false;
        String normalized = status.toUpperCase(Locale.ROOT);
        return normalized.contains("FAILED")
                || normalized.contains("CANCEL")
                || normalized.contains("DECLINED")
                || normalized.contains("EXPIRED");
    }

    private boolean isWithinFailureGrace(SmilePayCheckout checkout) {
        return checkout.getInitiatedAt() != null
                && checkout.getInitiatedAt().plus(PROVIDER_FAILURE_GRACE).isAfter(LocalDateTime.now());
    }

    private String required(Map<String, String> values, String key, String message) {
        String value = clean(values.get(key));
        if (value == null) throw new IllegalArgumentException(message);
        return value;
    }

    private String valueOrCurrent(String value, String current) {
        return clean(value) == null ? current : value;
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BigDecimal safeMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return String.valueOf(payload);
        }
    }

    public record CheckoutView(
            SmilePayCheckout checkout,
            Tenant tenant,
            SaasPlan plan) {
    }

    public record ExpressResult(
            SmilePayCheckout checkout,
            String redirectHtml,
            boolean requiresOtp,
            String message) {
    }

    public record RenewalQuote(
            SaasPlan plan,
            int billingMonths,
            BigDecimal unitPrice,
            BigDecimal total,
            LocalDateTime previousPeriodEnd,
            LocalDateTime newPeriodEnd,
            SmilePayCheckout.CheckoutPurpose purpose) {
    }

    private static final class ProviderRequestException extends IllegalStateException {
        private final int statusCode;
        private final boolean retryable;
        private final String safeResponse;

        private ProviderRequestException(
                int statusCode,
                boolean retryable,
                String message,
                String safeResponse) {
            super(message);
            this.statusCode = statusCode;
            this.retryable = retryable;
            this.safeResponse = safeResponse;
        }

        int statusCode() {
            return statusCode;
        }

        boolean retryable() {
            return retryable;
        }

        String safeResponse() {
            return safeResponse;
        }
    }
}
