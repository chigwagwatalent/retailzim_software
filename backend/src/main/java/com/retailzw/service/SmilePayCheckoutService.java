package com.retailzw.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailzw.enums.CurrencyCode;
import com.retailzw.enums.BusinessModule;
import com.retailzw.enums.ModuleAccessStatus;
import com.retailzw.model.SaasPlan;
import com.retailzw.model.SmilePayCheckout;
import com.retailzw.model.Tenant;
import com.retailzw.model.TenantEnabledModule;
import com.retailzw.model.TenantSubscription;
import com.retailzw.repository.SaasPlanRepository;
import com.retailzw.repository.SmilePayCheckoutRepository;
import com.retailzw.repository.TenantEnabledModuleRepository;
import com.retailzw.repository.TenantRepository;
import com.retailzw.repository.TenantSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final TenantEnabledModuleRepository tenantModules;
    private final EmailService emailService;
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
        Tenant tenant = tenants.findById(tenantId).orElseThrow();
        if (tenant.getPlanId() == null) {
            throw new IllegalArgumentException("Choose a package before checkout.");
        }
        return createCheckout(tenantId, tenant.getPlanId());
    }

    @Transactional
    public SmilePayCheckout createCheckout(Long tenantId, Long planId) {
        Tenant tenant = tenants.findById(tenantId).orElseThrow();
        SaasPlan plan = plans.findById(planId)
                .filter(candidate -> Boolean.TRUE.equals(candidate.getIsActive()))
                .orElseThrow(() -> new IllegalArgumentException("Choose an active package before checkout."));
        requireConfigured();

        return checkouts
                .findFirstByTenantIdAndPlanIdAndStatusInOrderByCreatedAtDesc(
                        tenantId, plan.getId(), REUSABLE_STATUSES)
                .filter(existing -> existing.getExpiresAt() == null
                        || existing.getExpiresAt().isAfter(LocalDateTime.now()))
                .orElseGet(() -> checkouts.save(SmilePayCheckout.builder()
                        .tenantId(tenant.getId())
                        .planId(plan.getId())
                        .orderReference(newOrderReference(tenant.getId()))
                        .amount(safeMoney(plan.getPriceUsd()))
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
            sendInvoiceIfNeeded(checkout);
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
            return activatePaidCheckout(checkout, checkout.getProviderReference());
        }
        if (isFailed(response)) {
            if (INITIATION_PENDING.equals(previousProviderStatus) && isWithinFailureGrace(checkout)) {
                checkout.setProviderStatus(INITIATION_PENDING);
                checkout.setStatus(SmilePayCheckout.CheckoutStatus.PROCESSING);
            } else {
                checkout.setStatus(SmilePayCheckout.CheckoutStatus.FAILED);
            }
        } else if (!SmilePayCheckout.CheckoutStatus.AWAITING_OTP.equals(checkout.getStatus())) {
            checkout.setStatus(SmilePayCheckout.CheckoutStatus.PROCESSING);
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
        }
        return checkouts.save(checkout);
    }

    public String checkoutUrl(SmilePayCheckout checkout) {
        return cleanBaseUrl() + "/checkout/smilepay/" + checkout.getOrderReference();
    }

    private SmilePayCheckout activatePaidCheckout(SmilePayCheckout checkout, String paymentReference) {
        if (SmilePayCheckout.CheckoutStatus.PAID.equals(checkout.getStatus())) {
            return checkout;
        }
        Tenant tenant = tenants.findById(checkout.getTenantId()).orElseThrow();
        SaasPlan plan = plans.findById(checkout.getPlanId()).orElseThrow();
        LocalDateTime paidAt = LocalDateTime.now();
        LocalDateTime periodStart = paidAt;
        TenantSubscription subscription;

        var active = tenantSubscriptions.findByTenantIdAndStatus(
                tenant.getId(), TenantSubscription.SubscriptionStatus.ACTIVE);
        if (active.isPresent() && active.get().getPlanId().equals(plan.getId())) {
            subscription = active.get();
            periodStart = subscription.getEndsAt() != null && subscription.getEndsAt().isAfter(paidAt)
                    ? subscription.getEndsAt()
                    : paidAt;
            subscription.setEndsAt(addBillingCycle(periodStart, plan.getBillingCycle()));
            subscription.setCurrency(checkout.getCurrency());
            subscription.setAmountPaid(checkout.getAmount());
            subscription.setPaymentReference(paymentReference(checkout, paymentReference));
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
                    .endsAt(addBillingCycle(periodStart, plan.getBillingCycle()))
                    .currency(checkout.getCurrency())
                    .amountPaid(checkout.getAmount())
                    .paymentReference(paymentReference(checkout, paymentReference))
                    .notes("Activated by Smile & Pay " + checkout.getOrderReference())
                    .build());
        }

        tenant.setStatus(Tenant.TenantStatus.ACTIVE);
        tenant.setPlanId(plan.getId());
        tenant.setSubscriptionStart(subscription.getStartsAt());
        tenant.setSubscriptionEnd(subscription.getEndsAt());
        tenants.save(tenant);
        syncTenantModules(tenant.getId(), plan);

        checkout.setStatus(SmilePayCheckout.CheckoutStatus.PAID);
        checkout.setPaidAt(paidAt);
        checkout.setProviderStatus("PAID");
        SmilePayCheckout saved = checkouts.save(checkout);
        if (emailService.sendPaymentConfirmation(tenant, plan, subscription, checkout)) {
            saved.setInvoiceSentAt(LocalDateTime.now());
            saved = checkouts.save(saved);
        }
        return saved;
    }

    private void sendInvoiceIfNeeded(SmilePayCheckout checkout) {
        if (checkout.getInvoiceSentAt() != null) {
            return;
        }
        Tenant tenant = tenants.findById(checkout.getTenantId()).orElseThrow();
        SaasPlan plan = plans.findById(checkout.getPlanId()).orElseThrow();
        TenantSubscription subscription = tenantSubscriptions
                .findByTenantIdAndStatus(tenant.getId(), TenantSubscription.SubscriptionStatus.ACTIVE)
                .orElse(null);
        if (subscription != null
                && emailService.sendPaymentConfirmation(tenant, plan, subscription, checkout)) {
            checkout.setInvoiceSentAt(LocalDateTime.now());
            checkouts.save(checkout);
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
        payload.put("returnUrl", cleanBaseUrl() + "/checkout/smilepay/return?orderReference="
                + payload.get("orderReference"));
        payload.put("cancelUrl", cleanBaseUrl() + "/checkout/smilepay/cancel?orderReference="
                + payload.get("orderReference"));
        payload.put("failureUrl", cleanBaseUrl() + "/checkout/smilepay/return?orderReference="
                + payload.get("orderReference"));
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

    private LocalDateTime addBillingCycle(LocalDateTime start, SaasPlan.BillingCycle cycle) {
        if (SaasPlan.BillingCycle.ANNUALLY.equals(cycle)) return start.plusYears(1);
        if (SaasPlan.BillingCycle.QUARTERLY.equals(cycle)) return start.plusMonths(3);
        return start.plusMonths(1);
    }

    private void syncTenantModules(Long tenantId, SaasPlan plan) {
        List<BusinessModule> allowed = plan.allowedModuleList().stream()
                .filter(module -> !BusinessModule.RESTAURANT_MODULE.equals(module))
                .toList();
        if (allowed.isEmpty()) {
            allowed = List.of(BusinessModule.SHOP_MODULE);
        }
        for (BusinessModule module : allowed) {
            TenantEnabledModule tenantModule = tenantModules.findByTenantIdAndModule(tenantId, module)
                    .orElseGet(() -> TenantEnabledModule.builder()
                            .tenantId(tenantId)
                            .module(module)
                            .build());
            tenantModule.setStatus(ModuleAccessStatus.ENABLED);
            tenantModules.save(tenantModule);
        }
        for (TenantEnabledModule existing : tenantModules.findByTenantId(tenantId)) {
            if (!allowed.contains(existing.getModule())) {
                existing.setStatus(ModuleAccessStatus.DISABLED);
                tenantModules.save(existing);
            }
        }
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
