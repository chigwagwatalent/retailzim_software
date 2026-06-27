package com.retailzw.controller.web;

import com.retailzw.model.SmilePayCheckout;
import com.retailzw.service.SmilePayCheckoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class SmilePayCheckoutController {

    private final SmilePayCheckoutService checkoutService;

    @GetMapping("/checkout/smilepay/{orderReference}")
    public String checkout(@PathVariable String orderReference, Model model) {
        var view = checkoutService.checkoutView(orderReference);
        model.addAttribute("checkout", view.checkout());
        model.addAttribute("tenant", view.tenant());
        model.addAttribute("plan", view.plan());
        model.addAttribute("paymentMethods", SmilePayCheckout.PaymentMethod.values());
        return "checkout/smilepay";
    }

    @PostMapping("/checkout/smilepay/{orderReference}/initiate")
    public Object initiate(
            @PathVariable String orderReference,
            @RequestParam SmilePayCheckout.PaymentMethod paymentMethod,
            @RequestParam(required = false) String mobile,
            @RequestParam(required = false) String firstName,
            @RequestParam(required = false) String lastName,
            @RequestParam(required = false) String pan,
            @RequestParam(required = false) String expMonth,
            @RequestParam(required = false) String expYear,
            @RequestParam(required = false) String securityCode,
            RedirectAttributes redirect) {
        try {
            Map<String, String> card = new LinkedHashMap<>();
            card.put("firstName", firstName);
            card.put("lastName", lastName);
            card.put("pan", pan);
            card.put("expMonth", expMonth);
            card.put("expYear", expYear);
            card.put("securityCode", securityCode);
            var result = checkoutService.initiateExpress(orderReference, paymentMethod, mobile, card);
            if (result.redirectHtml() != null && !result.redirectHtml().isBlank()) {
                return ResponseEntity.ok()
                        .contentType(MediaType.TEXT_HTML)
                        .body(result.redirectHtml());
            }
            if (result.requiresOtp()) {
                redirect.addFlashAttribute("message",
                        result.message() == null ? "Enter the OTP sent to your phone." : result.message());
                return "redirect:/checkout/smilepay/" + orderReference + "?otp=true";
            }
            redirect.addFlashAttribute("message",
                    result.message() == null
                            ? "Payment started. Approve it and keep this page open."
                            : result.message());
            return "redirect:/checkout/smilepay/" + orderReference + "?pending=true";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
            return "redirect:/checkout/smilepay/" + orderReference;
        }
    }

    @PostMapping("/checkout/smilepay/{orderReference}/confirm-otp")
    public String confirmOtp(
            @PathVariable String orderReference,
            @RequestParam String otp,
            RedirectAttributes redirect) {
        try {
            var result = checkoutService.confirmOtp(orderReference, otp);
            if (SmilePayCheckout.CheckoutStatus.PAID.equals(result.checkout().getStatus())) {
                redirect.addFlashAttribute("message", "Payment confirmed. Your RetailZW account is active.");
                return "redirect:/auth/shop/login";
            }
            redirect.addFlashAttribute("message", "OTP accepted. We are confirming the payment.");
            return "redirect:/checkout/smilepay/" + orderReference + "?pending=true";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
            return "redirect:/checkout/smilepay/" + orderReference + "?otp=true";
        }
    }

    @GetMapping("/checkout/smilepay/{orderReference}/status")
    @ResponseBody
    public Map<String, Object> status(@PathVariable String orderReference) {
        SmilePayCheckout checkout = checkoutService.verifyAndApply(orderReference);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", checkout.getStatus());
        payload.put("providerStatus", checkout.getProviderStatus());
        payload.put("paid", SmilePayCheckout.CheckoutStatus.PAID.equals(checkout.getStatus()));
        payload.put("redirectUrl", SmilePayCheckout.CheckoutStatus.PAID.equals(checkout.getStatus())
                ? "/auth/shop/login?payment=success"
                : null);
        return payload;
    }

    @GetMapping("/checkout/smilepay/{orderReference}/recheck")
    public String recheck(
            @PathVariable String orderReference,
            RedirectAttributes redirect) {
        try {
            SmilePayCheckout checkout = checkoutService.verifyAndApply(orderReference);
            if (SmilePayCheckout.CheckoutStatus.PAID.equals(checkout.getStatus())) {
                redirect.addFlashAttribute("message",
                        "Payment confirmed. Your invoice has been emailed and your account is active.");
                return "redirect:/auth/shop/login";
            }
            redirect.addFlashAttribute("message",
                    SmilePayCheckout.CheckoutStatus.PROCESSING.equals(checkout.getStatus())
                            ? "The payment is still being verified. Keep this page open."
                            : "Smile & Pay has not confirmed this payment. Retry only if no money was deducted.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirect.addFlashAttribute("message",
                    "Payment status is temporarily unavailable. Please check again before retrying.");
        }
        return "redirect:/checkout/smilepay/" + orderReference;
    }

    @GetMapping("/checkout/smilepay/return")
    public String paymentReturn(
            @RequestParam String orderReference,
            RedirectAttributes redirect) {
        try {
            SmilePayCheckout checkout = checkoutService.verifyAndApply(orderReference);
            if (SmilePayCheckout.CheckoutStatus.PAID.equals(checkout.getStatus())) {
                redirect.addFlashAttribute("message",
                        "Payment received. Your RetailZW account is active.");
                return "redirect:/auth/shop/login";
            }
            redirect.addFlashAttribute("message",
                    "Payment is still being confirmed. Keep this page open for a moment.");
            return "redirect:/checkout/smilepay/" + orderReference + "?pending=true";
        } catch (Exception ex) {
            redirect.addFlashAttribute("message", ex.getMessage());
            return "redirect:/checkout/smilepay/" + orderReference;
        }
    }

    @GetMapping("/checkout/smilepay/cancel")
    public String cancel(
            @RequestParam String orderReference,
            RedirectAttributes redirect) {
        checkoutService.cancel(orderReference);
        redirect.addFlashAttribute("message", "Payment cancelled. You can choose another method.");
        return "redirect:/checkout/smilepay/" + orderReference;
    }

    @PostMapping("/checkout/smilepay/webhook")
    @ResponseBody
    public ResponseEntity<String> webhook(@RequestBody Map<String, Object> payload) {
        checkoutService.applyWebhook(payload);
        return ResponseEntity.ok("SUCCESS");
    }
}
