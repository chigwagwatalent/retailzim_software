package com.retailzw.dto.request;


import com.retailzw.enums.CurrencyCode;
import com.retailzw.model.Tenant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class CartRequest {

    @NotNull(message = "Currency is required")
    private CurrencyCode currency;

    @NotEmpty(message = "Cart must have at least one item")
    @Valid
    private List<SaleItemRequest> items;

    private Long customerId;

    private String couponCode;
}

