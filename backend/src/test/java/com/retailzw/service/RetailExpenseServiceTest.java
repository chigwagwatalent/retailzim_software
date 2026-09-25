package com.retailzw.service;

import com.retailzw.enums.CurrencyCode;
import com.retailzw.model.AuditLog;
import com.retailzw.model.Branch;
import com.retailzw.model.RetailExpense;
import com.retailzw.repository.AuditLogRepository;
import com.retailzw.repository.BranchRepository;
import com.retailzw.repository.RetailExpenseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetailExpenseServiceTest {

    @Mock RetailExpenseRepository expenses;
    @Mock BranchRepository branches;
    @Mock AuditLogRepository audit;

    private RetailExpenseService service;

    @BeforeEach
    void setUp() {
        service = new RetailExpenseService(expenses, branches, audit);
    }

    @Test
    void createsTenantScopedPostedExpenseAndAuditEntry() {
        when(branches.findById(9L)).thenReturn(Optional.of(activeBranch(9L, 3L)));
        saveReturnsExpense();

        RetailExpense created = service.create(3L, 14L, command(9L, "52.40"));

        assertThat(created.getTenantId()).isEqualTo(3L);
        assertThat(created.getBranchId()).isEqualTo(9L);
        assertThat(created.getStatus()).isEqualTo(RetailExpense.Status.POSTED);
        assertThat(created.getAmount()).isEqualByComparingTo("52.40");
        assertThat(created.getExpenseNumber()).startsWith("EXP-9-");
        ArgumentCaptor<AuditLog> auditEntry = ArgumentCaptor.forClass(AuditLog.class);
        verify(audit).save(auditEntry.capture());
        assertThat(auditEntry.getValue().getAction()).isEqualTo("RETAIL_EXPENSE_CREATED");
        assertThat(auditEntry.getValue().getEntityId()).isEqualTo(77L);
    }

    @Test
    void rejectsBranchOwnedByAnotherTenant() {
        when(branches.findById(9L)).thenReturn(Optional.of(activeBranch(9L, 99L)));

        assertThatThrownBy(() -> service.create(3L, 14L, command(9L, "10.00")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("belonging to this shop");
    }

    @Test
    void voidsInsteadOfDeletingAndPreservesReason() {
        RetailExpense existing = RetailExpense.builder()
                .id(77L).tenantId(3L).branchId(9L).expenseNumber("EXP-9-1")
                .description("Electricity").category(RetailExpense.Category.UTILITIES)
                .amount(new BigDecimal("22.00")).currency(CurrencyCode.USD)
                .paymentMethod("CASH").incurredOn(LocalDate.now()).createdBy(14L).updatedBy(14L)
                .status(RetailExpense.Status.POSTED).version(2L).build();
        when(expenses.findByIdAndTenantId(77L, 3L)).thenReturn(Optional.of(existing));
        saveReturnsExpense();

        service.voidExpense(3L, 21L, 77L, 2L, "Duplicate receipt");

        assertThat(existing.getStatus()).isEqualTo(RetailExpense.Status.VOIDED);
        assertThat(existing.getVoidReason()).isEqualTo("Duplicate receipt");
        assertThat(existing.getVoidedBy()).isEqualTo(21L);
        assertThat(existing.getVoidedAt()).isNotNull();
        verify(expenses).save(existing);
    }

    @Test
    void refusesFutureDatedExpenses() {
        when(branches.findById(9L)).thenReturn(Optional.of(activeBranch(9L, 3L)));
        RetailExpenseService.Command future = new RetailExpenseService.Command(
                9L, "Electricity", "Utility Co", RetailExpense.Category.UTILITIES,
                new BigDecimal("10.00"), CurrencyCode.USD, "CASH", null,
                LocalDate.now().plusDays(1), null);

        assertThatThrownBy(() -> service.create(3L, 14L, future))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be in the future");
    }

    @Test
    void rejectsUnsupportedPaymentMethodEvenIfRequestIsTampered() {
        when(branches.findById(9L)).thenReturn(Optional.of(activeBranch(9L, 3L)));
        RetailExpenseService.Command tampered = new RetailExpenseService.Command(
                9L, "Electricity", "Utility Co", RetailExpense.Category.UTILITIES,
                new BigDecimal("10.00"), CurrencyCode.USD, "UNVERIFIED_METHOD", null,
                LocalDate.now(), null);

        assertThatThrownBy(() -> service.create(3L, 14L, tampered))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid payment method");
    }

    private RetailExpenseService.Command command(Long branchId, String amount) {
        return new RetailExpenseService.Command(branchId, "Electricity", "Utility Co",
                RetailExpense.Category.UTILITIES, new BigDecimal(amount), CurrencyCode.USD,
                "cash", "RCPT-1", LocalDate.now(), "Monthly bill");
    }

    private void saveReturnsExpense() {
        when(expenses.save(any(RetailExpense.class))).thenAnswer(invocation -> {
            RetailExpense expense = invocation.getArgument(0);
            if (expense.getId() == null) expense.setId(77L);
            return expense;
        });
    }

    private Branch activeBranch(Long id, Long tenantId) {
        return Branch.builder().id(id).tenantId(tenantId).branchCode("B" + id)
                .name("Main Branch").isActive(true).build();
    }
}
