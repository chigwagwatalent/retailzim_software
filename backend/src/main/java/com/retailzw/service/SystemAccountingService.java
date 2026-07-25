package com.retailzw.service;

import com.retailzw.enums.CurrencyCode;
import com.retailzw.model.SubscriptionPayment;
import com.retailzw.model.SystemExpense;
import com.retailzw.repository.SubscriptionPaymentRepository;
import com.retailzw.repository.SystemExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SystemAccountingService {
    private static final DateTimeFormatter MONTH_KEY = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM yyyy");

    private final SubscriptionPaymentRepository payments;
    private final SystemExpenseRepository expenses;

    @Transactional(readOnly = true)
    public AccountingOverview overview(YearMonth month) {
        PeriodBounds monthBounds = bounds(month);
        Map<CurrencyCode, Aggregate> revenue = aggregate(
                payments.summarizeConfirmed(monthBounds.fromDateTime(), monthBounds.toDateTime()));
        Map<CurrencyCode, Aggregate> cost = aggregate(
                expenses.summarizePosted(monthBounds.fromDate(), monthBounds.toDate(), SystemExpense.ExpenseStatus.POSTED));

        EnumMap<CurrencyCode, FinancialSummary> summaries = new EnumMap<>(CurrencyCode.class);
        for (CurrencyCode currency : CurrencyCode.values()) {
            Aggregate gross = revenue.getOrDefault(currency, Aggregate.ZERO);
            Aggregate spent = cost.getOrDefault(currency, Aggregate.ZERO);
            BigDecimal net = gross.amount().subtract(spent.amount());
            summaries.put(currency, new FinancialSummary(
                    currency, money(gross.amount()), money(spent.amount()), money(net),
                    margin(gross.amount(), net), gross.count(), spent.count()));
        }

        YearMonth trendStart = month.minusMonths(11);
        PeriodBounds trendBounds = new PeriodBounds(
                trendStart.atDay(1), month.plusMonths(1).atDay(1));
        Map<String, EnumMap<CurrencyCode, BigDecimal>> revenueTrend = monthlyMap(
                payments.monthlyTotals(trendBounds.fromDateTime(), trendBounds.toDateTime()));
        Map<String, EnumMap<CurrencyCode, BigDecimal>> expenseTrend = monthlyMap(
                expenses.monthlyTotals(trendBounds.fromDate(), trendBounds.toDate(), SystemExpense.ExpenseStatus.POSTED));

        List<MonthlyFinancials> trend = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            YearMonth cursor = trendStart.plusMonths(i);
            String key = cursor.format(MONTH_KEY);
            EnumMap<CurrencyCode, BigDecimal> monthRevenue = revenueTrend.getOrDefault(key, new EnumMap<>(CurrencyCode.class));
            EnumMap<CurrencyCode, BigDecimal> monthExpense = expenseTrend.getOrDefault(key, new EnumMap<>(CurrencyCode.class));
            trend.add(new MonthlyFinancials(
                    key,
                    cursor.format(MONTH_LABEL),
                    financialPoint(monthRevenue, monthExpense, CurrencyCode.USD),
                    financialPoint(monthRevenue, monthExpense, CurrencyCode.ZWG)));
        }

        return new AccountingOverview(month, summaries, trend);
    }

    @Transactional(readOnly = true)
    public Page<SubscriptionPayment> paymentPage(YearMonth month,
                                                  CurrencyCode currency,
                                                  String search,
                                                  Pageable pageable) {
        PeriodBounds period = bounds(month);
        return payments.searchConfirmed(period.fromDateTime(), period.toDateTime(), currency, clean(search), pageable);
    }

    @Transactional(readOnly = true)
    public Page<SystemExpense> expensePage(YearMonth month,
                                           CurrencyCode currency,
                                           SystemExpense.ExpenseCategory category,
                                           SystemExpense.ExpenseStatus status,
                                           String search,
                                           Pageable pageable) {
        PeriodBounds period = bounds(month);
        return expenses.search(period.fromDate(), period.toDate(), currency, category, status, clean(search), pageable);
    }

    @Transactional(readOnly = true)
    public List<CategoryTotal> categoryTotals(YearMonth month, CurrencyCode currency) {
        PeriodBounds period = bounds(month);
        return expenses.categoryTotals(period.fromDate(), period.toDate(), currency, SystemExpense.ExpenseStatus.POSTED)
                .stream()
                .map(row -> new CategoryTotal(
                        (SystemExpense.ExpenseCategory) row[0],
                        money((BigDecimal) row[1])))
                .toList();
    }

    @Transactional
    public SystemExpense createExpense(ExpenseCommand command, String administrator) {
        validate(command);
        String actor = requiredActor(administrator);
        SystemExpense expense = SystemExpense.builder()
                .description(command.description().trim())
                .vendor(clean(command.vendor()))
                .category(command.category())
                .amount(money(command.amount()))
                .currency(command.currency())
                .incurredOn(command.incurredOn())
                .paymentReference(clean(command.paymentReference()))
                .notes(clean(command.notes()))
                .recurring(command.recurring())
                .status(SystemExpense.ExpenseStatus.POSTED)
                .createdBy(actor)
                .updatedBy(actor)
                .build();
        return expenses.save(expense);
    }

    @Transactional
    public SystemExpense updateExpense(Long id, ExpenseCommand command, long expectedVersion, String administrator) {
        validate(command);
        SystemExpense expense = requireExpense(id);
        if (expense.getStatus() == SystemExpense.ExpenseStatus.VOIDED) {
            throw new IllegalArgumentException("Voided expenses cannot be edited.");
        }
        if (expense.getVersion() != expectedVersion) {
            throw new IllegalArgumentException("This expense changed while you were editing it. Refresh and try again.");
        }
        expense.setDescription(command.description().trim());
        expense.setVendor(clean(command.vendor()));
        expense.setCategory(command.category());
        expense.setAmount(money(command.amount()));
        expense.setCurrency(command.currency());
        expense.setIncurredOn(command.incurredOn());
        expense.setPaymentReference(clean(command.paymentReference()));
        expense.setNotes(clean(command.notes()));
        expense.setRecurring(command.recurring());
        expense.setUpdatedBy(requiredActor(administrator));
        return expenses.save(expense);
    }

    @Transactional
    public SystemExpense voidExpense(Long id, long expectedVersion, String reason, String administrator) {
        SystemExpense expense = requireExpense(id);
        if (expense.getStatus() == SystemExpense.ExpenseStatus.VOIDED) {
            return expense;
        }
        if (expense.getVersion() != expectedVersion) {
            throw new IllegalArgumentException("This expense changed while you were reviewing it. Refresh and try again.");
        }
        String cleanReason = clean(reason);
        if (cleanReason == null || cleanReason.length() < 4) {
            throw new IllegalArgumentException("Enter a clear reason for voiding this expense.");
        }
        String actor = requiredActor(administrator);
        expense.setStatus(SystemExpense.ExpenseStatus.VOIDED);
        expense.setVoidReason(cleanReason);
        expense.setVoidedBy(actor);
        expense.setVoidedAt(LocalDateTime.now());
        expense.setUpdatedBy(actor);
        return expenses.save(expense);
    }

    private void validate(ExpenseCommand command) {
        if (command == null || command.description() == null || command.description().trim().length() < 3) {
            throw new IllegalArgumentException("Expense description must contain at least 3 characters.");
        }
        if (command.description().trim().length() > 180) {
            throw new IllegalArgumentException("Expense description cannot exceed 180 characters.");
        }
        if (command.category() == null || command.currency() == null) {
            throw new IllegalArgumentException("Category and currency are required.");
        }
        if (command.amount() == null || command.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Expense amount must be greater than zero.");
        }
        if (command.incurredOn() == null || command.incurredOn().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Expense date is required and cannot be in the future.");
        }
    }

    private SystemExpense requireExpense(Long id) {
        return expenses.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Expense record was not found."));
    }

    private String requiredActor(String administrator) {
        String actor = clean(administrator);
        return actor == null ? "system" : actor;
    }

    private PeriodBounds bounds(YearMonth month) {
        return new PeriodBounds(month.atDay(1), month.plusMonths(1).atDay(1));
    }

    private Map<CurrencyCode, Aggregate> aggregate(List<Object[]> rows) {
        EnumMap<CurrencyCode, Aggregate> result = new EnumMap<>(CurrencyCode.class);
        for (Object[] row : rows) {
            result.put((CurrencyCode) row[0], new Aggregate((BigDecimal) row[1], ((Number) row[2]).longValue()));
        }
        return result;
    }

    private Map<String, EnumMap<CurrencyCode, BigDecimal>> monthlyMap(List<Object[]> rows) {
        Map<String, EnumMap<CurrencyCode, BigDecimal>> result = new HashMap<>();
        for (Object[] row : rows) {
            String key = row[0].toString();
            CurrencyCode currency = (CurrencyCode) row[1];
            result.computeIfAbsent(key, ignored -> new EnumMap<>(CurrencyCode.class))
                    .put(currency, money((BigDecimal) row[2]));
        }
        return result;
    }

    private FinancialPoint financialPoint(Map<CurrencyCode, BigDecimal> revenue,
                                           Map<CurrencyCode, BigDecimal> cost,
                                           CurrencyCode currency) {
        BigDecimal gross = revenue.getOrDefault(currency, BigDecimal.ZERO);
        BigDecimal expenses = cost.getOrDefault(currency, BigDecimal.ZERO);
        return new FinancialPoint(money(gross), money(expenses), money(gross.subtract(expenses)));
    }

    private BigDecimal margin(BigDecimal gross, BigDecimal net) {
        if (gross == null || gross.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(1);
        }
        return net.multiply(BigDecimal.valueOf(100)).divide(gross, 1, RoundingMode.HALF_UP);
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record ExpenseCommand(String description,
                                 String vendor,
                                 SystemExpense.ExpenseCategory category,
                                 BigDecimal amount,
                                 CurrencyCode currency,
                                 LocalDate incurredOn,
                                 String paymentReference,
                                 String notes,
                                 boolean recurring) {
    }

    public record AccountingOverview(YearMonth month,
                                     Map<CurrencyCode, FinancialSummary> summaries,
                                     List<MonthlyFinancials> trend) {
    }

    public record FinancialSummary(CurrencyCode currency,
                                   BigDecimal grossRevenue,
                                   BigDecimal expenses,
                                   BigDecimal netRevenue,
                                   BigDecimal marginPercent,
                                   long paymentCount,
                                   long expenseCount) {
    }

    public record MonthlyFinancials(String key,
                                    String label,
                                    FinancialPoint usd,
                                    FinancialPoint zwg) {
    }

    public record FinancialPoint(BigDecimal grossRevenue, BigDecimal expenses, BigDecimal netRevenue) {
    }

    public record CategoryTotal(SystemExpense.ExpenseCategory category, BigDecimal amount) {
    }

    private record Aggregate(BigDecimal amount, long count) {
        private static final Aggregate ZERO = new Aggregate(BigDecimal.ZERO, 0);
    }

    private record PeriodBounds(LocalDate fromDate, LocalDate toDate) {
        private LocalDateTime fromDateTime() {
            return fromDate.atStartOfDay();
        }

        private LocalDateTime toDateTime() {
            return toDate.atStartOfDay();
        }
    }
}
