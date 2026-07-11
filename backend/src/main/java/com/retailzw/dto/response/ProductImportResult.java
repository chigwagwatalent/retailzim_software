package com.retailzw.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class ProductImportResult {

    private int processedRows;
    private int createdProducts;
    private int updatedProducts;
    private int stockRowsUpdated;
    private int skippedRows;

    @Builder.Default
    private List<String> errors = new ArrayList<>();
}
