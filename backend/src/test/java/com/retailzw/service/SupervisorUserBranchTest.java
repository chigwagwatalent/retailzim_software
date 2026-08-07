package com.retailzw.service;

import com.retailzw.dto.request.CreateUserRequest;
import com.retailzw.enums.UserRole;
import com.retailzw.model.Branch;
import com.retailzw.model.Role;
import com.retailzw.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupervisorUserBranchTest {

    private RoleRepository roles;
    private UserRepository users;
    private BranchRepository branches;
    private RetailOperationsService service;

    @BeforeEach
    void setUp() {
        roles = mock(RoleRepository.class);
        users = mock(UserRepository.class);
        branches = mock(BranchRepository.class);
        service = new RetailOperationsService(
                mock(ProductRepository.class),
                mock(ProductCategoryRepository.class),
                mock(UnitOfMeasureRepository.class),
                mock(InventoryRepository.class),
                mock(InventoryTransactionRepository.class),
                mock(InventoryAdjustmentRepository.class),
                branches,
                mock(TenantEnabledModuleRepository.class),
                mock(CustomerRepository.class),
                mock(SupplierRepository.class),
                roles,
                users,
                mock(SaleRepository.class),
                mock(SalePaymentRepository.class),
                mock(CashDrawerRepository.class),
                mock(CashSessionRepository.class),
                mock(PasswordEncoder.class),
                mock(CreditAndChangeService.class),
                mock(CurrencyConversionService.class),
                mock(WholesalePricingService.class));
    }

    @Test
    void supervisorCannotBeCreatedWithoutAnAssignedBranch() {
        Role supervisor = Role.builder().id(4L).name(UserRole.SUPERVISOR).build();
        when(roles.findById(4L)).thenReturn(Optional.of(supervisor));
        when(users.findAllByUsernameForMobileLogin("tendai")).thenReturn(List.of());

        assertThatThrownBy(() -> service.createUser(2L, request(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Supervisor users must be assigned");
        verify(users, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void supervisorCannotBeAttachedToAnotherTenantsBranch() {
        Role supervisor = Role.builder().id(4L).name(UserRole.SUPERVISOR).build();
        when(roles.findById(4L)).thenReturn(Optional.of(supervisor));
        when(users.findAllByUsernameForMobileLogin("tendai")).thenReturn(List.of());
        when(branches.findById(8L)).thenReturn(Optional.of(
                Branch.builder().id(8L).tenantId(55L).isActive(true).build()));

        assertThatThrownBy(() -> service.createUser(2L, request(8L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong");
        verify(users, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private CreateUserRequest request(Long branchId) {
        CreateUserRequest request = new CreateUserRequest();
        request.setFirstName("Tendai");
        request.setLastName("Moyo");
        request.setUsername("tendai");
        request.setEmail("tendai@example.com");
        request.setPassword("Password123!");
        request.setRoleId(4L);
        request.setBranchId(branchId);
        return request;
    }
}
