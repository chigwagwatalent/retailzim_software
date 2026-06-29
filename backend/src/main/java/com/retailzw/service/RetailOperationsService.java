package com.retailzw.service;


import com.retailzw.enums.CurrencyCode;
import com.retailzw.dto.request.*;
import com.retailzw.enums.BusinessModule;
import com.retailzw.enums.ModuleAccessStatus;
import com.retailzw.enums.UserRole;
import com.retailzw.model.*;
import com.retailzw.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RetailOperationsService {

    private final ProductRepository products;
    private final ProductCategoryRepository categories;
    private final UnitOfMeasureRepository uoms;
    private final InventoryRepository inventory;
    private final InventoryTransactionRepository inventoryTransactions;
    private final InventoryAdjustmentRepository adjustments;
    private final BranchRepository branches;
    private final TenantEnabledModuleRepository tenantModules;
    private final CustomerRepository customers;
    private final SupplierRepository suppliers;
    private final RoleRepository roles;
    private final UserRepository users;
    private final SaleRepository sales;
    private final SalePaymentRepository salePayments;
    private final CashDrawerRepository drawers;
    private final CashSessionRepository cashSessions;
    private final PasswordEncoder passwordEncoder;
    private final CreditAndChangeService creditAndChange;

    @Transactional
    public Product createProduct(Long tenantId, CreateProductRequest request, Long createdBy) {
        Long targetBranchId = request.getBranchId();
        if (targetBranchId == null) {
            throw new IllegalArgumentException("Select the branch this product belongs to.");
        }
        validateActiveBranch(tenantId, targetBranchId);
        Product product = Product.builder()
                .tenantId(tenantId)
                .name(request.getName())
                .sku(request.getSku())
                .barcode(request.getBarcode())
                .description(request.getDescription())
                .category(request.getCategoryId() == null ? null : categories.findById(request.getCategoryId()).orElse(null))
                .unitOfMeasure(request.getUomId() == null ? null : uoms.findById(request.getUomId()).orElse(null))
                .costPriceUsd(nvl(request.getCostPriceUsd()))
                .sellingPriceUsd(nvl(request.getSellingPriceUsd()))
                .costPriceZwg(nvl(request.getCostPriceZwg()))
                .sellingPriceZwg(nvl(request.getSellingPriceZwg()))
                .taxRate(nvl(request.getTaxRate()))
                .isTaxable(Boolean.TRUE.equals(request.getIsTaxable()))
                .reorderLevel(nvl(request.getReorderLevel()))
                .maxStockLevel(request.getMaxStockLevel())
                .imageUrl(request.getImageUrl())
                .hasVariants(Boolean.TRUE.equals(request.getHasVariants()))
                .isService(Boolean.TRUE.equals(request.getIsService()))
                .createdBy(createdBy)
                .isActive(true)
                .build();
        Product saved = products.save(product);
        assignProductToBranch(tenantId, targetBranchId, saved.getId(), nvl(request.getOpeningStock()));
        log.info("Product created tenant={} branch={} product={} sku={} openingStock={}",
                tenantId, targetBranchId, saved.getId(), saved.getSku(), nvl(request.getOpeningStock()));
        return saved;
    }

    @Transactional
    public Product updateProduct(Long tenantId, Long productId, CreateProductRequest request) {
        if (request.getBranchId() != null) {
            validateActiveBranch(tenantId, request.getBranchId());
        }
        Product product = products.findById(productId)
                .filter(p -> p.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Product not found."));
        product.setName(request.getName());
        product.setSku(request.getSku());
        product.setBarcode(request.getBarcode());
        product.setDescription(request.getDescription());
        product.setCategory(request.getCategoryId() == null ? null : categories.findById(request.getCategoryId()).orElse(null));
        product.setUnitOfMeasure(request.getUomId() == null ? null : uoms.findById(request.getUomId()).orElse(null));
        product.setCostPriceUsd(nvl(request.getCostPriceUsd()));
        product.setSellingPriceUsd(nvl(request.getSellingPriceUsd()));
        product.setCostPriceZwg(nvl(request.getCostPriceZwg()));
        product.setSellingPriceZwg(nvl(request.getSellingPriceZwg()));
        product.setTaxRate(nvl(request.getTaxRate()));
        product.setIsTaxable(Boolean.TRUE.equals(request.getIsTaxable()));
        product.setReorderLevel(nvl(request.getReorderLevel()));
        product.setMaxStockLevel(request.getMaxStockLevel());
        product.setImageUrl(request.getImageUrl());
        product.setHasVariants(Boolean.TRUE.equals(request.getHasVariants()));
        product.setIsService(Boolean.TRUE.equals(request.getIsService()));
        Product saved = products.save(product);
        if (request.getBranchId() != null) {
            assignProductToBranch(tenantId, request.getBranchId(), saved.getId(), BigDecimal.ZERO);
        }
        log.info("Product updated tenant={} product={} branch={} sku={}",
                tenantId, saved.getId(), request.getBranchId(), saved.getSku());
        return saved;
    }

    @Transactional
    public Inventory assignProductToBranch(Long tenantId, Long branchId, Long productId, BigDecimal openingStock) {
        validateActiveBranch(tenantId, branchId);
        Product product = products.findById(productId)
                .filter(p -> p.getTenantId().equals(tenantId))
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .orElseThrow(() -> new IllegalArgumentException("Product is not available for this shop."));
        Inventory assigned = inventory.findByTenantIdAndBranchIdAndProductId(tenantId, branchId, productId)
                .orElseGet(() -> inventory.save(Inventory.builder()
                        .tenantId(tenantId)
                        .branchId(branchId)
                        .productId(product.getId())
                        .quantityOnHand(nvl(openingStock))
                        .averageCostUsd(product.getCostPriceUsd())
                        .averageCostZwg(product.getCostPriceZwg())
                        .build()));
        log.info("Product assigned to branch tenant={} branch={} product={} onHand={}",
                tenantId, branchId, productId, assigned.getQuantityOnHand());
        return assigned;
    }

    @Transactional
    public void removeProductFromBranch(Long tenantId, Long branchId, Long productId) {
        validateActiveBranch(tenantId, branchId);
        Inventory stock = inventory.findByTenantIdAndBranchIdAndProductId(tenantId, branchId, productId)
                .orElseThrow(() -> new IllegalArgumentException("Product is not assigned to this branch."));
        BigDecimal totalHeld = nvl(stock.getQuantityOnHand())
                .add(nvl(stock.getQuantityReserved()))
                .add(nvl(stock.getQuantityOnOrder()));
        if (totalHeld.compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalStateException("Move or adjust this branch stock to zero before removing the product from the branch.");
        }
        inventory.delete(stock);
    }

    @Transactional
    public Product setProductActive(Long tenantId, Long productId, boolean active) {
        Product product = products.findById(productId)
                .filter(p -> p.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Product not found."));
        product.setIsActive(active);
        return products.save(product);
    }

    @Transactional
    public ProductCategory updateCategory(Long tenantId, Long categoryId, ProductCategory request) {
        ProductCategory category = categories.findById(categoryId)
                .filter(c -> c.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Category not found."));
        category.setName(request.getName());
        category.setCode(request.getCode());
        category.setDescription(request.getDescription());
        category.setParentId(request.getParentId());
        category.setImageUrl(request.getImageUrl());
        category.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        return categories.save(category);
    }

    @Transactional
    public ProductCategory setCategoryActive(Long tenantId, Long categoryId, boolean active) {
        ProductCategory category = categories.findById(categoryId)
                .filter(c -> c.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Category not found."));
        category.setIsActive(active);
        return categories.save(category);
    }

    @Transactional
    public Customer createCustomer(Long tenantId, Long branchId, CreateCustomerRequest request, Long userId) {
        return customers.save(Customer.builder()
                .tenantId(tenantId)
                .branchId(branchId)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .address(request.getAddress())
                .dateOfBirth(request.getDateOfBirth())
                .gender(request.getGender())
                .notes(request.getNotes())
                .loyaltyCardNumber("LOY-" + tenantId + "-" + System.currentTimeMillis())
                .registeredBy(userId)
                .isActive(true)
                .build());
    }

    @Transactional
    public Customer updateCustomer(Long tenantId, Long customerId, CreateCustomerRequest request) {
        Customer customer = customers.findById(customerId)
                .filter(c -> c.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Customer not found."));
        customer.setFirstName(request.getFirstName());
        customer.setLastName(request.getLastName());
        customer.setEmail(request.getEmail());
        customer.setPhone(request.getPhone());
        customer.setAddress(request.getAddress());
        customer.setDateOfBirth(request.getDateOfBirth());
        customer.setGender(request.getGender());
        customer.setNotes(request.getNotes());
        return customers.save(customer);
    }

    @Transactional
    public Customer setCustomerActive(Long tenantId, Long customerId, boolean active) {
        Customer customer = customers.findById(customerId)
                .filter(c -> c.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Customer not found."));
        customer.setIsActive(active);
        return customers.save(customer);
    }

    @Transactional
    public User createUser(Long tenantId, CreateUserRequest request) {
        users.findAllByUsernameForMobileLogin(normalize(request.getUsername())).stream().findFirst().ifPresent(existing -> {
            throw new IllegalArgumentException("Username is already used by another shop. Mobile usernames must be unique.");
        });
        Role role = roles.findById(request.getRoleId()).orElseThrow();
        validateUserBranch(tenantId, role, request.getBranchId());
        User saved = users.save(User.builder()
                .tenantId(tenantId)
                .branchId(request.getBranchId())
                .role(role)
                .username(normalize(request.getUsername()))
                .email(normalize(request.getEmail()))
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(clean(request.getFirstName()))
                .lastName(clean(request.getLastName()))
                .phone(clean(request.getPhone()))
                .employeeNumber(clean(request.getEmployeeNumber()))
                .forcePasswordChange(true)
                .isActive(true)
                .build());
        log.info("User created tenant={} user={} username={} role={} branch={}",
                tenantId, saved.getId(), saved.getUsername(), role.getName(), saved.getBranchId());
        return saved;
    }

    @Transactional
    public User updateUser(Long tenantId, Long userId, CreateUserRequest request) {
        User user = users.findById(userId)
                .filter(u -> u.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("User not found."));
        Role role = roles.findById(request.getRoleId()).orElseThrow();
        validateUserBranch(tenantId, role, request.getBranchId());
        users.findAllByUsernameForMobileLogin(normalize(request.getUsername())).stream()
                .filter(existing -> !existing.getId().equals(userId))
                .findFirst()
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Username is already used by another shop. Mobile usernames must be unique.");
                });
        user.setRole(role);
        user.setBranchId(request.getBranchId());
        user.setUsername(normalize(request.getUsername()));
        user.setEmail(normalize(request.getEmail()));
        user.setFirstName(clean(request.getFirstName()));
        user.setLastName(clean(request.getLastName()));
        user.setPhone(clean(request.getPhone()));
        user.setEmployeeNumber(clean(request.getEmployeeNumber()));
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
            user.setForcePasswordChange(true);
        }
        User saved = users.save(user);
        log.info("User updated tenant={} user={} username={} role={} branch={}",
                tenantId, saved.getId(), saved.getUsername(), role.getName(), saved.getBranchId());
        return saved;
    }

    @Transactional
    public User setUserActive(Long tenantId, Long userId, boolean active) {
        User user = users.findById(userId)
                .filter(u -> u.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("User not found."));
        user.setIsActive(active);
        User saved = users.save(user);
        log.info("User status changed tenant={} user={} active={}", tenantId, userId, active);
        return saved;
    }

    @Transactional
    public Branch createBranch(Long tenantId, Branch request) {
        validateBranchCodeAvailable(tenantId, null, request.getBranchCode());
        validateTenantModule(tenantId, moduleOrShop(request.getModuleType()));
        Branch branch = branches.save(Branch.builder()
                .tenantId(tenantId)
                .branchCode(normalizeCode(request.getBranchCode()))
                .name(clean(request.getName()))
                .moduleType(moduleOrShop(request.getModuleType()))
                .city(clean(request.getCity()))
                .phone(clean(request.getPhone()))
                .email(normalize(request.getEmail()))
                .address(clean(request.getAddress()))
                .isActive(true)
                .build());
        prepareBranchOperations(tenantId, branch.getId());
        log.info("Branch created tenant={} branch={} code={} name={}",
                tenantId, branch.getId(), branch.getBranchCode(), branch.getName());
        return branch;
    }

    @Transactional
    public Branch updateBranch(Long tenantId, Long branchId, Branch request) {
        Branch branch = branches.findById(branchId)
                .filter(b -> b.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Branch not found."));
        validateBranchCodeAvailable(tenantId, branchId, request.getBranchCode());
        validateTenantModule(tenantId, moduleOrShop(request.getModuleType()));
        branch.setBranchCode(normalizeCode(request.getBranchCode()));
        branch.setName(clean(request.getName()));
        branch.setModuleType(moduleOrShop(request.getModuleType()));
        branch.setCity(clean(request.getCity()));
        branch.setPhone(clean(request.getPhone()));
        branch.setEmail(normalize(request.getEmail()));
        branch.setAddress(clean(request.getAddress()));
        Branch saved = branches.save(branch);
        log.info("Branch updated tenant={} branch={} code={} name={}",
                tenantId, saved.getId(), saved.getBranchCode(), saved.getName());
        return saved;
    }

    @Transactional
    public Branch setBranchActive(Long tenantId, Long branchId, boolean active) {
        Branch branch = branches.findById(branchId)
                .filter(b -> b.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Branch not found."));
        if (!active && branches.findByTenantIdAndIsActiveTrue(tenantId).size() <= 1) {
            throw new IllegalStateException("At least one active branch is required.");
        }
        if (!active && cashSessions.findByTenantIdAndBranchIdAndStatus(tenantId, branchId, CashSession.SessionStatus.OPEN).isPresent()) {
            throw new IllegalStateException("Close all open cashier shifts before deactivating this branch.");
        }
        branch.setIsActive(active);
        Branch saved = branches.save(branch);
        if (active) {
            prepareBranchOperations(tenantId, branchId);
        }
        log.info("Branch status changed tenant={} branch={} active={}", tenantId, branchId, active);
        return saved;
    }

    @Transactional
    public InventoryAdjustment adjustStock(Long tenantId, Long branchId, StockAdjustmentRequest request, Long userId) {
        validateActiveBranch(tenantId, branchId);
        if (userId == null) {
            throw new IllegalArgumentException("A signed-in user is required to adjust stock.");
        }
        BigDecimal quantityChange = nvl(request.getQuantityChange());
        if (quantityChange.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("Stock adjustment quantity cannot be zero.");
        }
        if (request.getReason() == null) {
            throw new IllegalArgumentException("Select a stock adjustment reason.");
        }
        Product product = products.findById(request.getProductId())
                .filter(p -> p.getTenantId().equals(tenantId))
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .orElseThrow(() -> new IllegalArgumentException("Product is not available for this shop."));
        Inventory item = inventory.lockStock(tenantId, branchId, product.getId())
                .orElseThrow(() -> new IllegalArgumentException("Product is not enabled for this branch. Open the product module and assign it to the branch first."));
        BigDecimal before = nvl(item.getQuantityOnHand());
        BigDecimal after = before.add(quantityChange);
        if (after.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Stock adjustment would make branch stock negative.");
        }
        item.setQuantityOnHand(after);
        inventory.save(item);

        InventoryAdjustment adjustment = adjustments.save(InventoryAdjustment.builder()
                .tenantId(tenantId)
                .branchId(branchId)
                .productId(product.getId())
                .adjustmentNumber("ADJ-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + System.currentTimeMillis())
                .quantityChange(quantityChange)
                .quantityBefore(before)
                .quantityAfter(after)
                .reason(request.getReason())
                .notes(request.getNotes())
                .createdBy(userId)
                .build());

        inventoryTransactions.save(InventoryTransaction.builder()
                .tenantId(tenantId)
                .branchId(branchId)
                .productId(product.getId())
                .type(InventoryTransaction.TransactionType.ADJUSTMENT)
                .quantity(quantityChange)
                .quantityBefore(before)
                .quantityAfter(after)
                .referenceType("ADJUSTMENT")
                .referenceId(adjustment.getId())
                .notes(request.getNotes())
                .createdBy(userId)
                .build());
        log.info("Stock adjusted tenant={} branch={} product={} before={} change={} after={} user={} reason={}",
                tenantId, branchId, product.getId(), before, quantityChange, after, userId, request.getReason());
        return adjustment;
    }

    @Transactional
    public CashSession openSession(Long tenantId, Long branchId, Long cashierId, OpenSessionRequest request) {
        cashSessions.findActiveSession(tenantId, branchId, cashierId).ifPresent(s -> {
            throw new IllegalStateException("Cashier already has an open session.");
        });
        CashSession saved = cashSessions.save(CashSession.builder()
                .tenantId(tenantId)
                .branchId(branchId)
                .drawerId(request.getDrawerId())
                .cashierId(cashierId)
                .status(CashSession.SessionStatus.OPEN)
                .openingFloatUsd(nvl(request.getOpeningFloatUsd()))
                .openingFloatZwg(nvl(request.getOpeningFloatZwg()))
                .expectedCashUsd(nvl(request.getOpeningFloatUsd()))
                .expectedCashZwg(nvl(request.getOpeningFloatZwg()))
                .openedAt(LocalDateTime.now())
                .build());
        log.info("Cash session opened tenant={} branch={} cashier={} drawer={} session={} floatUsd={} floatZwg={}",
                tenantId, branchId, cashierId, request.getDrawerId(), saved.getId(),
                saved.getOpeningFloatUsd(), saved.getOpeningFloatZwg());
        return saved;
    }

    @Transactional
    public CashSession closeSession(Long tenantId, Long branchId, Long cashierId, CloseSessionRequest request) {
        CashSession session = request.getSessionId() == null
                ? cashSessions.findActiveSession(tenantId, branchId, cashierId)
                    .orElseThrow(() -> new IllegalStateException("Open a shift before closing."))
                : cashSessions.findById(request.getSessionId())
                    .filter(cs -> cs.getTenantId().equals(tenantId))
                    .filter(cs -> cs.getBranchId().equals(branchId))
                    .filter(cs -> cs.getCashierId().equals(cashierId))
                    .orElseThrow(() -> new IllegalStateException("This shift does not belong to the signed-in cashier."));
        if (!CashSession.SessionStatus.OPEN.equals(session.getStatus())) {
            throw new IllegalStateException("Cash session is already closed.");
        }
        session.setStatus(CashSession.SessionStatus.CLOSED);
        session.setActualCashUsd(nvl(request.getActualUsd()));
        session.setActualCashZwg(nvl(request.getActualZwg()));
        session.setClosingFloatUsd(nvl(request.getActualUsd()));
        session.setClosingFloatZwg(nvl(request.getActualZwg()));
        session.setVarianceUsd(nvl(request.getActualUsd()).subtract(nvl(session.getExpectedCashUsd())));
        session.setVarianceZwg(nvl(request.getActualZwg()).subtract(nvl(session.getExpectedCashZwg())));
        session.setClosingNotes(request.getClosingNotes());
        session.setClosedAt(LocalDateTime.now());
        CashSession saved = cashSessions.save(session);
        log.info("Cash session closed tenant={} branch={} cashier={} session={} transactions={} expectedUsd={} actualUsd={} varianceUsd={} expectedZwg={} actualZwg={} varianceZwg={}",
                tenantId, branchId, cashierId, saved.getId(), saved.getTotalTransactions(),
                saved.getExpectedCashUsd(), saved.getActualCashUsd(), saved.getVarianceUsd(),
                saved.getExpectedCashZwg(), saved.getActualCashZwg(), saved.getVarianceZwg());
        return saved;
    }

    @Transactional
    public Sale completeSale(Long tenantId, Long branchId, Long cashierId, SaleRequest request) {
        final Long requestedBranchId = branchId;
        String offlineReceipt = clean(request.getOfflineReceiptNumber());
        if (offlineReceipt != null) {
            var existing = sales.findByTenantIdAndOfflineReceiptNumber(tenantId, offlineReceipt);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        CashSession session;
        if (request.getCashSessionId() != null) {
            session = cashSessions.findById(request.getCashSessionId())
                    .filter(cs -> cs.getTenantId().equals(tenantId))
                    .filter(cs -> cs.getBranchId().equals(requestedBranchId))
                    .filter(cs -> cs.getCashierId().equals(cashierId))
                    .orElseThrow(() -> new IllegalStateException("This sale belongs to another shift or cashier."));
            if (!CashSession.SessionStatus.OPEN.equals(session.getStatus())) {
                throw new IllegalStateException("This sale belongs to a shift that is already closed.");
            }
        } else {
            session = cashSessions.findActiveSession(tenantId, requestedBranchId, cashierId)
                    .orElseThrow(() -> new IllegalStateException("Open a shift before completing sales."));
        }
        if (!CashSession.SessionStatus.OPEN.equals(session.getStatus())) {
            throw new IllegalStateException("Cash session is not open.");
        }
        Long sessionCashierId = session.getCashierId();
        Long saleBranchId = session.getBranchId();
        Sale sale = Sale.builder()
                .tenantId(tenantId)
                .branchId(saleBranchId)
                .receiptNumber(receiptNumber(saleBranchId))
                .cashSessionId(session.getId())
                .cashierId(sessionCashierId)
                .customerId(request.getCustomerId())
                .currency(request.getCurrency())
                .couponCode(request.getCouponCode())
                .isOfflineSale(offlineReceipt != null)
                .offlineReceiptNumber(offlineReceipt)
                .offlineCreatedAt(parseDateTime(request.getOfflineCreatedAt()))
                .status(Sale.SaleStatus.COMPLETED)
                .build();

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        for (SaleItemRequest line : request.getItems()) {
            Product product = products.findById(line.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Product " + line.getProductId() + " was not found."));
            if (!product.getTenantId().equals(tenantId) || !Boolean.TRUE.equals(product.getIsActive())) {
                throw new IllegalArgumentException("Product is not available for this shop.");
            }
            BigDecimal quantity = nvl(line.getQuantity());
            Inventory stock = inventory.lockStock(tenantId, saleBranchId, product.getId())
                    .orElseThrow(() -> new IllegalArgumentException(product.getName() + " is not enabled for this branch."));
            if (!Boolean.TRUE.equals(product.getIsService())) {
                BigDecimal available = nvl(stock.getQuantityOnHand()).subtract(nvl(stock.getQuantityReserved()));
                if (available.compareTo(quantity) < 0) {
                    throw new IllegalArgumentException(product.getName() + " has only " + available + " available at this branch.");
                }
            }
            BigDecimal unitPrice = CurrencyCode.ZWG.equals(request.getCurrency()) ? product.getSellingPriceZwg() : product.getSellingPriceUsd();
            if (line.getUnitPrice() != null) unitPrice = line.getUnitPrice();
            BigDecimal discount = nvl(line.getDiscountAmount());
            BigDecimal lineSubtotal = unitPrice.multiply(quantity).subtract(discount).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineTax = Boolean.TRUE.equals(product.getIsTaxable()) ? lineSubtotal.multiply(nvl(product.getTaxRate())).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            subtotal = subtotal.add(lineSubtotal);
            tax = tax.add(lineTax);
            BigDecimal lineCost = (CurrencyCode.ZWG.equals(request.getCurrency()) ? product.getCostPriceZwg() : product.getCostPriceUsd()).multiply(quantity);
            cost = cost.add(lineCost);

            SaleItem item = SaleItem.builder()
                    .sale(sale)
                    .productId(product.getId())
                    .productName(product.getName())
                    .productSku(product.getSku())
                    .productBarcode(product.getBarcode())
                    .quantity(quantity)
                    .unitPrice(unitPrice)
                    .costPrice(CurrencyCode.ZWG.equals(request.getCurrency()) ? product.getCostPriceZwg() : product.getCostPriceUsd())
                    .discountAmount(discount)
                    .taxRate(product.getTaxRate())
                    .taxAmount(lineTax)
                    .lineTotal(lineSubtotal.add(lineTax))
                    .build();
            sale.getItems().add(item);
        }
        sale.setSubtotal(subtotal);
        sale.setTaxAmount(tax);
        sale.setGrandTotal(subtotal.add(tax));
        sale.setTotalCost(cost);
        sale.setGrossProfit(sale.getGrandTotal().subtract(cost));

        for (SalePaymentRequest paymentRequest : request.getPayments()) {
            sale.getPayments().add(SalePayment.builder()
                    .sale(sale)
                    .paymentMethod(paymentRequest.getMethod())
                    .currency(paymentRequest.getCurrency())
                    .amount(paymentRequest.getAmount())
                    .exchangeRate(paymentRequest.getExchangeRate() == null ? BigDecimal.ONE : paymentRequest.getExchangeRate())
                    .referenceNumber(paymentRequest.getReference())
                    .amountUsdEquivalent(CurrencyCode.USD.equals(paymentRequest.getCurrency()) ? paymentRequest.getAmount() : paymentRequest.getAmount().divide(paymentRequest.getExchangeRate() == null ? BigDecimal.ONE : paymentRequest.getExchangeRate(), 2, RoundingMode.HALF_UP))
                    .build());
        }
        Sale saved = sales.save(sale);

        boolean borrowerCredit = saved.getPayments().stream()
                .anyMatch(payment -> SalePayment.PaymentMethod.STORE_CREDIT.equals(payment.getPaymentMethod()));
        if (borrowerCredit) {
            creditAndChange.chargeSale(
                    tenantId,
                    saleBranchId,
                    sessionCashierId,
                    session,
                    saved,
                    request.getBorrowerId(),
                    clean(request.getBorrowerOfflineReference()) == null
                            ? (offlineReceipt == null ? saved.getReceiptNumber() : offlineReceipt) + "-BORROW"
                            : request.getBorrowerOfflineReference()
            );
            saved = sales.save(saved);
        } else if (request.getBorrowerId() != null) {
            throw new IllegalArgumentException("Borrower was selected but the payment method is not borrower credit.");
        }

        if (nvl(request.getHeldChangeAmount()).compareTo(BigDecimal.ZERO) > 0) {
            creditAndChange.holdChange(
                    tenantId,
                    saleBranchId,
                    sessionCashierId,
                    session,
                    saved,
                    request.getHeldChangeName(),
                    request.getHeldChangePhone(),
                    request.getHeldChangeAmount(),
                    clean(request.getHeldChangeOfflineReference()) == null
                            ? (offlineReceipt == null ? saved.getReceiptNumber() : offlineReceipt) + "-CHANGE"
                            : request.getHeldChangeOfflineReference()
            );
        }

        for (SaleItem item : saved.getItems()) {
            Inventory stock = inventory.lockStock(tenantId, saleBranchId, item.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Product is not enabled for this branch."));
            BigDecimal before = nvl(stock.getQuantityOnHand());
            BigDecimal after = before.subtract(item.getQuantity());
            stock.setQuantityOnHand(after);
            inventory.save(stock);
            inventoryTransactions.save(InventoryTransaction.builder()
                    .tenantId(tenantId)
                    .branchId(saleBranchId)
                    .productId(item.getProductId())
                    .type(InventoryTransaction.TransactionType.SALE)
                    .quantity(item.getQuantity().negate())
                    .quantityBefore(before)
                    .quantityAfter(after)
                    .referenceType("SALE")
                    .referenceId(saved.getId())
                    .createdBy(sessionCashierId)
                    .build());
        }

        BigDecimal cashUsd = nvl(salePayments.sumCashBySaleAndCurrency(saved.getId(), CurrencyCode.USD));
        BigDecimal cashZwg = nvl(salePayments.sumCashBySaleAndCurrency(saved.getId(), CurrencyCode.ZWG));
        session.setTotalTransactions((session.getTotalTransactions() == null ? 0 : session.getTotalTransactions()) + 1);
        session.setTotalSalesUsd(nvl(session.getTotalSalesUsd()).add(CurrencyCode.USD.equals(saved.getCurrency()) ? saved.getGrandTotal() : BigDecimal.ZERO));
        session.setTotalSalesZwg(nvl(session.getTotalSalesZwg()).add(CurrencyCode.ZWG.equals(saved.getCurrency()) ? saved.getGrandTotal() : BigDecimal.ZERO));
        session.setExpectedCashUsd(nvl(session.getExpectedCashUsd()).add(cashUsd));
        session.setExpectedCashZwg(nvl(session.getExpectedCashZwg()).add(cashZwg));
        cashSessions.save(session);
        log.info("Sale completed tenant={} branch={} cashier={} session={} sale={} receipt={} currency={} total={} items={} offlineReceipt={} syncedBy={}",
                tenantId, saleBranchId, sessionCashierId, session.getId(), saved.getId(), saved.getReceiptNumber(),
                saved.getCurrency(), saved.getGrandTotal(), saved.getItems().size(), saved.getOfflineReceiptNumber(), cashierId);
        return saved;
    }

    public List<Inventory> branchInventory(Long tenantId, Long branchId) {
        return inventory.findByTenantIdAndBranchId(tenantId, branchId);
    }

    public List<CashDrawer> drawers(Long tenantId, Long branchId) {
        return drawers.findByTenantIdAndBranchIdAndIsActiveTrue(tenantId, branchId);
    }

    @Transactional
    public CashDrawer createDrawer(Long tenantId, Long branchId, String name, String description) {
        validateActiveBranch(tenantId, branchId);
        return drawers.save(CashDrawer.builder()
                .tenantId(tenantId)
                .branchId(branchId)
                .name(clean(name))
                .description(clean(description))
                .isActive(true)
                .build());
    }

    @Transactional
    public CashDrawer updateDrawer(Long tenantId, Long branchId, Long drawerId, String name, String description) {
        CashDrawer drawer = drawers.findById(drawerId)
                .filter(d -> d.getTenantId().equals(tenantId))
                .filter(d -> d.getBranchId().equals(branchId))
                .orElseThrow(() -> new IllegalArgumentException("Cash drawer not found."));
        drawer.setName(clean(name));
        drawer.setDescription(clean(description));
        return drawers.save(drawer);
    }

    @Transactional
    public CashDrawer setDrawerActive(Long tenantId, Long branchId, Long drawerId, boolean active) {
        CashDrawer drawer = drawers.findById(drawerId)
                .filter(d -> d.getTenantId().equals(tenantId))
                .filter(d -> d.getBranchId().equals(branchId))
                .orElseThrow(() -> new IllegalArgumentException("Cash drawer not found."));
        if (!active && !cashSessions.findByDrawerIdAndStatus(drawerId, CashSession.SessionStatus.OPEN).isEmpty()) {
            throw new IllegalStateException("Close open shifts before deactivating this drawer.");
        }
        drawer.setIsActive(active);
        return drawers.save(drawer);
    }

    public List<Sale> recentSales(Long tenantId, Long branchId) {
        return sales.findByTenantIdAndBranchId(tenantId, branchId, PageRequest.of(0, 25, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private void validateUserBranch(Long tenantId, Role role, Long branchId) {
        if (role != null && UserRole.CASHIER.equals(role.getName()) && branchId == null) {
            throw new IllegalArgumentException("Cashier users must be assigned to an active branch.");
        }
        if (branchId == null) {
            return;
        }
        branches.findById(branchId)
                .filter(branch -> branch.getTenantId().equals(tenantId))
                .filter(branch -> Boolean.TRUE.equals(branch.getIsActive()))
                .orElseThrow(() -> new IllegalArgumentException("Selected branch is inactive or does not belong to this shop."));
    }

    private void validateActiveBranch(Long tenantId, Long branchId) {
        branches.findById(branchId)
                .filter(branch -> branch.getTenantId().equals(tenantId))
                .filter(branch -> Boolean.TRUE.equals(branch.getIsActive()))
                .orElseThrow(() -> new IllegalArgumentException("Selected branch is inactive or does not belong to this shop."));
    }

    private void validateTenantModule(Long tenantId, BusinessModule module) {
        if (BusinessModule.SHOP_MODULE.equals(module)) {
            return;
        }
        if (!tenantModules.existsByTenantIdAndModuleAndStatus(tenantId, module, ModuleAccessStatus.ENABLED)) {
            throw new IllegalArgumentException("This shop package does not include " + module.getDisplayName() + ".");
        }
    }

    private BusinessModule moduleOrShop(BusinessModule module) {
        if (module == null || BusinessModule.RESTAURANT_MODULE.equals(module)) {
            return BusinessModule.SHOP_MODULE;
        }
        return module;
    }

    private void validateBranchCodeAvailable(Long tenantId, Long currentBranchId, String branchCode) {
        String normalized = normalizeCode(branchCode);
        if (normalized == null || normalized.isBlank()) {
            throw new IllegalArgumentException("Branch code is required.");
        }
        branches.findByTenantIdAndBranchCode(tenantId, normalized)
                .filter(existing -> currentBranchId == null || !existing.getId().equals(currentBranchId))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Branch code is already used in this shop.");
                });
    }

    private void prepareBranchOperations(Long tenantId, Long branchId) {
        if (drawers.findByTenantIdAndBranchIdAndIsActiveTrue(tenantId, branchId).isEmpty()) {
            drawers.save(CashDrawer.builder()
                    .tenantId(tenantId)
                    .branchId(branchId)
                    .name("Till 1")
                    .description("Main counter")
                    .isActive(true)
                    .build());
        }
    }

    private String normalizeCode(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase();
    }

    private String clean(String value) {
        return value == null ? null : value.trim();
    }

    private String receiptNumber(Long branchId) {
        return "BR" + branchId + "-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + System.currentTimeMillis();
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }
}

