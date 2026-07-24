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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PackageModuleAccessService {

    private final TenantSubscriptionRepository subscriptions;
    private final SaasPlanRepository plans;
    private final TenantEnabledModuleRepository tenantModules;
    private final TenantRepository tenants;
    private final BranchRepository branches;

    @Transactional
    public List<BusinessModule> syncAndGetEnabledModules(Long tenantId) {
        Tenant tenant = tenants.findById(tenantId).orElse(null);
        alignCurrentSubscriptionWithTenantPlan(tenant);
        List<BusinessModule> allowed = subscribedModules(tenantId);
        List<TenantEnabledModule> existing = tenantModules.findByTenantId(tenantId);
        if (allowed.isEmpty()) {
            existing.stream()
                    .filter(module -> ModuleAccessStatus.ENABLED.equals(module.getStatus()))
                    .forEach(module -> updateStatus(module, ModuleAccessStatus.DISABLED));
            return List.of();
        }

        LinkedHashSet<BusinessModule> selected = existing.stream()
                .filter(module -> ModuleAccessStatus.ENABLED.equals(module.getStatus()))
                .map(TenantEnabledModule::getModule)
                .filter(allowed::contains)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        List<BusinessModule> branchModules = branches.findByTenantIdAndIsActiveTrue(tenantId).stream()
                .map(Branch::getModuleType)
                .filter(java.util.Objects::nonNull)
                .filter(allowed::contains)
                .distinct()
                .toList();
        TenantBusinessMode businessMode = tenant == null || tenant.getBusinessMode() == null
                ? TenantBusinessMode.SINGLE_MODULE
                : tenant.getBusinessMode();

        // A package that offers exactly one business module is authoritative.
        // Existing tenants may have been retail tenants before moving to a gas
        // plan, so reconcile their branch context as well as their entitlement.
        if (allowed.size() == 1) {
            BusinessModule onlyModule = allowed.get(0);
            selected.clear();
            selected.add(onlyModule);
            reconcileSingleModuleTenant(tenant, onlyModule);
            branchModules = List.of(onlyModule);
            businessMode = TenantBusinessMode.SINGLE_MODULE;
        }

        // A single-module tenant's branch type is the strongest record of the
        // module selected at signup. This also repairs tenants affected by the
        // old package sync, which enabled every module offered by the plan.
        if (allowed.size() > 1
                && TenantBusinessMode.SINGLE_MODULE.equals(businessMode)
                && branchModules.size() == 1) {
            selected.clear();
            selected.add(branchModules.get(0));
        }
        if (selected.isEmpty()) {
            selected.add(defaultModule(allowed, branchModules));
        }
        if (TenantBusinessMode.SINGLE_MODULE.equals(businessMode) && selected.size() > 1) {
            BusinessModule keep = branchModules.isEmpty() ? selected.iterator().next() : branchModules.get(0);
            selected.clear();
            selected.add(keep);
        }

        for (TenantEnabledModule module : existing) {
            ModuleAccessStatus desired = allowed.contains(module.getModule()) && selected.contains(module.getModule())
                    ? ModuleAccessStatus.ENABLED
                    : ModuleAccessStatus.DISABLED;
            if (!desired.equals(module.getStatus())) {
                updateStatus(module, desired);
            }
        }
        Set<BusinessModule> recorded = existing.stream()
                .map(TenantEnabledModule::getModule)
                .collect(java.util.stream.Collectors.toSet());
        selected.stream()
                .filter(module -> !recorded.contains(module))
                .forEach(module -> tenantModules.save(TenantEnabledModule.builder()
                        .tenantId(tenantId)
                        .module(module)
                        .status(ModuleAccessStatus.ENABLED)
                        .build()));

        return allowed.stream().filter(selected::contains).toList();
    }

    @Transactional(readOnly = true)
    public List<BusinessModule> subscribedModules(Long tenantId) {
        TenantSubscription currentSubscription = subscriptions.findByTenantId(tenantId).stream()
                .filter(subscription -> TenantSubscription.SubscriptionStatus.ACTIVE.equals(subscription.getStatus())
                        || TenantSubscription.SubscriptionStatus.TRIAL.equals(subscription.getStatus()))
                .filter(subscription -> subscription.getPlanId() != null)
                .max(Comparator.comparing(TenantSubscription::getEndsAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
        if (currentSubscription == null) {
            return List.of();
        }
        Long selectedPlanId = tenants.findById(tenantId)
                .map(Tenant::getPlanId)
                .orElse(null);
        Long effectivePlanId = selectedPlanId == null ? currentSubscription.getPlanId() : selectedPlanId;
        return plans.findById(effectivePlanId)
                .map(this::allowedModules)
                .orElse(List.of());
    }

    @Transactional
    public void reconcileTenantsOnPlan(Long planId) {
        tenants.findByPlanId(planId).forEach(tenant -> syncAndGetEnabledModules(tenant.getId()));
    }

    public boolean hasModule(Long tenantId, BusinessModule module) {
        return syncAndGetEnabledModules(tenantId).contains(module);
    }

    public boolean hasRetailShop(Long tenantId) {
        return hasModule(tenantId, BusinessModule.SHOP_MODULE);
    }

    public boolean hasGas(Long tenantId) {
        return hasModule(tenantId, BusinessModule.GAS_MODULE);
    }

    public void requireModule(Long tenantId, BusinessModule module) {
        if (!hasModule(tenantId, module)) {
            throw new IllegalStateException(module.getDisplayName() + " is not included in this subscription package.");
        }
    }

    private List<BusinessModule> allowedModules(SaasPlan plan) {
        List<BusinessModule> allowed = plan.allowedModuleList().stream()
                .filter(module -> !BusinessModule.RESTAURANT_MODULE.equals(module))
                .distinct()
                .toList();
        if (allowed.isEmpty()) {
            return List.of(BusinessModule.SHOP_MODULE);
        }
        return allowed;
    }

    private BusinessModule defaultModule(List<BusinessModule> allowed, List<BusinessModule> branchModules) {
        if (!branchModules.isEmpty()) {
            return branchModules.get(0);
        }
        return allowed.contains(BusinessModule.SHOP_MODULE) ? BusinessModule.SHOP_MODULE : allowed.get(0);
    }

    private void updateStatus(TenantEnabledModule module, ModuleAccessStatus status) {
        module.setStatus(status);
        tenantModules.save(module);
    }

    private void reconcileSingleModuleTenant(Tenant tenant, BusinessModule module) {
        if (tenant == null) {
            return;
        }
        if (!TenantBusinessMode.SINGLE_MODULE.equals(tenant.getBusinessMode())) {
            tenant.setBusinessMode(TenantBusinessMode.SINGLE_MODULE);
            tenants.save(tenant);
        }
        List<Branch> changedBranches = branches.findByTenantIdAndIsActiveTrue(tenant.getId()).stream()
                .filter(branch -> !module.equals(branch.getModuleType()))
                .peek(branch -> branch.setModuleType(module))
                .toList();
        if (!changedBranches.isEmpty()) {
            branches.saveAll(changedBranches);
        }
    }

    private void alignCurrentSubscriptionWithTenantPlan(Tenant tenant) {
        if (tenant == null || tenant.getPlanId() == null) {
            return;
        }
        subscriptions.findByTenantId(tenant.getId()).stream()
                .filter(subscription -> TenantSubscription.SubscriptionStatus.ACTIVE.equals(subscription.getStatus())
                        || TenantSubscription.SubscriptionStatus.TRIAL.equals(subscription.getStatus()))
                .max(Comparator.comparing(TenantSubscription::getEndsAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .filter(subscription -> !tenant.getPlanId().equals(subscription.getPlanId()))
                .ifPresent(subscription -> {
                    subscription.setPlanId(tenant.getPlanId());
                    subscriptions.save(subscription);
                });
    }

    public Set<String> retailWebModules() {
        return Set.of(
                "sales", "cash", "change", "returns",
                "products", "categories", "inventory", "inventory-intelligence",
                "customers", "borrowers",
                "suppliers", "purchasing", "reports",
                "audit");
    }
}
