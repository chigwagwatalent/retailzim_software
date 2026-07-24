package com.retailzw.service;

import com.retailzw.dto.request.TenantSignUpRequest;
import com.retailzw.enums.BusinessModule;
import com.retailzw.enums.UserRole;
import com.retailzw.model.Branch;
import com.retailzw.model.Role;
import com.retailzw.model.SaasPlan;
import com.retailzw.model.Tenant;
import com.retailzw.model.TenantEnabledModule;
import com.retailzw.model.User;
import com.retailzw.repository.BranchRepository;
import com.retailzw.repository.CashDrawerRepository;
import com.retailzw.repository.ProductCategoryRepository;
import com.retailzw.repository.RoleRepository;
import com.retailzw.repository.SaasPlanRepository;
import com.retailzw.repository.TenantEnabledModuleRepository;
import com.retailzw.repository.TenantRepository;
import com.retailzw.repository.UnitOfMeasureRepository;
import com.retailzw.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantProvisioningServiceTest {

    @Test
    void gasOnlySignupCreatesGasWorkspaceWithoutRetailDefaults() {
        TenantRepository tenants = mock(TenantRepository.class);
        BranchRepository branches = mock(BranchRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        UserRepository users = mock(UserRepository.class);
        UnitOfMeasureRepository uoms = mock(UnitOfMeasureRepository.class);
        ProductCategoryRepository categories = mock(ProductCategoryRepository.class);
        CashDrawerRepository drawers = mock(CashDrawerRepository.class);
        SaasPlanRepository plans = mock(SaasPlanRepository.class);
        TenantEnabledModuleRepository tenantModules = mock(TenantEnabledModuleRepository.class);
        EmailService email = mock(EmailService.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);

        when(tenants.existsByEmail(any())).thenReturn(false);
        when(tenants.existsByTenantCode(any())).thenReturn(false);
        when(tenants.save(any(Tenant.class))).thenAnswer(call -> {
            Tenant tenant = call.getArgument(0);
            tenant.setId(77L);
            return tenant;
        });
        when(users.findAllByUsernameForMobileLogin(any())).thenReturn(List.of());
        when(users.save(any(User.class))).thenAnswer(call -> call.getArgument(0));
        when(branches.save(any(Branch.class))).thenAnswer(call -> {
            Branch branch = call.getArgument(0);
            branch.setId(88L);
            return branch;
        });
        when(roles.findByName(UserRole.SUPER_ADMIN)).thenReturn(Optional.of(Role.builder()
                .id(1L).name(UserRole.SUPER_ADMIN).build()));
        when(plans.findById(5L)).thenReturn(Optional.of(SaasPlan.builder()
                .id(5L)
                .name("Gas Growth")
                .allowedModules("SHOP_MODULE,GAS_MODULE")
                .allowMixedModules(true)
                .build()));
        when(encoder.encode(any())).thenReturn("encoded");

        TenantProvisioningService service = new TenantProvisioningService(
                tenants, branches, roles, users, uoms, categories, drawers, plans,
                tenantModules, email, encoder);
        TenantSignUpRequest request = gasRequest();

        Tenant tenant = service.signUp(request);

        ArgumentCaptor<Branch> branchCaptor = ArgumentCaptor.forClass(Branch.class);
        verify(branches).save(branchCaptor.capture());
        assertThat(branchCaptor.getValue().getModuleType()).isEqualTo(BusinessModule.GAS_MODULE);
        assertThat(tenant.getBusinessMode()).isEqualTo(com.retailzw.enums.TenantBusinessMode.SINGLE_MODULE);

        ArgumentCaptor<TenantEnabledModule> moduleCaptor = ArgumentCaptor.forClass(TenantEnabledModule.class);
        verify(tenantModules).save(moduleCaptor.capture());
        assertThat(moduleCaptor.getValue().getModule()).isEqualTo(BusinessModule.GAS_MODULE);
        verify(categories, never()).save(any());
        verify(drawers, never()).save(any());
    }

    private TenantSignUpRequest gasRequest() {
        TenantSignUpRequest request = new TenantSignUpRequest();
        request.setCompanyName("Harare Gas");
        request.setEmail("admin@hararegas.co.zw");
        request.setPhone("+263717170895");
        request.setCity("Harare");
        request.setPlanId(5L);
        request.setModules(List.of(BusinessModule.GAS_MODULE));
        request.setAdminFirstName("Gas");
        request.setAdminLastName("Owner");
        request.setAdminUsername("gasowner");
        request.setAdminPassword("StrongPass123!");
        return request;
    }
}
