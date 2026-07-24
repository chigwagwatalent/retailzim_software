package com.retailzw.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retailzw.enums.CurrencyCode;
import com.retailzw.model.SaasPlan;
import com.retailzw.model.SmilePayCheckout;
import com.retailzw.model.Tenant;
import com.retailzw.model.TenantSubscription;
import com.retailzw.repository.SaasPlanRepository;
import com.retailzw.repository.SmilePayCheckoutRepository;
import com.retailzw.repository.TenantRepository;
import com.retailzw.repository.TenantSubscriptionRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SmilePayCheckoutServiceTest {

    @Test
    public void providerServerErrorIsReconciledInsteadOfShownAsInitiationFailure() throws Exception {
        HttpServer provider = HttpServer.create(new InetSocketAddress(0), 0);
        provider.createContext("/payments/express-checkout/ecocash", exchange -> {
            byte[] response = "{\"message\":\"Internal Server Error\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        provider.start();

        try {
            SmilePayCheckoutRepository checkouts = mock(SmilePayCheckoutRepository.class);
            TenantRepository tenants = mock(TenantRepository.class);
            SaasPlanRepository plans = mock(SaasPlanRepository.class);
            TenantSubscriptionRepository subscriptions = mock(TenantSubscriptionRepository.class);
            PackageModuleAccessService packageModuleAccess = mock(PackageModuleAccessService.class);
            EmailService email = mock(EmailService.class);
            SmilePayCheckoutService service = new SmilePayCheckoutService(
                    checkouts, tenants, plans, subscriptions, packageModuleAccess, email, new ObjectMapper());

            Tenant tenant = Tenant.builder()
                    .id(7L)
                    .companyName("Test Shop")
                    .email("billing@example.com")
                    .phone("0771234567")
                    .planId(1L)
                    .build();
            SaasPlan plan = SaasPlan.builder()
                    .id(1L)
                    .name("Starter")
                    .priceUsd(new BigDecimal("30.00"))
                    .billingCycle(SaasPlan.BillingCycle.MONTHLY)
                    .build();
            SmilePayCheckout checkout = SmilePayCheckout.builder()
                    .id(10L)
                    .tenantId(7L)
                    .planId(1L)
                    .orderReference("RZW-TEST-500")
                    .amount(new BigDecimal("30.00"))
                    .currency(CurrencyCode.USD)
                    .status(SmilePayCheckout.CheckoutStatus.PENDING)
                    .expiresAt(LocalDateTime.now().plusHours(1))
                    .build();

            when(checkouts.findByOrderReference("RZW-TEST-500")).thenReturn(Optional.of(checkout));
            when(checkouts.save(any(SmilePayCheckout.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(tenants.findById(7L)).thenReturn(Optional.of(tenant));
            when(plans.findById(1L)).thenReturn(Optional.of(plan));

            ReflectionTestUtils.setField(service, "baseUrl",
                    "http://localhost:" + provider.getAddress().getPort());
            ReflectionTestUtils.setField(service, "appBaseUrl", "https://retailzw.co.zw");
            ReflectionTestUtils.setField(service, "apiKey", "test-key");
            ReflectionTestUtils.setField(service, "apiSecret", "test-secret");

            SmilePayCheckoutService.ExpressResult result = service.initiateExpress(
                    "RZW-TEST-500",
                    SmilePayCheckout.PaymentMethod.ECOCASH,
                    "0771234567",
                    Map.of());

            assertThat(result.checkout().getStatus())
                    .isEqualTo(SmilePayCheckout.CheckoutStatus.PROCESSING);
            assertThat(result.checkout().getProviderStatus()).isEqualTo("INITIATION_PENDING");
            assertThat(result.message()).contains("verification");
        } finally {
            provider.stop(0);
        }
    }

    @Test
    public void successfulPaymentInvoiceIsGeneratedAsPdf() {
        EmailService email = new EmailService();
        Tenant tenant = Tenant.builder()
                .companyName("Test Shop")
                .email("billing@example.com")
                .build();
        SaasPlan plan = SaasPlan.builder()
                .name("Growth")
                .build();
        TenantSubscription subscription = TenantSubscription.builder()
                .startsAt(LocalDateTime.of(2026, 6, 1, 0, 0))
                .endsAt(LocalDateTime.of(2026, 7, 1, 0, 0))
                .paymentReference("PAY-123")
                .build();
        SmilePayCheckout checkout = SmilePayCheckout.builder()
                .orderReference("RZW-PAID-123")
                .amount(new BigDecimal("60.00"))
                .currency(CurrencyCode.USD)
                .paidAt(LocalDateTime.of(2026, 6, 15, 11, 0))
                .build();

        byte[] pdf = email.buildInvoicePdf(
                tenant, plan, subscription, checkout, "INV-RZW-PAID-123", "EcoCash");

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }
}
