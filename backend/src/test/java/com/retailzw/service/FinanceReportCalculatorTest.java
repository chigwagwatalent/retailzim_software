package com.retailzw.service;

import com.retailzw.enums.CurrencyCode;
import com.retailzw.model.Return;
import com.retailzw.model.ReturnItem;
import com.retailzw.model.Sale;
import com.retailzw.model.SaleItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FinanceReportCalculatorTest {

    private final FinanceReportCalculator calculator = new FinanceReportCalculator();

    @Test
    void calculatesRevenueAndProfitWithoutTreatingTaxAsIncome() {
        Sale sale = sale(1L, CurrencyCode.USD, "18.00", "2.70", "8.00", "2.00", "4.00");

        FinanceReportCalculator.CurrencyTotals totals = calculator
                .calculate(List.of(sale), List.of(), Map.of())
                .usd();

        assertAmount(totals.grossSales(), "20.00");
        assertAmount(totals.discounts(), "2.00");
        assertAmount(totals.netSales(), "18.00");
        assertAmount(totals.taxCollected(), "2.70");
        assertAmount(totals.netCogs(), "8.00");
        assertAmount(totals.grossProfit(), "10.00");
        assertAmount(totals.grossMargin(), "55.56");
    }

    @Test
    void approvedPartialReturnReversesRevenueAndOriginalCost() {
        Sale sale = sale(1L, CurrencyCode.USD, "18.00", "2.70", "8.00", "2.00", "4.00");
        Return approvedReturn = returnedSale(1L, CurrencyCode.USD, "9.00", "1.00", true, true);

        FinanceReportCalculator.CurrencyTotals totals = calculator
                .calculate(List.of(sale), List.of(approvedReturn), Map.of(1L, sale))
                .usd();

        assertAmount(totals.refunds(), "9.00");
        assertAmount(totals.netSales(), "9.00");
        assertAmount(totals.returnedCogs(), "4.00");
        assertAmount(totals.netCogs(), "4.00");
        assertAmount(totals.grossProfit(), "5.00");
        assertAmount(totals.taxCollected(), "1.35");
    }

    @Test
    void excludesVoidedSalesAndReturnsAwaitingApproval() {
        Sale completed = sale(1L, CurrencyCode.USD, "18.00", "2.70", "8.00", "2.00", "4.00");
        Sale voided = sale(2L, CurrencyCode.USD, "100.00", "15.00", "40.00", "0.00", "20.00");
        voided.setStatus(Sale.SaleStatus.VOIDED);
        Return pendingReturn = returnedSale(1L, CurrencyCode.USD, "9.00", "1.00", true, false);

        FinanceReportCalculator.CurrencyTotals totals = calculator
                .calculate(List.of(completed, voided), List.of(pendingReturn), Map.of(1L, completed))
                .usd();

        assertAmount(totals.netSales(), "18.00");
        assertAmount(totals.refunds(), "0.00");
        assertAmount(totals.grossProfit(), "10.00");
    }

    @Test
    void keepsRealLossesVisibleInsteadOfClampingThemToZero() {
        Sale loss = sale(1L, CurrencyCode.USD, "5.00", "0.75", "8.00", "0.00", "4.00");

        FinanceReportCalculator.CurrencyTotals totals = calculator
                .calculate(List.of(loss), List.of(), Map.of())
                .usd();

        assertAmount(totals.grossProfit(), "-3.00");
        assertAmount(totals.netProfit(), "-3.00");
        assertAmount(totals.grossMargin(), "-60.00");
    }

    @Test
    void reportsUsdAndZwgIndependently() {
        Sale usd = sale(1L, CurrencyCode.USD, "18.00", "2.70", "8.00", "2.00", "4.00");
        Sale zwg = sale(2L, CurrencyCode.ZWG, "180.00", "27.00", "80.00", "20.00", "40.00");

        FinanceReportCalculator.ReportTotals totals = calculator
                .calculate(List.of(usd, zwg), List.of(), Map.of());

        assertAmount(totals.usd().netSales(), "18.00");
        assertAmount(totals.zwg().netSales(), "180.00");
        assertAmount(totals.usd().grossProfit(), "10.00");
        assertAmount(totals.zwg().grossProfit(), "100.00");
    }

    private Sale sale(Long id, CurrencyCode currency, String subtotal, String tax, String cost,
                      String lineDiscount, String unitCost) {
        Sale sale = Sale.builder()
                .id(id)
                .currency(currency)
                .status(Sale.SaleStatus.COMPLETED)
                .subtotal(amount(subtotal))
                .discountAmount(amount(lineDiscount))
                .taxAmount(amount(tax))
                .grandTotal(amount(subtotal).add(amount(tax)))
                .totalCost(amount(cost))
                .build();
        sale.getItems().add(SaleItem.builder()
                .sale(sale)
                .productId(10L)
                .productName("Test product")
                .quantity(amount("2.00"))
                .unitPrice(amount("10.00"))
                .costPrice(amount(unitCost))
                .discountAmount(amount(lineDiscount))
                .taxAmount(amount(tax))
                .lineTotal(amount(subtotal).add(amount(tax)))
                .build());
        return sale;
    }

    private Return returnedSale(Long saleId, CurrencyCode currency, String refund, String quantity,
                                boolean requiresApproval, boolean approved) {
        Return result = Return.builder()
                .originalSaleId(saleId)
                .currency(currency)
                .totalRefund(amount(refund))
                .requiresApproval(requiresApproval)
                .isApproved(approved)
                .build();
        result.getItems().add(ReturnItem.builder()
                .returnRecord(result)
                .productId(10L)
                .productName("Test product")
                .quantity(amount(quantity))
                .unitPrice(amount("10.00"))
                .refundAmount(amount(refund))
                .build());
        return result;
    }

    private BigDecimal amount(String value) {
        return new BigDecimal(value);
    }

    private void assertAmount(BigDecimal actual, String expected) {
        assertThat(actual).isEqualByComparingTo(amount(expected));
    }
}
