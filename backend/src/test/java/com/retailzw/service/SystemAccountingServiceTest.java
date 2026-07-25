package com.retailzw.service;

import com.retailzw.enums.CurrencyCode;
import com.retailzw.model.SystemExpense;
import com.retailzw.repository.SubscriptionPaymentRepository;
import com.retailzw.repository.SystemExpenseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemAccountingServiceTest {

    @Mock
    private SubscriptionPaymentRepository payments;
    @Mock
    private SystemExpenseRepository expenses;

    private SystemAccountingService accounting;

    @BeforeEach
    void setUp() {
        accounting = new SystemAccountingService(payments, expenses);
    }

    @Test
    void overviewKeepsCurrenciesSeparateAndUsesConfirmedPaymentTotals() {
        when(payments.summarizeConfirmed(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(
                        new Object[]{CurrencyCode.USD, new BigDecimal("100.00"), 3L},
                        new Object[]{CurrencyCode.ZWG, new BigDecimal("900.00"), 2L}));
        when(expenses.summarizePosted(any(LocalDate.class), any(LocalDate.class),
                eq(SystemExpense.ExpenseStatus.POSTED)))
                .thenReturn(List.of(
                        new Object[]{CurrencyCode.USD, new BigDecimal("35.00"), 2L},
                        new Object[]{CurrencyCode.ZWG, new BigDecimal("120.00"), 1L}));
        when(payments.monthlyTotals(any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(List.of());
        when(expenses.monthlyTotals(any(LocalDate.class), any(LocalDate.class),
                eq(SystemExpense.ExpenseStatus.POSTED))).thenReturn(List.of());

        SystemAccountingService.AccountingOverview result = accounting.overview(YearMonth.of(2026, 7));

        assertThat(result.summaries().get(CurrencyCode.USD).grossRevenue()).isEqualByComparingTo("100.00");
        assertThat(result.summaries().get(CurrencyCode.USD).netRevenue()).isEqualByComparingTo("65.00");
        assertThat(result.summaries().get(CurrencyCode.ZWG).grossRevenue()).isEqualByComparingTo("900.00");
        assertThat(result.summaries().get(CurrencyCode.ZWG).netRevenue()).isEqualByComparingTo("780.00");
        assertThat(result.trend()).hasSize(12);
    }

    @Test
    void voidingRetainsTheExpenseAndAddsAuditEvidence() {
        SystemExpense expense = SystemExpense.builder()
                .id(9L)
                .description("Production hosting")
                .amount(new BigDecimal("20.00"))
                .currency(CurrencyCode.USD)
                .category(SystemExpense.ExpenseCategory.INFRASTRUCTURE)
                .incurredOn(LocalDate.of(2026, 7, 20))
                .status(SystemExpense.ExpenseStatus.POSTED)
                .createdBy("platform")
                .updatedBy("platform")
                .version(4L)
                .build();
        when(expenses.findById(9L)).thenReturn(Optional.of(expense));
        when(expenses.save(any(SystemExpense.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SystemExpense result = accounting.voidExpense(9L, 4L, "Duplicate invoice", "finance-admin");

        assertThat(result.getStatus()).isEqualTo(SystemExpense.ExpenseStatus.VOIDED);
        assertThat(result.getVoidReason()).isEqualTo("Duplicate invoice");
        assertThat(result.getVoidedBy()).isEqualTo("finance-admin");
        assertThat(result.getVoidedAt()).isNotNull();
        verify(expenses).save(expense);
    }

    @Test
    void staleExpenseEditIsRejected() {
        SystemExpense expense = SystemExpense.builder()
                .id(9L)
                .status(SystemExpense.ExpenseStatus.POSTED)
                .version(5L)
                .build();
        when(expenses.findById(9L)).thenReturn(Optional.of(expense));
        SystemAccountingService.ExpenseCommand command = new SystemAccountingService.ExpenseCommand(
                "Cloud hosting", "Vendor", SystemExpense.ExpenseCategory.INFRASTRUCTURE,
                new BigDecimal("25.00"), CurrencyCode.USD, LocalDate.now(), null, null, true);

        assertThatThrownBy(() -> accounting.updateExpense(9L, command, 4L, "platform"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("changed");
        verify(expenses, never()).save(any());
    }
}
