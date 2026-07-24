package com.retailzw.service;

import com.retailzw.enums.BusinessModule;
import com.retailzw.enums.ModuleAccessStatus;
import com.retailzw.enums.TenantBusinessMode;
import com.retailzw.model.Branch;
import com.retailzw.model.SaasPlan;
import com.retailzw.model.Tenant;
import com.retailzw.model.TenantEnabledModule;
import com.retailzw.model.TenantSubscription;
import com.retailzw.repository.BranchRepository;
import com.retailzw.repository.SaasPlanRepository;
import com.retailzw.repository.TenantEnabledModuleRepository;
import com.retailzw.repository.TenantRepository;
import com.retailzw.repository.TenantSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PackageModuleAccessServiceTest {

    private TenantSubscriptionRepository subscriptions;
    private SaasPlanRepository plans;
    private TenantEnabledModuleRepository tenantModules;
    private TenantRepository tenants;
    private BranchRepository branches;
    private PackageModuleAccessService service;

    @BeforeEach
    void setUp() {
        subscriptions = mock(TenantSubscriptionRepository.class);
        plans = mock(SaasPlanRepository.class);
        tenantModules = mock(TenantEnabledModuleRepository.class);
        tenants = mock(TenantRepository.class);
        branches = mock(BranchRepository.class);
        service = new PackageModuleAccessService(subscriptions, plans, tenantModules, tenants, branches);
        when(tenantModules.save(any(TenantEnabledModule.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void singleGasTenantDoesNotInheritRetailFromMixedPackage() {
        Long tenantId = 10L;
        TenantEnabledModule shop = module(tenantId, BusinessModule.SHOP_MODULE, ModuleAccessStatus.ENABLED);
        TenantEnabledModule gas = module(tenantId, BusinessModule.GAS_MODULE, ModuleAccessStatus.ENABLED);
        stubSubscription(tenantId, "SHOP_MODULE,GAS_MODULE");
        when(tenantModules.findByTenantId(tenantId)).thenReturn(new ArrayList<>(List.of(shop, gas)));
        when(tenants.findById(tenantId)).thenReturn(Optional.of(Tenant.builder()
                .id(tenantId).businessMode(TenantBusinessMode.SINGLE_MODULE).build()));
        when(branches.findByTenantIdAndIsActiveTrue(tenantId)).thenReturn(List.of(Branch.builder()
                .tenantId(tenantId).moduleType(BusinessModule.GAS_MODULE).isActive(true).build()));

        List<BusinessModule> enabled = service.syncAndGetEnabledModules(tenantId);

        assertThat(enabled).containsExactly(BusinessModule.GAS_MODULE);
        assertThat(gas.getStatus()).isEqualTo(ModuleAccessStatus.ENABLED);
        assertThat(shop.getStatus()).isEqualTo(ModuleAccessStatus.DISABLED);
    }

    @Test
    void mixedTenantKeepsBothSelectedModules() {
        Long tenantId = 20L;
        TenantEnabledModule shop = module(tenantId, BusinessModule.SHOP_MODULE, ModuleAccessStatus.ENABLED);
        TenantEnabledModule gas = module(tenantId, BusinessModule.GAS_MODULE, ModuleAccessStatus.ENABLED);
        stubSubscription(tenantId, "SHOP_MODULE,GAS_MODULE");
        when(tenantModules.findByTenantId(tenantId)).thenReturn(new ArrayList<>(List.of(shop, gas)));
        when(tenants.findById(tenantId)).thenReturn(Optional.of(Tenant.builder()
                .id(tenantId).businessMode(TenantBusinessMode.MIXED_MODULE).build()));
        when(branches.findByTenantIdAndIsActiveTrue(tenantId)).thenReturn(List.of(
                Branch.builder().tenantId(tenantId).moduleType(BusinessModule.SHOP_MODULE).isActive(true).build(),
                Branch.builder().tenantId(tenantId).moduleType(BusinessModule.GAS_MODULE).isActive(true).build()));

        assertThat(service.syncAndGetEnabledModules(tenantId))
                .containsExactly(BusinessModule.SHOP_MODULE, BusinessModule.GAS_MODULE);
    }

    @Test
    void gasOnlyTenantPlanOverridesStaleGrowthSubscriptionAndShopBranch() {
        Long tenantId = 25L;
        Long oldGrowthPlanId = 125L;
        Long gasPlanId = 225L;
        TenantEnabledModule shop = module(tenantId, BusinessModule.SHOP_MODULE, ModuleAccessStatus.ENABLED);
        List<TenantEnabledModule> recorded = new ArrayList<>(List.of(shop));
        Branch branch = Branch.builder()
                .tenantId(tenantId)
                .moduleType(BusinessModule.SHOP_MODULE)
                .isActive(true)
                .build();
        TenantSubscription staleSubscription = TenantSubscription.builder()
                .tenantId(tenantId)
                .planId(oldGrowthPlanId)
                .status(TenantSubscription.SubscriptionStatus.ACTIVE)
                .endsAt(LocalDateTime.now().plusDays(30))
                .build();

        when(subscriptions.findByTenantId(tenantId)).thenReturn(List.of(staleSubscription));
        when(plans.findById(gasPlanId)).thenReturn(Optional.of(SaasPlan.builder()
                .id(gasPlanId)
                .allowedModules(BusinessModule.GAS_MODULE.name())
                .build()));
        when(tenantModules.findByTenantId(tenantId)).thenReturn(recorded);
        when(tenants.findById(tenantId)).thenReturn(Optional.of(Tenant.builder()
                .id(tenantId)
                .planId(gasPlanId)
                .businessMode(TenantBusinessMode.SINGLE_MODULE)
                .build()));
        when(branches.findByTenantIdAndIsActiveTrue(tenantId)).thenReturn(List.of(branch));
        when(tenantModules.save(any(TenantEnabledModule.class))).thenAnswer(call -> {
            TenantEnabledModule saved = call.getArgument(0);
            if (!recorded.contains(saved)) recorded.add(saved);
            return saved;
        });

        assertThat(service.syncAndGetEnabledModules(tenantId))
                .containsExactly(BusinessModule.GAS_MODULE);
        assertThat(staleSubscription.getPlanId()).isEqualTo(gasPlanId);
        assertThat(branch.getModuleType()).isEqualTo(BusinessModule.GAS_MODULE);
        assertThat(shop.getStatus()).isEqualTo(ModuleAccessStatus.DISABLED);
        assertThat(recorded).anySatisfy(module -> {
            assertThat(module.getModule()).isEqualTo(BusinessModule.GAS_MODULE);
            assertThat(module.getStatus()).isEqualTo(ModuleAccessStatus.ENABLED);
        });
    }

    @Test
    void incompatiblePlanChangeFallsBackToAnAllowedModule() {
        Long tenantId = 30L;
        TenantEnabledModule gas = module(tenantId, BusinessModule.GAS_MODULE, ModuleAccessStatus.ENABLED);
        List<TenantEnabledModule> recorded = new ArrayList<>(List.of(gas));
        stubSubscription(tenantId, "SHOP_MODULE");
        when(tenantModules.findByTenantId(tenantId)).thenReturn(recorded);
        when(tenants.findById(tenantId)).thenReturn(Optional.of(Tenant.builder()
                .id(tenantId).businessMode(TenantBusinessMode.SINGLE_MODULE).build()));
        when(branches.findByTenantIdAndIsActiveTrue(tenantId)).thenReturn(List.of(Branch.builder()
                .tenantId(tenantId).moduleType(BusinessModule.GAS_MODULE).isActive(true).build()));
        when(tenantModules.save(any(TenantEnabledModule.class))).thenAnswer(call -> {
            TenantEnabledModule saved = call.getArgument(0);
            if (!recorded.contains(saved)) recorded.add(saved);
            return saved;
        });

        assertThat(service.syncAndGetEnabledModules(tenantId)).containsExactly(BusinessModule.SHOP_MODULE);
        assertThat(gas.getStatus()).isEqualTo(ModuleAccessStatus.DISABLED);
        assertThat(recorded).anySatisfy(module -> {
            assertThat(module.getModule()).isEqualTo(BusinessModule.SHOP_MODULE);
            assertThat(module.getStatus()).isEqualTo(ModuleAccessStatus.ENABLED);
        });
    }

    private void stubSubscription(Long tenantId, String allowedModules) {
        Long planId = tenantId + 100;
        when(subscriptions.findByTenantId(tenantId)).thenReturn(List.of(TenantSubscription.builder()
                .tenantId(tenantId)
                .planId(planId)
                .status(TenantSubscription.SubscriptionStatus.ACTIVE)
                .startsAt(LocalDateTime.now().minusDays(1))
                .endsAt(LocalDateTime.now().plusDays(30))
                .build()));
        when(plans.findById(planId)).thenReturn(Optional.of(SaasPlan.builder()
                .id(planId)
                .allowedModules(allowedModules)
                .build()));
    }

    private TenantEnabledModule module(Long tenantId, BusinessModule module, ModuleAccessStatus status) {
        return TenantEnabledModule.builder().tenantId(tenantId).module(module).status(status).build();
    }
}
