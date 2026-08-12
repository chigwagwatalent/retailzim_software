package com.retailzw.repository;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PurchaseOrderRepositoryContractTest {

    @Test
    void supervisorQueryReturnsItemCountsWithoutExposingLazyItems() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/retailzw/repository/PurchaseOrderRepository.java"));

        assertTrue(source.contains("findSupervisorReadyOrders"));
        assertTrue(source.contains("count(item.id) as itemCount"));
        assertTrue(source.contains("po.status in :statuses"));
        assertTrue(source.contains("Pageable pageable"));
        assertTrue(source.contains("Long getItemCount()"));
    }
}
