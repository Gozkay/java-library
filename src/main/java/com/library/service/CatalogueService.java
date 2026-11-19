package com.library.service;

import com.library.exception.ItemNotFoundException;
import com.library.model.Item;
import com.library.model.ItemType;
import com.library.repository.ItemRepository;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public final class CatalogueService implements CatalogueServiceApi {
    private final ItemRepository items;

    public CatalogueService(ItemRepository items) {
        this.items = Objects.requireNonNull(items, "items repository required");
    }

    @Override
    public List<Item> search(String query, ItemType typeFilter, boolean availableOnly) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return items.findAll().stream()
                .filter(item -> !item.isRetired())
                .filter(item -> typeFilter == null || item.getType() == typeFilter)
                .filter(item -> !availableOnly || item.countAvailable() > 0)
                .filter(item -> normalized.isEmpty()
                        || contains(item.getItemId(), normalized)
                        || contains(item.getTitle(), normalized)
                        || contains(item.getCategory(), normalized)
                        || contains(item.getAuthor(), normalized))
                .toList();
    }

    @Override
    public Item addItem(Item item) {
        Objects.requireNonNull(item, "item required");
        if (item.getItemId() == null || item.getItemId().isBlank()) {
            throw new IllegalArgumentException("Item ID is required.");
        }
        if (item.getTitle() == null || item.getTitle().isBlank()) {
            throw new IllegalArgumentException("Item title is required.");
        }
        if (item.getCopies().isEmpty()) {
            throw new IllegalArgumentException("An item must have at least one copy.");
        }
        if (items.existsById(item.getItemId())) {
            throw new IllegalArgumentException("An item with ID " + item.getItemId() + " already exists.");
        }
        items.save(item);
        return item;
    }

    @Override
    public Optional<Item> findById(String itemId) {
        return items.findById(itemId);
    }

    @Override
    public void addCopies(String itemId, int count) {
        if (count < 1) {
            throw new IllegalArgumentException("Copy count must be at least one.");
        }
        Item item = findItem(itemId);
        if (item.isRetired()) {
            throw new IllegalArgumentException("Copies cannot be added to a retired item.");
        }
        item.addCopies(count);
        items.save(item);
    }

    @Override
    public void updateItem(String itemId, String title, String category, Integer publicationYear) {
        Item item = findItem(itemId);
        if (item.isRetired()) {
            throw new IllegalArgumentException("A retired item cannot be edited.");
        }
        if (title != null) {
            if (title.isBlank()) {
                throw new IllegalArgumentException("Item title must not be blank.");
            }
            item.setTitle(title.trim());
        }
        if (category != null) {
            item.setCategory(category.trim());
        }
        if (publicationYear != null) {
            item.setPublicationYear(publicationYear);
        }
        items.save(item);
    }

    @Override
    public void retireItem(String itemId) {
        Item item = findItem(itemId);
        item.retire();
        items.save(item);
    }

    private Item findItem(String itemId) {
        return items.findById(itemId)
                .orElseThrow(() -> new ItemNotFoundException("Item not found: " + itemId));
    }

    private static boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }
}
