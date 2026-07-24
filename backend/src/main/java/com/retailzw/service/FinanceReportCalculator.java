package com.retailzw.service;

import com.retailzw.enums.CurrencyCode;
import com.retailzw.model.Return;
import com.retailzw.model.ReturnItem;
import com.retailzw.model.Sale;
import com.retailzw.model.SaleItem;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Service
public class FinanceReportCalculator {

    public ReportTotals calculate(List<Sale> sales, List<Return> returns, Map<Long, Sale> originalSalesById) {
        List<Sale> recognizedSales = reportableSales(sales);
        List<Return> recognizedReturns = reportableReturns(returns);
        return new ReportTotals(
                calculateCurrency(CurrencyCode.USD, recognizedSales, recognizedReturns, originalSalesById),
                calculateCurrency(CurrencyCode.ZWG, recognizedSales, recognizedReturns, originalSalesById)
        );
    }

    public List<Sale> reportableSales(List<Sale> sales) {
        if (sales == null) return List.of();
        return sales.stream()
                .filter(sale -> sale != null && (Sale.SaleStatus.COMPLETED.equals(sale.getStatus())
                        || Sale.SaleStatus.PARTIAL_REFUND.equals(sale.getStatus())
                        || Sale.SaleStatus.REFUNDED.equals(sale.getStatus())))
                .toList();
    }

    public List<Return> reportableReturns(List<Return> returns) {
        if (returns == null) return List.of();
        return returns.stream()
                .filter(ret -> ret != null && (!Boolean.TRUE.equals(ret.getRequiresApproval())
                        || Boolean.TRUE.equals(ret.getIsApproved())))
                .toList();
    }

    private CurrencyTotals calculateCurrency(CurrencyCode currency, List<Sale> sales, List<Return> returns,
                                              Map<Long, Sale> originalSalesById) {
        List<Sale> currencySales = sales.stream().filter(sale -> currency.equals(sale.getCurrency())).toList();
        List<Return> currencyReturns = returns.stream().filter(ret -> currency.equals(ret.getCurrency())).toList();

        BigDecimal salesAfterDiscounts = currencySales.stream()
                .map(this::saleSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discounts = currencySales.stream()
                .map(this::saleDiscounts)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal grossSales = salesAfterDiscounts.add(discounts);
        BigDecimal refunds = currencyReturns.stream()
                .map(Return::getTotalRefund)
                .map(this::nvl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal salesTax = currencySales.stream()
                .map(Sale::getTaxAmount)
                .map(this::nvl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal grossCogs = currencySales.stream()
                .map(this::saleCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal returnedCogs = currencyReturns.stream()
                .map(ret -> returnedCost(ret, originalSalesById == null ? null : originalSalesById.get(ret.getOriginalSaleId())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal returnedTax = currencyReturns.stream()
                .map(ret -> returnedTax(ret, originalSalesById == null ? null : originalSalesById.get(ret.getOriginalSaleId())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netSales = salesAfterDiscounts.subtract(refunds);
        BigDecimal netCogs = grossCogs.subtract(returnedCogs);
        BigDecimal grossProfit = netSales.subtract(netCogs);
        BigDecimal operatingExpenses = BigDecimal.ZERO;
        BigDecimal netProfit = grossProfit.subtract(operatingExpenses);

        return new CurrencyTotals(
                grossSales,
                discounts,
                refunds,
                netSales,
                grossCogs,
                returnedCogs,
                netCogs,
                grossProfit,
                operatingExpenses,
                netProfit,
                salesTax.subtract(returnedTax),
                percentage(grossProfit, netSales),
                percentage(netProfit, netSales)
        );
    }

    private BigDecimal saleSubtotal(Sale sale) {
        if (sale.getSubtotal() != null) return sale.getSubtotal();
        return items(sale).stream()
                .map(item -> nvl(item.getUnitPrice()).multiply(nvl(item.getQuantity())).subtract(nvl(item.getDiscountAmount())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal saleDiscounts(Sale sale) {
        BigDecimal itemDiscounts = items(sale).stream()
                .map(SaleItem::getDiscountAmount)
                .map(this::nvl)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(nvl(sale.getCouponDiscount()));
        return nvl(sale.getDiscountAmount()).max(itemDiscounts);
    }

    private BigDecimal saleCost(Sale sale) {
        BigDecimal itemCost = items(sale).stream()
                .map(item -> nvl(item.getCostPrice()).multiply(nvl(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal headerCost = nvl(sale.getTotalCost());
        return headerCost.compareTo(BigDecimal.ZERO) != 0 ? headerCost : itemCost;
    }

    private BigDecimal returnedCost(Return ret, Sale originalSale) {
        if (originalSale == null || originalSale.getItems() == null || ret.getItems() == null) {
            return BigDecimal.ZERO;
        }
        return ret.getItems().stream()
                .map(item -> returnedItemCost(item, originalSale.getItems()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal returnedTax(Return ret, Sale originalSale) {
        if (originalSale == null || originalSale.getItems() == null || ret.getItems() == null) {
            return BigDecimal.ZERO;
        }
        return ret.getItems().stream()
                .map(item -> returnedItemTax(item, originalSale.getItems()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal returnedItemCost(ReturnItem returnedItem, List<SaleItem> saleItems) {
        List<SaleItem> matches = matchingItems(returnedItem, saleItems);
        BigDecimal soldQuantity = matches.stream().map(SaleItem::getQuantity).map(this::nvl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (soldQuantity.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        BigDecimal soldCost = matches.stream()
                .map(item -> nvl(item.getCostPrice()).multiply(nvl(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal averageUnitCost = soldCost.divide(soldQuantity, 8, RoundingMode.HALF_UP);
        return averageUnitCost.multiply(nvl(returnedItem.getQuantity()));
    }

    private BigDecimal returnedItemTax(ReturnItem returnedItem, List<SaleItem> saleItems) {
        List<SaleItem> matches = matchingItems(returnedItem, saleItems);
        BigDecimal soldQuantity = matches.stream().map(SaleItem::getQuantity).map(this::nvl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (soldQuantity.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        BigDecimal soldTax = matches.stream().map(SaleItem::getTaxAmount).map(this::nvl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return soldTax.divide(soldQuantity, 8, RoundingMode.HALF_UP)
                .multiply(nvl(returnedItem.getQuantity()));
    }

    private List<SaleItem> matchingItems(ReturnItem returnedItem, List<SaleItem> saleItems) {
        return saleItems.stream()
                .filter(item -> returnedItem.getProductId().equals(item.getProductId()))
                .toList();
    }

    private List<SaleItem> items(Sale sale) {
        return sale.getItems() == null ? List.of() : sale.getItems();
    }

    private BigDecimal percentage(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        return numerator.multiply(BigDecimal.valueOf(100)).divide(denominator, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public record ReportTotals(CurrencyTotals usd, CurrencyTotals zwg) {
    }

    public record CurrencyTotals(BigDecimal grossSales,
                                 BigDecimal discounts,
                                 BigDecimal refunds,
                                 BigDecimal netSales,
                                 BigDecimal grossCogs,
                                 BigDecimal returnedCogs,
                                 BigDecimal netCogs,
                                 BigDecimal grossProfit,
                                 BigDecimal operatingExpenses,
                                 BigDecimal netProfit,
                                 BigDecimal taxCollected,
                                 BigDecimal grossMargin,
                                 BigDecimal netMargin) {
    }
}
