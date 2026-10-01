package com.retailzw.service;

import com.retailzw.dto.request.CreateProductRequest;
import com.retailzw.model.Branch;
import com.retailzw.model.Inventory;
import com.retailzw.model.Product;
import com.retailzw.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RetailOperationsProductIdentityTest {

    private ProductRepository products;
    private BranchRepository branches;
    private InventoryRepository inventory;
    private RetailOperationsService service;

    @BeforeEach
    void setUp() {
        products = mock(ProductRepository.class);
        branches = mock(BranchRepository.class);
        inventory = mock(InventoryRepository.class);
        service = new RetailOperationsService(
                products,
                mock(ProductCategoryRepository.class),
                mock(UnitOfMeasureRepository.class),
                inventory,
                mock(InventoryTransactionRepository.class),
                mock(InventoryAdjustmentRepository.class),
                branches,
                mock(TenantEnabledModuleRepository.class),
                mock(CustomerRepository.class),
                mock(SupplierRepository.class),
                mock(RoleRepository.class),
                mock(UserRepository.class),
                mock(SaleRepository.class),
                mock(SalePaymentRepository.class),
                mock(CashDrawerRepository.class),
                mock(CashSessionRepository.class),
                null,
                mock(CreditAndChangeService.class),
                mock(CurrencyConversionService.class),
                mock(WholesalePricingService.class));
        when(branches.findById(3L)).thenReturn(Optional.of(
                Branch.builder().id(3L).tenantId(7L).isActive(true).build()));
    }

    @Test
    void blankSkuIsGeneratedAndBlankBarcodeIsStoredAsNull() {
        CreateProductRequest request = productRequest("iPhone X Backcover", "  ", " ");
        AtomicReference<Product> savedProduct = new AtomicReference<>();
        when(products.findByTenantIdAndSku(7L, "IPHONE-X-BACKCOVER")).thenReturn(Optional.empty());
        when(products.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            product.setId(11L);
            savedProduct.set(product);
            return product;
        });
        when(products.findById(11L)).thenAnswer(invocation -> Optional.of(savedProduct.get()));
        when(inventory.findByTenantIdAndBranchIdAndProductId(7L, 3L, 11L)).thenReturn(Optional.empty());
        when(inventory.save(any(Inventory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Product created = service.createProduct(7L, request, 5L);

        assertThat(created.getSku()).isEqualTo("IPHONE-X-BACKCOVER");
        assertThat(created.getBarcode()).isNull();
        verify(products, never()).findByTenantIdAndBarcode(anyLong(), anyString());
    }

    @Test
    void generatedSkuAddsSuffixWhenProductNameAlreadyHasThatSku() {
        CreateProductRequest request = productRequest("iPhone X Backcover", null, null);
        when(products.findByTenantIdAndSku(7L, "IPHONE-X-BACKCOVER"))
                .thenReturn(Optional.of(Product.builder().id(90L).tenantId(7L).build()));
        when(products.findByTenantIdAndSku(7L, "IPHONE-X-BACKCOVER-2")).thenReturn(Optional.empty());
        AtomicReference<Product> savedProduct = new AtomicReference<>();
        when(products.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            product.setId(12L);
            savedProduct.set(product);
            return product;
        });
        when(products.findById(12L)).thenAnswer(invocation -> Optional.of(savedProduct.get()));
        when(inventory.findByTenantIdAndBranchIdAndProductId(7L, 3L, 12L)).thenReturn(Optional.empty());
        when(inventory.save(any(Inventory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Product created = service.createProduct(7L, request, 5L);

        assertThat(created.getSku()).isEqualTo("IPHONE-X-BACKCOVER-2");
    }

    @Test
    void duplicateManualSkuExplainsTheExactConflict() {
        CreateProductRequest request = productRequest("New Product", " SAME-SKU ", null);
        when(products.findByTenantIdAndSku(7L, "SAME-SKU"))
                .thenReturn(Optional.of(Product.builder().id(99L).tenantId(7L).isActive(false).build()));

        assertThatThrownBy(() -> service.createProduct(7L, request, 5L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("SKU 'SAME-SKU' is already used by another product in this shop. Use a different SKU or assign the existing product to this branch.");
        verify(products, never()).save(any());
    }

    @Test
    void duplicateBarcodeExplainsTheExactConflict() {
        CreateProductRequest request = productRequest("New Product", "NEW-SKU", " 123456 ");
        when(products.findByTenantIdAndSku(7L, "NEW-SKU")).thenReturn(Optional.empty());
        when(products.findByTenantIdAndBarcode(7L, "123456"))
                .thenReturn(Optional.of(Product.builder().id(99L).tenantId(7L).isActive(false).build()));

        assertThatThrownBy(() -> service.createProduct(7L, request, 5L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Barcode '123456' is already used by another product in this shop. Use a different barcode or assign the existing product to this branch.");
        verify(products, never()).save(any());
    }

    private CreateProductRequest productRequest(String name, String sku, String barcode) {
        CreateProductRequest request = new CreateProductRequest();
        request.setName(name);
        request.setSku(sku);
        request.setBarcode(barcode);
        request.setBranchId(3L);
        request.setCostPriceUsd(BigDecimal.ZERO);
        request.setSellingPriceUsd(BigDecimal.ZERO);
        request.setCostPriceZwg(BigDecimal.ZERO);
        request.setSellingPriceZwg(BigDecimal.ZERO);
        request.setOpeningStock(BigDecimal.ZERO);
        return request;
    }
}
