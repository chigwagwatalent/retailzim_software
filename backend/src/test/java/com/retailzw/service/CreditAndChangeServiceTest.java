package com.retailzw.service;

import com.retailzw.enums.CurrencyCode;
import com.retailzw.model.Borrower;
import com.retailzw.model.BorrowerTransaction;
import com.retailzw.model.CashSession;
import com.retailzw.model.HeldChange;
import com.retailzw.model.Sale;
import com.retailzw.model.SalePayment;
import com.retailzw.repository.BorrowerRepository;
import com.retailzw.repository.BorrowerTransactionRepository;
import com.retailzw.repository.CashSessionRepository;
import com.retailzw.repository.HeldChangeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CreditAndChangeServiceTest {

    private BorrowerRepository borrowers;
    private BorrowerTransactionRepository borrowerTransactions;
    private HeldChangeRepository heldChange;
    private CashSessionRepository cashSessions;
    private CreditAndChangeService service;

    @BeforeEach
    public void setUp() {
        borrowers = mock(BorrowerRepository.class);
        borrowerTransactions = mock(BorrowerTransactionRepository.class);
        heldChange = mock(HeldChangeRepository.class);
        cashSessions = mock(CashSessionRepository.class);
        service = new CreditAndChangeService(borrowers, borrowerTransactions, heldChange, cashSessions);
    }

    @Test
    public void creditSaleUpdatesBorrowerAndLinksSale() {
        Borrower borrower = Borrower.builder()
                .id(8L)
                .tenantId(2L)
                .currency(CurrencyCode.USD)
                .creditLimit(new BigDecimal("100.00"))
                .currentBalance(new BigDecimal("25.00"))
                .isActive(true)
                .build();
        CashSession session = CashSession.builder().id(4L).build();
        Sale sale = Sale.builder()
                .id(12L)
                .receiptNumber("BR1-12")
                .currency(CurrencyCode.USD)
                .grandTotal(new BigDecimal("30.00"))
                .build();

        when(borrowers.lockById(2L, 8L)).thenReturn(Optional.of(borrower));
        when(borrowers.save(any(Borrower.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(borrowerTransactions.save(any(BorrowerTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BorrowerTransaction transaction = service.chargeSale(
                2L, 3L, 5L, session, sale, 8L, "offline-sale-12");

        assertThat(borrower.getCurrentBalance()).isEqualByComparingTo("55.00");
        assertThat(sale.getBorrowerId()).isEqualTo(8L);
        assertThat(sale.getSaleType()).isEqualTo(Sale.SaleType.BORROWER_CREDIT);
        assertThat(transaction.getBalanceAfter()).isEqualByComparingTo("55.00");
        assertThat(transaction.getOfflineReference()).isEqualTo("offline-sale-12");
    }

    @Test
    public void creditSaleCannotExceedAvailableLimit() {
        Borrower borrower = Borrower.builder()
                .id(8L)
                .tenantId(2L)
                .currency(CurrencyCode.USD)
                .creditLimit(new BigDecimal("50.00"))
                .currentBalance(new BigDecimal("45.00"))
                .isActive(true)
                .build();
        Sale sale = Sale.builder()
                .currency(CurrencyCode.USD)
                .grandTotal(new BigDecimal("10.00"))
                .build();

        when(borrowers.lockById(2L, 8L)).thenReturn(Optional.of(borrower));

        assertThatThrownBy(() -> service.chargeSale(
                2L, 3L, 5L, CashSession.builder().id(4L).build(), sale, 8L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("available");
        verify(borrowers, never()).save(any());
        verify(borrowerTransactions, never()).save(any());
    }

    @Test
    public void holdingCashChangeAddsItToExpectedDrawerCash() {
        CashSession session = CashSession.builder()
                .id(4L)
                .expectedCashUsd(new BigDecimal("40.00"))
                .build();
        Sale sale = Sale.builder()
                .id(12L)
                .receiptNumber("BR1-12")
                .currency(CurrencyCode.USD)
                .payments(List.of(SalePayment.builder()
                        .paymentMethod(SalePayment.PaymentMethod.CASH)
                        .currency(CurrencyCode.USD)
                        .amount(new BigDecimal("20.00"))
                        .build()))
                .build();

        when(heldChange.save(any(HeldChange.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(cashSessions.save(any(CashSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        HeldChange record = service.holdChange(
                2L, 3L, 5L, session, sale, "Jane Doe", "0772123456",
                new BigDecimal("2.50"), "offline-change-12");

        assertThat(record.getStatus()).isEqualTo(HeldChange.Status.OPEN);
        assertThat(record.getAmount()).isEqualByComparingTo("2.50");
        assertThat(session.getExpectedCashUsd()).isEqualByComparingTo("42.50");
        verify(cashSessions).save(session);
    }
}
