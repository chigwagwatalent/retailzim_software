package com.retailzw.controller.api;

import com.retailzw.dto.request.CloseGasShiftRequest;
import com.retailzw.dto.request.GasPriceRequest;
import com.retailzw.dto.request.GasExpenseRequest;
import com.retailzw.dto.request.GasRestockRequest;
import com.retailzw.dto.request.GasSaleRequest;
import com.retailzw.dto.request.GasStockReconciliationRequest;
import com.retailzw.dto.request.GasTankRequest;
import com.retailzw.dto.request.OpenGasShiftRequest;
import com.retailzw.dto.response.ApiResponse;
import com.retailzw.service.CurrentUserService;
import com.retailzw.service.GasOperationsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/gas")
@RequiredArgsConstructor
public class GasApiController {
    private final CurrentUserService current;
    private final GasOperationsService gas;

    @GetMapping("/bootstrap")
    public ApiResponse<Map<String, Object>> bootstrap(@RequestParam Long branchId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("currentShift", gas.currentShift(current.tenantId(), branchId, current.userId()));
        payload.put("tanks", gas.tanks(current.tenantId(), branchId));
        payload.put("prices", gas.prices(current.tenantId(), branchId));
        payload.put("shiftSales", gas.shiftSales(current.tenantId(), branchId, current.userId()));
        payload.put("dashboard", gas.dashboard(current.tenantId(), branchId));
        payload.put("restocks", gas.restocks(current.tenantId(), branchId));
        payload.put("stockAdjustments", gas.stockAdjustments(current.tenantId(), branchId));
        payload.put("expenses", gas.expenses(current.tenantId(), branchId));
        return ApiResponse.success(payload);
    }

    @GetMapping("/tanks")
    public ApiResponse<?> tanks(@RequestParam Long branchId) {
        return ApiResponse.success(gas.tanks(current.tenantId(), branchId));
    }

    @GetMapping("/prices")
    public ApiResponse<?> prices(@RequestParam Long branchId) {
        return ApiResponse.success(gas.prices(current.tenantId(), branchId));
    }

    @GetMapping("/shift/current")
    public ApiResponse<?> currentShift(@RequestParam Long branchId) {
        return ApiResponse.success(gas.currentShift(current.tenantId(), branchId, current.userId()));
    }

    @PostMapping("/shift/open")
    public ApiResponse<?> openShift(@Valid @RequestBody OpenGasShiftRequest request) {
        return ApiResponse.success("Gas shift opened", gas.openShift(current.tenantId(), current.userId(), request));
    }

    @PostMapping("/shift/close")
    public ApiResponse<?> closeShift(@Valid @RequestBody CloseGasShiftRequest request) {
        return ApiResponse.success("Gas shift closed", gas.closeShift(current.tenantId(), current.userId(), request));
    }

    @GetMapping("/shift/sales")
    public ApiResponse<?> shiftSales(@RequestParam Long branchId) {
        return ApiResponse.success(gas.shiftSales(current.tenantId(), branchId, current.userId()));
    }

    @PostMapping("/sales")
    public ApiResponse<?> sale(@Valid @RequestBody GasSaleRequest request) {
        return ApiResponse.success("Gas sale completed", gas.completeSale(current.tenantId(), current.userId(), request));
    }

    @PostMapping("/restocks")
    public ApiResponse<?> restock(@Valid @RequestBody GasRestockRequest request) {
        return ApiResponse.success("Gas tank restocked", gas.restock(current.tenantId(), current.userId(), request));
    }

    @PostMapping("/stock/reconcile")
    public ApiResponse<?> reconcileStock(@Valid @RequestBody GasStockReconciliationRequest request) {
        return ApiResponse.success("Gas stock reconciled",
                gas.reconcileStock(current.tenantId(), current.userId(), request));
    }

    @PostMapping("/expenses")
    public ApiResponse<?> expense(@Valid @RequestBody GasExpenseRequest request) {
        return ApiResponse.success("Gas expense recorded", gas.recordExpense(current.tenantId(), current.userId(), request));
    }

    @PostMapping("/tanks")
    public ApiResponse<?> createTank(@Valid @RequestBody GasTankRequest request) {
        return ApiResponse.success("Gas tank created", gas.createTank(current.tenantId(), request));
    }

    @PutMapping("/tanks/{tankId}")
    public ApiResponse<?> updateTank(@PathVariable Long tankId, @Valid @RequestBody GasTankRequest request) {
        return ApiResponse.success("Gas tank updated", gas.updateTank(current.tenantId(), tankId, request));
    }

    @PostMapping("/prices")
    public ApiResponse<?> setPrice(@Valid @RequestBody GasPriceRequest request) {
        return ApiResponse.success("Gas price updated", gas.setPrice(current.tenantId(), request));
    }
}
