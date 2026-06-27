package com.retailzw.repository;

import com.retailzw.model.StocktakeItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface StocktakeItemRepository extends JpaRepository<StocktakeItem, Long> {

    List<StocktakeItem> findByStocktakeSessionId(Long sessionId);

    Optional<StocktakeItem> findByStocktakeSessionIdAndProductId(Long sessionId, Long productId);

    List<StocktakeItem> findByStocktakeSessionIdAndIsCounted(Long sessionId, Boolean isCounted);
}

