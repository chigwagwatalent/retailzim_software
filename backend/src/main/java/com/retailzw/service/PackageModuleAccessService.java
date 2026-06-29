package com.retailzw.service;

import com.retailzw.enums.BusinessModule;
import com.retailzw.enums.ModuleAccessStatus;
import com.retailzw.model.SaasPlan;
import com.retailzw.model.TenantEnabledModule;
import com.retailzw.model.TenantSubscription;
import com.retailzw.repository.SaasPlanRepository;
import com.retailzw.repository.TenantEnabledModuleRepository;
import com.retailzw.repository.TenantSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class PackageModuleAccessService {

    private final TenantSubscriptionRepository subscriptions;
    private final SaasPlanRepository plans;
    private final TenantEnabledModuleRepository tenantModules;

    @Transactional
    public List<BusinessModule> syncAndGetEnabledModules(Long tenantId) {
        List<BusinessModule> allowed = subscribedModules(tenantId);
        for (BusinessModule module : allowed) {
            TenantEnabledModule tenantModule = tenantModules.findByTenantIdAndModule(tenantId, module)
                    .orElseGet(() -> TenantEnabledModule.builder()
                            .tenantId(tenantId)
                            .module(module)
                            .build());
            tenantModule.setStatus(ModuleAccessStatus.ENABLED);
            tenantModules.save(tenantModule);
        }
        for (TenantEnabledModule existing : tenantModules.findByTenantId(tenantId)) {
            if (!allowed.contains(existing.getModule())) {
                existing.setStatus(ModuleAccessStatus.DISABLED);
                tenantModules.save(existing);
            }
        }
        return allowed;
    }

    @Transactional(readOnly = true)
    public List<BusinessModule> subscribedModules(Long tenantId) {
        return subscriptions.findByTenantId(tenantId).stream()
                .filter(subscription -> TenantSubscription.SubscriptionStatus.ACTIVE.equals(subscription.getStatus())
                        || TenantSubscription.SubscriptionStatus.TRIAL.equals(subscription.getStatus()))
                .filter(subscription -> subscription.getPlanId() != null)
                .max(Comparator.comparing(TenantSubscription::getEndsAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .flatMap(subscription -> plans.findById(subscription.getPlanId()))
                .map(this::allowedModules)
                .orElse(List.of());
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

    public Set<String> retailWebModules() {
        return Set.of(
                "sales", "cash", "change", "returns",
                "products", "categories", "inventory", "inventory-intelligence",
                "customers", "borrowers",
                "suppliers", "purchasing", "reports",
                "branches", "company", "notifications", "audit");
    }
}
