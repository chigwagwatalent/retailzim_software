package com.retailzw.service;

import com.retailzw.dto.request.CloseSessionRequest;
import com.retailzw.model.CashSession;
import com.retailzw.repository.BranchRepository;
import com.retailzw.repository.CashDrawerRepository;
import com.retailzw.repository.CashSessionRepository;
import com.retailzw.repository.CustomerRepository;
import com.retailzw.repository.InventoryAdjustmentRepository;
import com.retailzw.repository.InventoryRepository;
import com.retailzw.repository.InventoryTransactionRepository;
import com.retailzw.repository.ProductCategoryRepository;
import com.retailzw.repository.ProductRepository;
import com.retailzw.repository.RoleRepository;
import com.retailzw.repository.SalePaymentRepository;
import com.retailzw.repository.SaleRepository;
import com.retailzw.repository.SupplierRepository;
import com.retailzw.repository.TenantEnabledModuleRepository;
import com.retailzw.repository.UnitOfMeasureRepository;
import com.retailzw.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RetailOperationsServiceShiftTest {

    private CashSessionRepository cashSessions;
    private RetailOperationsService service;

    @BeforeEach
    public void setUp() {
        cashSessions = mock(CashSessionRepository.class);
        service = new RetailOperationsService(
                mock(ProductRepository.class),
                mock(ProductCategoryRepository.class),
                mock(UnitOfMeasureRepository.class),
                mock(InventoryRepository.class),
                mock(InventoryTransactionRepository.class),
                mock(InventoryAdjustmentRepository.class),
                mock(BranchRepository.class),
                mock(TenantEnabledModuleRepository.class),
                mock(CustomerRepository.class),
                mock(SupplierRepository.class),
                mock(RoleRepository.class),
                mock(UserRepository.class),
                mock(SaleRepository.class),
                mock(SalePaymentRepository.class),
                mock(CashDrawerRepository.class),
                cashSessions,
                mock(PasswordEncoder.class),
                mock(CreditAndChangeService.class)
        );
    }

    @Test
    public void closeSessionRequiresTheSignedInCashierToOwnTheShiftId() {
        CloseSessionRequest request = new CloseSessionRequest();
        request.setSessionId(10L);

        CashSession cashierOneShift = CashSession.builder()
                .id(10L)
                .tenantId(1L)
                .branchId(2L)
                .cashierId(100L)
                .status(CashSession.SessionStatus.OPEN)
                .expectedCashUsd(BigDecimal.ZERO)
                .expectedCashZwg(BigDecimal.ZERO)
                .build();

        when(cashSessions.findById(10L)).thenReturn(Optional.of(cashierOneShift));

        assertThatThrownBy(() -> service.closeSession(1L, 2L, 200L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not belong");
    }

    @Test
    public void closeSessionClosesOnlyTheRequestedOpenShift() {
        CloseSessionRequest request = new CloseSessionRequest();
        request.setSessionId(11L);
        request.setActualUsd(new BigDecimal("25.00"));
        request.setActualZwg(BigDecimal.ZERO);

        CashSession cashierTwoShift = CashSession.builder()
                .id(11L)
                .tenantId(1L)
                .branchId(2L)
                .cashierId(200L)
                .status(CashSession.SessionStatus.OPEN)
                .expectedCashUsd(new BigDecimal("20.00"))
                .expectedCashZwg(BigDecimal.ZERO)
                .build();

        when(cashSessions.findById(11L)).thenReturn(Optional.of(cashierTwoShift));
        when(cashSessions.save(any(CashSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CashSession closed = service.closeSession(1L, 2L, 200L, request);

        assertThat(closed.getStatus()).isEqualTo(CashSession.SessionStatus.CLOSED);
        assertThat(closed.getId()).isEqualTo(11L);
        assertThat(closed.getCashierId()).isEqualTo(200L);
        assertThat(closed.getVarianceUsd()).isEqualByComparingTo("5.00");
    }
}
