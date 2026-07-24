package com.retailzw.service;

import com.retailzw.enums.CurrencyCode;
import com.retailzw.model.*;
import com.retailzw.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class PurchaseOrderServiceTest {

    private PurchaseOrderRepository purchaseOrders;
    private PurchaseOrderItemRepository purchaseOrderItems;
    private InventoryRepository inventory;
    private InventoryTransactionRepository inventoryTransactions;
    private GoodsReceivedNoteRepository goodsReceivedNotes;
    private BranchRepository branches;
    private ProductRepository products;
    private SupplierRepository suppliers;
    private PurchaseOrderService service;

    @BeforeEach
    public void setUp() {
        purchaseOrders = mock(PurchaseOrderRepository.class);
        purchaseOrderItems = mock(PurchaseOrderItemRepository.class);
        inventory = mock(InventoryRepository.class);
        inventoryTransactions = mock(InventoryTransactionRepository.class);
        goodsReceivedNotes = mock(GoodsReceivedNoteRepository.class);
        branches = mock(BranchRepository.class);
        products = mock(ProductRepository.class);
        suppliers = mock(SupplierRepository.class);
        service = new PurchaseOrderService(
                purchaseOrders, purchaseOrderItems, inventory, inventoryTransactions,
                goodsReceivedNotes, branches, products, suppliers,
                mock(NotificationService.class), mock(PurchaseOrderApprovalRepository.class));
    }

    @Test
    public void receivingGoodsIncreasesStockAndCreatesAuditRecords() {
        PurchaseOrderItem line = PurchaseOrderItem.builder()
                .id(51L).productId(101L)
                .quantity(new BigDecimal("10"))
                .quantityReceived(new BigDecimal("2"))
                .unitCostUsd(new BigDecimal("4"))
                .unitCostZwg(new BigDecimal("100"))
                .build();
        PurchaseOrder po = PurchaseOrder.builder()
                .id(7L).tenantId(2L).branchId(3L).supplierId(9L)
                .poNumber("PO-BR1-7").status(PurchaseOrder.PoStatus.ORDERED)
                .currency(CurrencyCode.USD).items(List.of(line)).build();
        line.setPurchaseOrder(po);
        Inventory stock = Inventory.builder()
                .id(33L).tenantId(2L).branchId(3L).productId(101L)
                .quantityOnHand(new BigDecimal("5"))
                .quantityOnOrder(new BigDecimal("8"))
                .averageCostUsd(new BigDecimal("2"))
                .averageCostZwg(new BigDecimal("80"))
                .build();

        when(purchaseOrders.lockById(7L)).thenReturn(Optional.of(po));
        when(inventory.lockStock(2L, 3L, 101L)).thenReturn(Optional.of(stock));
        when(purchaseOrders.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(purchaseOrderItems.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(inventory.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PurchaseOrder result = service.receiveGoods(7L, Map.of(51L, new BigDecimal("3")), 4L);

        assertThat(result.getStatus()).isEqualTo(PurchaseOrder.PoStatus.PARTIAL);
        assertThat(line.getQuantityReceived()).isEqualByComparingTo("5");
        assertThat(stock.getQuantityOnHand()).isEqualByComparingTo("8");
        assertThat(stock.getQuantityOnOrder()).isEqualByComparingTo("5");
        assertThat(stock.getAverageCostUsd()).isEqualByComparingTo("2.7500");
        assertThat(stock.getAverageCostZwg()).isEqualByComparingTo("87.5000");

        ArgumentCaptor<InventoryTransaction> movement = ArgumentCaptor.forClass(InventoryTransaction.class);
        verify(inventoryTransactions).save(movement.capture());
        assertThat(movement.getValue().getType()).isEqualTo(InventoryTransaction.TransactionType.PURCHASE);
        assertThat(movement.getValue().getQuantityBefore()).isEqualByComparingTo("5");
        assertThat(movement.getValue().getQuantityAfter()).isEqualByComparingTo("8");
        assertThat(movement.getValue().getReferenceId()).isEqualTo(7L);

        ArgumentCaptor<GoodsReceivedNote> grn = ArgumentCaptor.forClass(GoodsReceivedNote.class);
        verify(goodsReceivedNotes).save(grn.capture());
        assertThat(grn.getValue().getPurchaseOrderId()).isEqualTo(7L);
        assertThat(grn.getValue().getReceivedBy()).isEqualTo(4L);
    }

    @Test
    public void receivingWithoutPositivePoLinesDoesNotCreateAGrn() {
        PurchaseOrderItem line = PurchaseOrderItem.builder()
                .id(51L).productId(101L).quantity(BigDecimal.TEN)
                .quantityReceived(BigDecimal.ZERO).build();
        PurchaseOrder po = PurchaseOrder.builder()
                .id(7L).tenantId(2L).branchId(3L).supplierId(9L)
                .status(PurchaseOrder.PoStatus.ORDERED).items(List.of(line)).build();
        when(purchaseOrders.lockById(7L)).thenReturn(Optional.of(po));

        assertThatThrownBy(() -> service.receiveGoods(7L, Map.of(51L, BigDecimal.ZERO), 4L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("received quantity");
        verify(goodsReceivedNotes, never()).save(any());
        verify(inventory, never()).save(any());
    }

    @Test
    public void approvalAddsOutstandingQuantityToStockOnOrder() {
        PurchaseOrderItem line = PurchaseOrderItem.builder()
                .id(51L).productId(101L).quantity(new BigDecimal("6"))
                .quantityReceived(BigDecimal.ZERO).build();
        PurchaseOrder po = PurchaseOrder.builder()
                .id(7L).tenantId(2L).branchId(3L).createdBy(4L)
                .status(PurchaseOrder.PoStatus.SUBMITTED).items(List.of(line)).build();
        Inventory stock = Inventory.builder().id(33L).tenantId(2L).branchId(3L).productId(101L)
                .quantityOnHand(new BigDecimal("2")).quantityOnOrder(BigDecimal.ZERO).build();
        when(purchaseOrders.lockById(7L)).thenReturn(Optional.of(po));
        when(inventory.lockStock(2L, 3L, 101L)).thenReturn(Optional.of(stock));
        when(inventory.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(purchaseOrders.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PurchaseOrder result = service.approvePurchaseOrder(7L, 8L);

        assertThat(result.getStatus()).isEqualTo(PurchaseOrder.PoStatus.APPROVED);
        assertThat(result.getApprovedBy()).isEqualTo(8L);
        assertThat(stock.getQuantityOnOrder()).isEqualByComparingTo("6");
    }

    @Test
    public void cancellingOrderedPoReleasesOutstandingStockOnOrder() {
        PurchaseOrderItem line = PurchaseOrderItem.builder()
                .id(51L).productId(101L).quantity(BigDecimal.TEN)
                .quantityReceived(new BigDecimal("3")).build();
        PurchaseOrder po = PurchaseOrder.builder()
                .id(7L).tenantId(2L).branchId(3L)
                .status(PurchaseOrder.PoStatus.ORDERED).items(List.of(line)).build();
        Inventory stock = Inventory.builder().id(33L).tenantId(2L).branchId(3L).productId(101L)
                .quantityOnHand(new BigDecimal("5")).quantityOnOrder(new BigDecimal("7")).build();
        when(purchaseOrders.lockById(7L)).thenReturn(Optional.of(po));
        when(inventory.lockStock(2L, 3L, 101L)).thenReturn(Optional.of(stock));
        when(inventory.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(purchaseOrders.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PurchaseOrder result = service.cancelPurchaseOrder(7L);

        assertThat(result.getStatus()).isEqualTo(PurchaseOrder.PoStatus.CANCELLED);
        assertThat(stock.getQuantityOnOrder()).isEqualByComparingTo("0");
    }

    @Test
    public void autoReorderCreatesTargetStockQuantityAndSkipsDuplicateOpenLines() {
        Branch branch = Branch.builder().id(3L).tenantId(2L).branchCode("BR1").isActive(true).build();
        Supplier supplier = Supplier.builder().id(9L).tenantId(2L).name("Main Supplier").isActive(true).build();
        Product product = Product.builder().id(101L).tenantId(2L).name("Sugar").isActive(true)
                .reorderLevel(new BigDecimal("5")).maxStockLevel(new BigDecimal("10"))
                .costPriceUsd(new BigDecimal("2")).costPriceZwg(new BigDecimal("50")).build();
        Inventory stock = Inventory.builder().id(33L).tenantId(2L).branchId(3L).productId(101L)
                .quantityOnHand(new BigDecimal("2")).quantityOnOrder(BigDecimal.ZERO).build();

        when(inventory.findLowStockItems(2L, 3L)).thenReturn(List.of(stock));
        when(branches.findById(3L)).thenReturn(Optional.of(branch));
        when(suppliers.findByTenantIdAndIsActiveTrue(2L)).thenReturn(List.of(supplier));
        when(products.findById(101L)).thenReturn(Optional.of(product));
        when(purchaseOrders.save(any())).thenAnswer(invocation -> {
            PurchaseOrder value = invocation.getArgument(0);
            if (value.getId() == null) value.setId(20L);
            return value;
        });
        when(purchaseOrderItems.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<PurchaseOrder> generated = service.autoGeneratePurchaseOrders(2L, 3L, 4L);

        assertThat(generated).hasSize(1);
        assertThat(generated.get(0).getCreatedBy()).isEqualTo(4L);
        assertThat(generated.get(0).getItems()).hasSize(1);
        assertThat(generated.get(0).getItems().get(0).getQuantity()).isEqualByComparingTo("8");
        assertThat(generated.get(0).getTotalUsd()).isEqualByComparingTo("16.00");

        PurchaseOrder openPo = PurchaseOrder.builder().id(21L).tenantId(2L).branchId(3L)
                .status(PurchaseOrder.PoStatus.DRAFT)
                .items(List.of(PurchaseOrderItem.builder().productId(101L).quantity(new BigDecimal("8"))
                        .quantityReceived(BigDecimal.ZERO).build()))
                .build();
        when(purchaseOrders.findByTenantIdAndBranchIdAndStatus(2L, 3L, PurchaseOrder.PoStatus.DRAFT))
                .thenReturn(List.of(openPo));
        clearInvocations(purchaseOrders, purchaseOrderItems);

        assertThat(service.autoGeneratePurchaseOrders(2L, 3L, 4L)).isEmpty();
        verify(purchaseOrderItems, never()).save(any());
    }
}
