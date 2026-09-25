package com.retailzw.controller.api;

import com.retailzw.dto.request.FuelStationRequests.CloseShift;
import com.retailzw.dto.request.FuelStationRequests.Delivery;
import com.retailzw.dto.request.FuelStationRequests.OpenShift;
import com.retailzw.dto.request.FuelStationRequests.Sale;
import com.retailzw.dto.response.ApiResponse;
import com.retailzw.service.CurrentUserService;
import com.retailzw.service.FuelStationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/fuel")
@RequiredArgsConstructor
public class FuelStationApiController {
    private final CurrentUserService current;
    private final FuelStationService fuel;

    @GetMapping("/pos/bootstrap")
    public ApiResponse<?> bootstrap(@RequestParam Long branchId) {
        requireRole("CASHIER", "SUPERVISOR");
        return ApiResponse.success(fuel.posBootstrap(current.tenantId(), current.userId(), current.branchId(), branchId));
    }

    @PostMapping("/pos/shifts/open")
    public ApiResponse<?> openShift(@Valid @RequestBody OpenShift request) {
        requireRole("CASHIER", "SUPERVISOR");
        return ApiResponse.success("Fuel shift opened",
                java.util.Map.of("id", fuel.openShift(current.tenantId(), current.userId(), current.branchId(), request)));
    }

    @PostMapping("/pos/sales")
    public ApiResponse<?> sale(@Valid @RequestBody Sale request) {
        requireRole("CASHIER", "SUPERVISOR");
        return ApiResponse.success("Fuel sale completed",
                java.util.Map.of("id", fuel.sale(current.tenantId(), current.userId(), current.branchId(), request)));
    }

    @PostMapping("/pos/shifts/close")
    public ApiResponse<?> closeShift(@Valid @RequestBody CloseShift request) {
        requireRole("CASHIER", "SUPERVISOR");
        fuel.closeShift(current.tenantId(), current.userId(), current.branchId(), request);
        return ApiResponse.success("Fuel shift reconciled and closed", java.util.Map.of("id", request.shiftId()));
    }

    @PostMapping("/deliveries")
    public ApiResponse<?> delivery(@Valid @RequestBody Delivery request) {
        requireRole("SUPER_ADMIN", "ACCOUNTANT", "SUPERVISOR");
        return ApiResponse.success("Fuel delivery received",
                java.util.Map.of("id", fuel.delivery(current.tenantId(), current.userId(), current.branchId(), request)));
    }

    private void requireRole(String... allowed) {
        if (!Set.of(allowed).contains(current.roleName())) {
            throw new AccessDeniedException("This fuel operation is not available to your role.");
        }
    }
}
