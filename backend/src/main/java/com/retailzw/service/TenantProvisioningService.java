package com.retailzw.service;



import com.retailzw.enums.UserRole;
import com.retailzw.enums.BusinessModule;
import com.retailzw.enums.CurrencyCode;
import com.retailzw.enums.ModuleAccessStatus;
import com.retailzw.enums.TenantBusinessMode;
import com.retailzw.dto.request.TenantSignUpRequest;
import com.retailzw.model.*;
import com.retailzw.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class TenantProvisioningService {

    private final TenantRepository tenants;
    private final BranchRepository branches;
    private final RoleRepository roles;
    private final UserRepository users;
    private final UnitOfMeasureRepository uoms;
    private final ProductCategoryRepository categories;
    private final CashDrawerRepository drawers;
    private final SaasPlanRepository plans;
    private final TenantEnabledModuleRepository tenantModules;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Tenant signUp(TenantSignUpRequest request) {
        if (tenants.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("A shop with this email already exists.");
        }
        if (!users.findAllByUsernameForMobileLogin(request.getAdminUsername()).isEmpty()) {
            throw new IllegalArgumentException("This admin username is already used. Mobile usernames must be unique.");
        }
        String code = uniqueCode(request.getCompanyName());
        SaasPlan plan = request.getPlanId() == null ? null : plans.findById(request.getPlanId()).orElse(null);
        List<BusinessModule> selectedModules = selectedModules(request, plan);
        Tenant tenant = tenants.save(Tenant.builder()
                .tenantCode(code)
                .companyName(request.getCompanyName())
                .registrationNumber(request.getRegistrationNumber())
                .vatNumber(request.getVatNumber())
                .email(request.getEmail())
                .phone(request.getPhone())
                .address(request.getAddress())
                .city(request.getCity())
                .country(request.getCountry() == null ? "Zimbabwe" : request.getCountry())
                .website(request.getWebsite())
                .logoUrl(request.getLogoUrl())
                .planId(request.getPlanId())
                .businessMode(selectedModules.size() > 1 ? TenantBusinessMode.MIXED_MODULE : TenantBusinessMode.SINGLE_MODULE)
                .status(Tenant.TenantStatus.PENDING)
                .trialEnd(LocalDateTime.now().plusDays(14))
                .defaultCurrency(CurrencyCode.USD)
                .secondaryCurrency(CurrencyCode.ZWG)
                .defaultTaxRate(new BigDecimal("15.00"))
                .receiptFooter(request.getReceiptFooter() == null || request.getReceiptFooter().isBlank()
                        ? "Thank you for shopping with us."
                        : request.getReceiptFooter())
                .timezone("Africa/Harare")
                .build());

        Branch branch = branches.save(Branch.builder()
                .tenantId(tenant.getId())
                .branchCode("HO")
                .name("Head Office")
                .moduleType(selectedModules.get(0))
                .city(request.getCity())
                .phone(request.getPhone())
                .email(request.getEmail())
                .address(request.getAddress())
                .isActive(true)
                .build());

        Role role = roles.findByName(UserRole.SUPER_ADMIN).orElseThrow();
        User admin = users.save(User.builder()
                .tenantId(tenant.getId())
                .branchId(null)
                .role(role)
                .username(request.getAdminUsername())
                .email(request.getAdminEmail() == null || request.getAdminEmail().isBlank() ? request.getEmail() : request.getAdminEmail())
                .passwordHash(passwordEncoder.encode(request.getAdminPassword()))
                .firstName(request.getAdminFirstName())
                .lastName(request.getAdminLastName())
                .phone(request.getPhone())
                .employeeNumber("HEAD-OFFICE")
                .forcePasswordChange(false)
                .isActive(true)
                .build());

        uoms.save(UnitOfMeasure.builder().tenantId(tenant.getId()).name("Each").abbreviation("EA").isDecimal(false).build());
        uoms.save(UnitOfMeasure.builder().tenantId(tenant.getId()).name("Kilogram").abbreviation("KG").isDecimal(true).build());
        categories.save(ProductCategory.builder().tenantId(tenant.getId()).name("Groceries").code("GROC").description("Daily retail goods").isActive(true).sortOrder(1).build());
        drawers.save(CashDrawer.builder().tenantId(tenant.getId()).branchId(branch.getId()).name("Till 1").description("Default till").isActive(true).build());
        for (BusinessModule module : selectedModules) {
            tenantModules.save(TenantEnabledModule.builder()
                    .tenantId(tenant.getId())
                    .module(module)
                    .status(ModuleAccessStatus.ENABLED)
                    .build());
        }
        emailService.sendTenantWelcome(tenant, admin, plan);
        return tenant;
    }

    @Transactional
    public Tenant activate(Long tenantId) {
        Tenant tenant = tenants.findById(tenantId).orElseThrow();
        tenant.setStatus(Tenant.TenantStatus.ACTIVE);
        tenant.setSubscriptionStart(LocalDateTime.now());
        tenant.setSubscriptionEnd(LocalDateTime.now().plusMonths(1));
        return tenants.save(tenant);
    }

    @Transactional
    public Tenant suspend(Long tenantId) {
        Tenant tenant = tenants.findById(tenantId).orElseThrow();
        tenant.setStatus(Tenant.TenantStatus.SUSPENDED);
        return tenants.save(tenant);
    }

    private String uniqueCode(String companyName) {
        String base = companyName == null ? "SHOP" : companyName.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (base.length() < 4) base = (base + "SHOP").substring(0, 4);
        base = base.substring(0, Math.min(6, base.length()));
        String code = base;
        int suffix = 1;
        while (tenants.existsByTenantCode(code)) {
            code = base + suffix++;
        }
        return code;
    }

    private List<BusinessModule> selectedModules(TenantSignUpRequest request, SaasPlan plan) {
        List<BusinessModule> requested = request.getModules() == null || request.getModules().isEmpty()
                ? List.of(BusinessModule.SHOP_MODULE)
                : request.getModules().stream().distinct().toList();
        List<BusinessModule> allowed = plan == null ? List.of(BusinessModule.SHOP_MODULE) : plan.allowedModuleList();
        List<BusinessModule> selected = requested.stream().filter(allowed::contains).toList();
        if (selected.isEmpty()) {
            selected = List.of(BusinessModule.SHOP_MODULE);
        }
        if (selected.contains(BusinessModule.RESTAURANT_MODULE)) {
            selected = selected.stream().filter(module -> !BusinessModule.RESTAURANT_MODULE.equals(module)).toList();
        }
        if (selected.isEmpty()) {
            selected = List.of(BusinessModule.SHOP_MODULE);
        }
        return selected;
    }
}

