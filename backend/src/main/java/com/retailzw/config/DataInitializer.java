package com.retailzw.config;



import com.retailzw.enums.UserRole;
import com.retailzw.enums.CurrencyCode;
import com.retailzw.enums.BusinessModule;
import com.retailzw.model.*;
import com.retailzw.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class DataInitializer {

    private final PasswordEncoder passwordEncoder;

    @Bean
    CommandLineRunner seedData(RoleRepository roles,
                               SaasPlanRepository plans,
                               SaasAdminRepository saasAdmins,
                               TenantRepository tenants,
                               BranchRepository branches,
                               UserRepository users,
                               UnitOfMeasureRepository uoms,
                               ProductCategoryRepository categories,
                               ProductRepository products,
                               InventoryRepository inventory,
                               CashDrawerRepository drawers) {
        return args -> {
            for (UserRole name : UserRole.values()) {
                roles.findByName(name).orElseGet(() -> roles.save(Role.builder()
                        .name(name)
                        .description(name.name().replace('_', ' '))
                        .isSystemRole(true)
                        .build()));
            }

            if (plans.count() == 0) {
                plans.saveAll(List.of(
                        SaasPlan.builder().name("Starter").code("STARTER").description("1 branch, 5 users, essential POS and stock controls").priceUsd(new BigDecimal("30.00")).priceZwg(new BigDecimal("900.00")).maxBranches(1).maxUsers(5).maxProducts(500).allowedModules(BusinessModule.SHOP_MODULE.name()).features("[\"POS\",\"Inventory\",\"Customers\",\"Reports\"]").build(),
                        SaasPlan.builder().name("Growth").code("GROWTH").description("For growing Zimbabwean retailers with multiple branches").priceUsd(new BigDecimal("60.00")).priceZwg(new BigDecimal("1800.00")).maxBranches(5).maxUsers(30).maxProducts(5000).maxGasTanks(5).allowedModules(BusinessModule.SHOP_MODULE.name() + "," + BusinessModule.GAS_MODULE.name()).allowMixedModules(true).gasReconciliationEnabled(true).features("[\"All Starter\",\"Purchasing\",\"Transfers\",\"HR\",\"Promotions\",\"Gas Module\"]").build(),
                        SaasPlan.builder().name("Enterprise").code("ENTERPRISE").description("High-volume retail groups with advanced controls").priceUsd(new BigDecimal("150.00")).priceZwg(new BigDecimal("4500.00")).maxBranches(100).maxUsers(1000).maxProducts(100000).maxGasTanks(50).allowedModules(BusinessModule.SHOP_MODULE.name() + "," + BusinessModule.GAS_MODULE.name()).allowMixedModules(true).gasReconciliationEnabled(true).features("[\"All Growth\",\"Audit\",\"API\",\"Priority Support\",\"Mixed Modules\"]").build()
                ));
            }

            saasAdmins.findByUsername("platform").orElseGet(() -> saasAdmins.save(SaasAdmin.builder()
                    .username("platform")
                    .email("platform@retailzw.co.zw")
                    .passwordHash(passwordEncoder.encode("Admin@123"))
                    .firstName("Platform")
                    .lastName("Admin")
                    .isActive(true)
                    .build()));

            Tenant tenant = tenants.findByTenantCode("DEMO").orElseGet(() -> tenants.save(Tenant.builder()
                    .tenantCode("DEMO")
                    .companyName("Harare Demo Retail")
                    .email("admin@demo.co.zw")
                    .phone("+263771234567")
                    .address("Samora Machel Avenue")
                    .city("Harare")
                    .country("Zimbabwe")
                    .status(Tenant.TenantStatus.ACTIVE)
                    .planId(plans.findByCode("GROWTH").map(SaasPlan::getId).orElse(null))
                    .subscriptionStart(LocalDateTime.now())
                    .subscriptionEnd(LocalDateTime.now().plusMonths(1))
                    .trialEnd(LocalDateTime.now().plusDays(14))
                    .defaultCurrency(CurrencyCode.USD)
                    .secondaryCurrency(CurrencyCode.ZWG)
                    .defaultTaxRate(new BigDecimal("15.00"))
                    .receiptFooter("Thank you for shopping with us.")
                    .build()));

            Branch branch = branches.findByTenantIdAndBranchCode(tenant.getId(), "HRE01").orElseGet(() -> branches.save(Branch.builder()
                    .tenantId(tenant.getId())
                    .branchCode("HRE01")
                    .name("Harare CBD")
                    .city("Harare")
                    .phone("+263242000000")
                    .email("hre01@demo.co.zw")
                    .address("First Street, Harare")
                    .isActive(true)
                    .build()));

            Role adminRole = roles.findByName(UserRole.SUPER_ADMIN).orElseThrow();
            users.findByUsernameAndTenantId("admin", tenant.getId())
                    .or(() -> users.findByEmailAndTenantId("admin@demo.co.zw", tenant.getId()))
                    .orElseGet(() -> users.save(User.builder()
                    .tenantId(tenant.getId())
                    .branchId(null)
                    .role(adminRole)
                    .username("admin")
                    .email("admin@demo.co.zw")
                    .passwordHash(passwordEncoder.encode("Admin@123"))
                    .firstName("Demo")
                    .lastName("Admin")
                    .phone("+263771234567")
                    .forcePasswordChange(false)
                    .isActive(true)
                    .build()));

            UnitOfMeasure each = uoms.findByTenantId(tenant.getId()).stream()
                    .filter(u -> "EA".equalsIgnoreCase(u.getAbbreviation()))
                    .findFirst()
                    .orElseGet(() -> uoms.save(UnitOfMeasure.builder().tenantId(tenant.getId()).name("Each").abbreviation("EA").isDecimal(false).build()));

            ProductCategory grocery = categories.findByTenantIdAndCode(tenant.getId(), "GROC").orElseGet(() -> categories.save(ProductCategory.builder()
                    .tenantId(tenant.getId()).name("Groceries").code("GROC").description("Fast moving Zimbabwe retail lines").sortOrder(1).isActive(true).build()));

            if (products.countByTenantIdAndIsActiveTrue(tenant.getId()) == 0) {
                List<Product> sampleProducts = products.saveAll(List.of(
                        Product.builder().tenantId(tenant.getId()).name("Mazoe Orange Crush 2L").sku("GROC-MAZOE-2L").barcode("263000000001").category(grocery).unitOfMeasure(each).costPriceUsd(new BigDecimal("2.10")).sellingPriceUsd(new BigDecimal("3.50")).costPriceZwg(new BigDecimal("31.50")).sellingPriceZwg(new BigDecimal("52.50")).taxRate(new BigDecimal("15.00")).reorderLevel(new BigDecimal("12")).build(),
                        Product.builder().tenantId(tenant.getId()).name("Cerevita 500g").sku("GROC-CEREVITA-500").barcode("263000000002").category(grocery).unitOfMeasure(each).costPriceUsd(new BigDecimal("2.80")).sellingPriceUsd(new BigDecimal("4.25")).costPriceZwg(new BigDecimal("42.00")).sellingPriceZwg(new BigDecimal("63.75")).taxRate(new BigDecimal("15.00")).reorderLevel(new BigDecimal("10")).build(),
                        Product.builder().tenantId(tenant.getId()).name("Mealie Meal 10kg").sku("GROC-MEALIE-10KG").barcode("263000000003").category(grocery).unitOfMeasure(each).costPriceUsd(new BigDecimal("5.20")).sellingPriceUsd(new BigDecimal("7.00")).costPriceZwg(new BigDecimal("78.00")).sellingPriceZwg(new BigDecimal("105.00")).taxRate(BigDecimal.ZERO).isTaxable(false).reorderLevel(new BigDecimal("8")).build()
                ));
                for (Product product : sampleProducts) {
                    inventory.save(Inventory.builder()
                            .tenantId(tenant.getId())
                            .branchId(branch.getId())
                            .productId(product.getId())
                            .quantityOnHand(new BigDecimal("25"))
                            .averageCostUsd(product.getCostPriceUsd())
                            .averageCostZwg(product.getCostPriceZwg())
                            .build());
                }
            }

            if (drawers.findByTenantIdAndBranchIdAndIsActiveTrue(tenant.getId(), branch.getId()).isEmpty()) {
                drawers.save(CashDrawer.builder().tenantId(tenant.getId()).branchId(branch.getId()).name("Till 1").description("Main front counter").isActive(true).build());
            }
        };
    }
}

