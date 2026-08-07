package com.retailzw.service;

import com.retailzw.dto.request.StockAdjustmentRequest;
import com.retailzw.model.Branch;
import com.retailzw.model.Inventory;
import com.retailzw.model.InventoryAdjustment;
import com.retailzw.model.InventoryTransaction;
import com.retailzw.model.Product;
import com.retailzw.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class RetailOperationsInventoryTest {

    private ProductRepository products;
    private InventoryRepository inventory;
    private InventoryTransactionRepository inventoryTransactions;
    private InventoryAdjustmentRepository adjustments;
    private BranchRepository branches;
    private RetailOperationsService service;

    @BeforeEach
    public void setUp() {
        products = mock(ProductRepository.class);
        inventory = mock(InventoryRepository.class);
        inventoryTransactions = mock(InventoryTransactionRepository.class);
        adjustments = mock(InventoryAdjustmentRepository.class);
        branches = mock(BranchRepository.class);
        service = new RetailOperationsService(
                products, null, null, inventory, inventoryTransactions, adjustments, branches,
                mock(TenantEnabledModuleRepository.class), null, null, null, null, null, null, null, null, null, null,
                mock(CurrencyConversionService.class), mock(WholesalePricingService.class));
    }

    @Test
    public void adjustmentLocksStockAndWritesAdjustmentAndMovement() {
        Branch branch = Branch.builder().id(3L).tenantId(2L).isActive(true).build();
        Product product = Product.builder().id(101L).tenantId(2L).name("Sugar").isActive(true).build();
        Inventory stock = Inventory.builder().id(8L).tenantId(2L).branchId(3L).productId(101L)
                .quantityOnHand(new BigDecimal("12")).build();
        StockAdjustmentRequest request = adjustment(new BigDecimal("-2.5"));

        when(branches.findById(3L)).thenReturn(Optional.of(branch));
        when(products.findById(101L)).thenReturn(Optional.of(product));
        when(inventory.lockStock(2L, 3L, 101L)).thenReturn(Optional.of(stock));
        when(inventory.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(adjustments.save(any())).thenAnswer(invocation -> {
            InventoryAdjustment value = invocation.getArgument(0);
            value.setId(90L);
            return value;
        });

        InventoryAdjustment result = service.adjustStock(2L, 3L, request, 4L);

        assertThat(stock.getQuantityOnHand()).isEqualByComparingTo("9.5");
        assertThat(result.getQuantityBefore()).isEqualByComparingTo("12");
        assertThat(result.getQuantityAfter()).isEqualByComparingTo("9.5");
        ArgumentCaptor<InventoryTransaction> movement = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(inventoryTransactions).save(movement.capture());
        assertThat(movement.getValue().getType()).isEqualTo(InventoryTransaction.TransactionType.ADJUSTMENT);
        assertThat(movement.getValue().getReferenceId()).isEqualTo(90L);
        assertThat(movement.getValue().getQuantityAfter()).isEqualByComparingTo("9.5");
    }

    @Test
    public void zeroAdjustmentIsRejectedBeforeStockChanges() {
        when(branches.findById(3L)).thenReturn(Optional.of(
                Branch.builder().id(3L).tenantId(2L).isActive(true).build()));

        assertThatThrownBy(() -> service.adjustStock(2L, 3L, adjustment(BigDecimal.ZERO), 4L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be zero");
        verify(inventory, never()).save(any());
        verify(adjustments, never()).save(any());
    }

    private StockAdjustmentRequest adjustment(BigDecimal quantity) {
        StockAdjustmentRequest request = new StockAdjustmentRequest();
        request.setProductId(101L);
        request.setQuantityChange(quantity);
        request.setReason(InventoryAdjustment.AdjustmentReason.CORRECTION);
        request.setNotes("Count correction");
        return request;
    }
}
