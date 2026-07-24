package com.retailzw.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CheckoutUiContractTest {

    private static final Path TEMPLATE = Path.of(
            "src/main/resources/templates/checkout/smilepay.html");
    private static final Path STYLES = Path.of(
            "src/main/resources/static/css/billing.css");
    private static final Path CONTROLLER = Path.of(
            "src/main/java/com/retailzw/controller/web/SmilePayCheckoutController.java");

    @Test
    void checkoutKeepsApprovedLayoutAndPaymentFlowHooks() throws IOException {
        String html = Files.readString(TEMPLATE);

        assertThat(html)
                .contains("checkout-progress")
                .contains("payment-method-grid")
                .contains("mobile-number-control")
                .contains("checkout-assurances")
                .contains("checkout-summary")
                .contains("summary-modules")
                .contains("data-express-payment-form")
                .contains("data-payment-pending")
                .contains("confirm-otp")
                .contains("name=\"paymentMethod\"")
                .contains("name=\"mobile\"")
                .contains("name=\"pan\"")
                .contains("name=\"otp\"")
                .contains("/img/payments/");
    }

    @Test
    void checkoutOffersEverySupportedPaymentMethod() throws IOException {
        String controller = Files.readString(CONTROLLER);

        assertThat(controller)
                .contains("PaymentMethod.ECOCASH")
                .contains("PaymentMethod.ONEMONEY")
                .contains("PaymentMethod.OMARI")
                .contains("PaymentMethod.SMILECASH")
                .contains("PaymentMethod.INNBUCKS")
                .contains("PaymentMethod.CARD");
    }

    @Test
    void checkoutStylesRemainResponsiveAndFullWidth() throws IOException {
        String css = Files.readString(STYLES);

        assertThat(css)
                .contains("Checkout v3: approved signup payment experience")
                .contains(".checkout-layout")
                .contains("grid-template-columns: minmax(0, 1.5fr) minmax(350px, .85fr)")
                .contains("@media (max-width: 900px)")
                .contains("@media (max-width: 600px)")
                .contains(".payment-method-grid")
                .contains(".checkout-summary");
    }
}
