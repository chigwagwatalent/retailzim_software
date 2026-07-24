package com.retailzw.dto.response;

import java.util.ArrayList;
import java.util.List;

public class ProductImportResult {

    private int processedRows;
    private int createdProducts;
    private int updatedProducts;
    private int stockRowsUpdated;
    private int skippedRows;

    private List<String> errors = new ArrayList<>();

    public static Builder builder() {
        return new Builder();
    }

    public int getProcessedRows() {
        return processedRows;
    }

    public void setProcessedRows(int processedRows) {
        this.processedRows = processedRows;
    }

    public int getCreatedProducts() {
        return createdProducts;
    }

    public void setCreatedProducts(int createdProducts) {
        this.createdProducts = createdProducts;
    }

    public int getUpdatedProducts() {
        return updatedProducts;
    }

    public void setUpdatedProducts(int updatedProducts) {
        this.updatedProducts = updatedProducts;
    }

    public int getStockRowsUpdated() {
        return stockRowsUpdated;
    }

    public void setStockRowsUpdated(int stockRowsUpdated) {
        this.stockRowsUpdated = stockRowsUpdated;
    }

    public int getSkippedRows() {
        return skippedRows;
    }

    public void setSkippedRows(int skippedRows) {
        this.skippedRows = skippedRows;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors == null ? new ArrayList<>() : errors;
    }

    public static class Builder {
        private final ProductImportResult result = new ProductImportResult();

        public Builder processedRows(int processedRows) {
            result.setProcessedRows(processedRows);
            return this;
        }

        public Builder createdProducts(int createdProducts) {
            result.setCreatedProducts(createdProducts);
            return this;
        }

        public Builder updatedProducts(int updatedProducts) {
            result.setUpdatedProducts(updatedProducts);
            return this;
        }

        public Builder stockRowsUpdated(int stockRowsUpdated) {
            result.setStockRowsUpdated(stockRowsUpdated);
            return this;
        }

        public Builder skippedRows(int skippedRows) {
            result.setSkippedRows(skippedRows);
            return this;
        }

        public Builder errors(List<String> errors) {
            result.setErrors(errors);
            return this;
        }

        public ProductImportResult build() {
            return result;
        }
    }
}
