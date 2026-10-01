package com.retailzw.controller.web;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;

class WebFormExceptionHandlerTest {

    private final WebFormExceptionHandler handler = new WebFormExceptionHandler();

    @Test
    void identifiesSkuConstraintViolations() {
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "save failed",
                new RuntimeException("Duplicate entry '7-SAME-SKU' for key 'products.uk_sku_tenant'"));

        assertThat(handler.cleanMessage(exception))
                .isEqualTo("That SKU is already used by another product in this shop. Use a different SKU or assign the existing product to this branch.");
    }

    @Test
    void identifiesBarcodeConstraintViolations() {
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "save failed",
                new RuntimeException("Duplicate entry '7-123456' for key 'products.uk_barcode_tenant'"));

        assertThat(handler.cleanMessage(exception))
                .isEqualTo("That barcode is already used by another product in this shop. Use a different barcode or assign the existing product to this branch.");
    }
}
