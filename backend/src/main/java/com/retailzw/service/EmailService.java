package com.retailzw.service;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.retailzw.model.SaasPlan;
import com.retailzw.model.Tenant;
import com.retailzw.model.TenantSubscription;
import com.retailzw.model.User;
import com.retailzw.model.SmilePayCheckout;
import jakarta.mail.internet.MimeMessage;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    @Value("${app.mail.from-name:RetailZW}")
    private String fromName;

    @Value("${app.mail.support-from:support@retailzw.co.zw}")
    private String supportFrom;

    @Value("${app.mail.billing-from:sales@retailzw.co.zw}")
    private String billingFrom;

    @Value("${app.mail.notifications-from:info@retailzw.co.zw}")
    private String notificationsFrom;

    @Value("${app.mail.reply-to:support@retailzw.co.zw}")
    private String replyTo;

    @Value("${app.base-url:https://retailzw.co.zw}")
    private String baseUrl;

    @Value("${spring.mail.host:mail.retailzw.co.zw}")
    private String mailHost;

    @Value("${spring.mail.port:587}")
    private int mailPort;

    @Value("${app.mail.support-password:}")
    private String supportPassword;

    @Value("${app.mail.billing-password:}")
    private String billingPassword;

    @Value("${app.mail.notifications-password:}")
    private String notificationsPassword;

    @PostConstruct
    void validateConfiguration() {
        if (mailHost == null || mailHost.isBlank()) {
            log.error("SMTP is disabled: MAIL_HOST is not configured.");
        } else if (supportPassword.isBlank() || billingPassword.isBlank() || notificationsPassword.isBlank()) {
            log.warn("SMTP host is configured, but mailbox passwords are missing. Set MAIL_SUPPORT_PASSWORD, "
                    + "MAIL_BILLING_PASSWORD and MAIL_NOTIFICATIONS_PASSWORD.");
        } else {
            log.info("SMTP configured host={} port={} support={} billing={} notifications={}",
                    mailHost, mailPort, supportFrom, billingFrom, notificationsFrom);
        }
    }

    public boolean sendSupport(String to, String subject, String html) {
        return send(to, subject, html, supportFrom, supportPassword, replyTo);
    }

    public boolean sendBilling(String to, String subject, String html) {
        return send(to, subject, html, billingFrom, billingPassword, billingFrom);
    }

    public boolean sendNotification(String to, String subject, String html) {
        return send(to, subject, html, notificationsFrom, notificationsPassword, supportFrom);
    }

    public boolean sendPasswordReset(String to, String name, String resetLink) {
        return sendSupport(to, "Reset your RetailZW password", wrap("Password reset",
                "<p>Hello " + esc(name) + ",</p>"
                        + "<p>Use the button below to reset your RetailZW password. This link expires in 30 minutes.</p>"
                        + button(resetLink, "Reset Password")
                        + "<p>If you did not request this, you can ignore this email.</p>"));
    }

    public void sendTenantWelcome(Tenant tenant, User admin, SaasPlan plan) {
        String loginUrl = baseUrl + "/auth/shop/login";
        sendSupport(admin.getEmail(), "Welcome to RetailZW", wrap("Your RetailZW shop is ready",
                "<p>Hello " + esc(admin.getFirstName()) + ",</p>"
                        + "<p>Your shop <strong>" + esc(tenant.getCompanyName()) + "</strong> was created successfully.</p>"
                        + "<p><strong>Tenant code:</strong> " + esc(tenant.getTenantCode()) + "<br>"
                        + "<strong>Package:</strong> " + esc(plan == null ? "Pending package" : plan.getName()) + "</p>"
                        + button(loginUrl, "Open RetailZW")
                        + "<p>Platform approval may still be required before the account becomes fully active.</p>"));
        sendBilling(billingFrom, "New RetailZW tenant signup: " + tenant.getCompanyName(), wrap("New tenant signup",
                "<p><strong>" + esc(tenant.getCompanyName()) + "</strong> signed up.</p>"
                        + "<p>Email: " + esc(tenant.getEmail()) + "<br>Phone: " + esc(tenant.getPhone()) + "</p>"));
    }

    public void sendSubscriptionBilling(Tenant tenant, SaasPlan plan, TenantSubscription subscription) {
        String amount = money(subscription.getAmountPaid()) + " " + subscription.getCurrency();
        String period = format(subscription.getStartsAt()) + " to " + (subscription.getEndsAt() == null ? "open ended" : format(subscription.getEndsAt()));
        sendBilling(tenant.getEmail(), "RetailZW billing update", wrap("Subscription updated",
                "<p>Hello " + esc(tenant.getCompanyName()) + ",</p>"
                        + "<p>Your RetailZW subscription has been updated.</p>"
                        + "<p><strong>Package:</strong> " + esc(plan.getName()) + "<br>"
                        + "<strong>Status:</strong> " + subscription.getStatus() + "<br>"
                        + "<strong>Amount:</strong> " + amount + "<br>"
                        + "<strong>Billing period:</strong> " + period + "</p>"
                        + "<p>Payment reference: " + esc(subscription.getPaymentReference()) + "</p>"));
    }

    public boolean sendPaymentReminder(
            Tenant tenant,
            SaasPlan plan,
            TenantSubscription subscription,
            String checkoutUrl,
            long daysRemaining) {
        String timing = daysRemaining > 1
                ? "is due in " + daysRemaining + " days"
                : daysRemaining == 1
                ? "is due tomorrow"
                : daysRemaining == 0
                ? "is due today"
                : "is " + Math.abs(daysRemaining) + " day" + (Math.abs(daysRemaining) == 1 ? "" : "s") + " overdue";
        String amount = money(plan.getPriceUsd()) + " USD";
        return sendBilling(tenant.getEmail(), "RetailZW subscription payment reminder",
                wrap("Subscription payment reminder",
                        "<p>Hello " + esc(tenant.getCompanyName()) + ",</p>"
                                + "<p>Your <strong>" + esc(plan.getName()) + "</strong> subscription " + timing + ".</p>"
                                + "<p><strong>Amount:</strong> " + amount + "<br>"
                                + "<strong>Current access ends:</strong> " + format(subscription.getEndsAt()) + "</p>"
                                + button(checkoutUrl, "Pay securely with Smile & Pay")
                                + "<p>You can use EcoCash, InnBucks, SmileCash, Omari, OneMoney, Visa or Mastercard.</p>"));
    }

    public boolean sendPaymentConfirmation(
            Tenant tenant,
            SaasPlan plan,
            TenantSubscription subscription,
            SmilePayCheckout checkout) {
        String method = checkout.getPaymentMethod() == null
                ? "Smile & Pay"
                : checkout.getPaymentMethod().getLabel();
        String invoiceNumber = "INV-" + checkout.getOrderReference();
        String html = wrap("Payment confirmed",
                        "<p>Hello " + esc(tenant.getCompanyName()) + ",</p>"
                                + "<p>Your payment was successful and your RetailZW access is active.</p>"
                                + "<p><strong>Invoice:</strong> " + esc(invoiceNumber) + "</p>"
                                + "<p><strong>Package:</strong> " + esc(plan.getName()) + "<br>"
                                + "<strong>Amount:</strong> " + money(checkout.getAmount()) + " " + checkout.getCurrency() + "<br>"
                                + "<strong>Payment method:</strong> " + esc(method) + "<br>"
                                + "<strong>Reference:</strong> " + esc(subscription.getPaymentReference()) + "<br>"
                                + "<strong>Access until:</strong> " + format(subscription.getEndsAt()) + "</p>"
                                + "<p>Your paid invoice is attached to this email.</p>"
                                + button(baseUrl + "/auth/shop/login", "Open RetailZW"));
        byte[] invoice = buildInvoicePdf(tenant, plan, subscription, checkout, invoiceNumber, method);
        return send(
                tenant.getEmail(),
                "RetailZW payment confirmed - " + invoiceNumber,
                html,
                billingFrom,
                billingPassword,
                billingFrom,
                "RetailZW-" + invoiceNumber + ".pdf",
                invoice);
    }

    public void sendTenantAnnouncement(Tenant tenant, String subject, String message) {
        sendNotification(tenant.getEmail(), subject, wrap(subject,
                "<p>Hello " + esc(tenant.getCompanyName()) + ",</p><p>" + esc(message).replace("\n", "<br>") + "</p>"));
    }

    public void sendUserNotification(User user, String title, String message) {
        sendNotification(user.getEmail(), title, wrap(title,
                "<p>Hello " + esc(user.getFirstName()) + ",</p><p>" + esc(message).replace("\n", "<br>") + "</p>"));
    }

    private boolean send(String to, String subject, String html, String from, String password, String reply) {
        return send(to, subject, html, from, password, reply, null, null);
    }

    private boolean send(
            String to,
            String subject,
            String html,
            String from,
            String password,
            String reply,
            String attachmentName,
            byte[] attachment) {
        if (to == null || to.isBlank()) return false;
        if (password == null || password.isBlank()) {
            log.error("Email not sent: SMTP password is missing for mailbox={}", from);
            return false;
        }
        try {
            JavaMailSenderImpl sender = senderFor(from, password);
            MimeMessage mime = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    mime,
                    attachment != null,
                    StandardCharsets.UTF_8.name());
            helper.setTo(to.trim());
            helper.setFrom(from, fromName);
            helper.setReplyTo(reply);
            helper.setSubject(subject);
            helper.setText(html, true);
            if (attachment != null && attachmentName != null) {
                helper.addAttachment(attachmentName, new ByteArrayResource(attachment));
            }
            sender.send(mime);
            log.info("Email sent from={} to={} subject={}", from, to.trim(), subject);
            return true;
        } catch (Exception ex) {
            Throwable root = ex;
            while (root.getCause() != null) root = root.getCause();
            log.warn("Email failed from={} to={} subject={} cause={}", from, to, subject, root.getMessage());
            return false;
        }
    }

    byte[] buildInvoicePdf(
            Tenant tenant,
            SaasPlan plan,
            TenantSubscription subscription,
            SmilePayCheckout checkout,
            String invoiceNumber,
            String method) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Document document = new Document();
            PdfWriter.getInstance(document, output);
            document.open();

            Font brand = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, new BaseColor(35, 87, 214));
            Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Font strong = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            Font normal = FontFactory.getFont(FontFactory.HELVETICA, 10);

            document.add(new Paragraph("RetailZW", brand));
            Paragraph heading = new Paragraph("PAID SUBSCRIPTION INVOICE", title);
            heading.setSpacingBefore(12);
            heading.setSpacingAfter(14);
            document.add(heading);

            PdfPTable details = new PdfPTable(new float[]{1.2f, 2.8f});
            details.setWidthPercentage(100);
            invoiceRow(details, "Invoice", invoiceNumber, strong, normal);
            invoiceRow(details, "Issued", formatDateTime(checkout.getPaidAt()), strong, normal);
            invoiceRow(details, "Billed to", tenant.getCompanyName(), strong, normal);
            invoiceRow(details, "Email", tenant.getEmail(), strong, normal);
            invoiceRow(details, "Payment method", method, strong, normal);
            invoiceRow(details, "Payment reference", subscription.getPaymentReference(), strong, normal);
            document.add(details);

            PdfPTable charges = new PdfPTable(new float[]{3.2f, 1.2f});
            charges.setWidthPercentage(100);
            charges.setSpacingBefore(18);
            invoiceHeader(charges, "Description", title);
            invoiceHeader(charges, "Amount", title);
            invoiceCell(charges, plan.getName() + " subscription\n"
                    + format(subscription.getStartsAt()) + " to " + format(subscription.getEndsAt()), normal);
            invoiceCell(charges, checkout.getCurrency() + " " + money(checkout.getAmount()), normal);
            invoiceCell(charges, "Total paid", strong);
            invoiceCell(charges, checkout.getCurrency() + " " + money(checkout.getAmount()), strong);
            document.add(charges);

            Paragraph status = new Paragraph("PAYMENT STATUS: PAID", strong);
            status.setSpacingBefore(18);
            document.add(status);
            document.add(new Paragraph("Thank you for using RetailZW.", normal));
            document.close();
            return output.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not generate the payment invoice.", ex);
        }
    }

    private void invoiceRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        invoiceCell(table, label, labelFont);
        invoiceCell(table, value == null ? "" : value, valueFont);
    }

    private void invoiceHeader(PdfPTable table, String value, Font font) {
        PdfPCell cell = new PdfPCell(new Paragraph(value, font));
        cell.setBackgroundColor(new BaseColor(235, 243, 255));
        cell.setPadding(9);
        table.addCell(cell);
    }

    private void invoiceCell(PdfPTable table, String value, Font font) {
        PdfPCell cell = new PdfPCell(new Paragraph(value == null ? "" : value, font));
        cell.setPadding(9);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        table.addCell(cell);
    }

    private JavaMailSenderImpl senderFor(String from, String password) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(mailHost);
        sender.setPort(mailPort);
        sender.setUsername(from);
        sender.setPassword(password);
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.ssl.trust", mailHost);
        props.put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
        props.put("mail.smtp.localhost", "retailzw.co.zw");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "15000");
        props.put("mail.smtp.writetimeout", "15000");
        return sender;
    }

    private String wrap(String title, String body) {
        return "<div style=\"font-family:Arial,sans-serif;background:#f6f9fc;padding:24px;color:#122033\">"
                + "<div style=\"max-width:640px;margin:auto;background:#fff;border:1px solid #dce6f2;border-radius:12px;overflow:hidden\">"
                + "<div style=\"background:#2357d6;color:white;padding:18px 22px;font-size:20px;font-weight:800\">RetailZW</div>"
                + "<div style=\"padding:22px\"><h2 style=\"margin-top:0\">" + esc(title) + "</h2>" + body
                + "<p style=\"margin-top:26px;color:#60708a;font-size:13px\">RetailZW</p>"
                + "</div></div></div>";
    }

    private String button(String url, String label) {
        return "<p><a href=\"" + esc(url) + "\" style=\"display:inline-block;background:#2357d6;color:white;text-decoration:none;padding:12px 18px;border-radius:8px;font-weight:700\">" + esc(label) + "</a></p>";
    }

    private String esc(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String money(BigDecimal amount) {
        return amount == null ? "0.00" : amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private String format(java.time.LocalDateTime dateTime) {
        return dateTime == null ? "" : dateTime.format(DateTimeFormatter.ofPattern("dd MMM yyyy"));
    }

    private String formatDateTime(java.time.LocalDateTime dateTime) {
        return dateTime == null ? "" : dateTime.format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm"));
    }
}
