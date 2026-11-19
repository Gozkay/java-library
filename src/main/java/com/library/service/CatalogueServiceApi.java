package com.library.service;

import com.library.model.Item;
import com.library.model.ItemType;
import java.util.List;
import java.util.Optional;
import java.util.Locale;

public interface CatalogueServiceApi {
    List<Item> search(String query, ItemType typeFilter, boolean availableOnly);

    default List<Item> search(String query, String authorFilter, String categoryFilter,
                              ItemType typeFilter, boolean availableOnly) {
        return search(query, typeFilter, availableOnly).stream()
                .filter(item -> authorFilter == null || authorFilter.isBlank()
                        || item.getAuthor() != null
                        && item.getAuthor().toLowerCase(Locale.ROOT).contains(authorFilter.toLowerCase(Locale.ROOT)))
                .filter(item -> categoryFilter == null || categoryFilter.isBlank()
                        || item.getCategory() != null
                        && item.getCategory().toLowerCase(Locale.ROOT).contains(categoryFilter.toLowerCase(Locale.ROOT)))
                .toList();
    }

    Item addItem(Item item);
    Optional<Item> findById(String itemId);
    void addCopies(String itemId, int count);
    void updateItem(String itemId, String title, String category, Integer publicationYear);
    void retireItem(String itemId);
}
