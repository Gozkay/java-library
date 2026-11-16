package com.library.service;
import com.library.model.Item;
import java.util.List;
import java.util.Map;

public interface CatalogueService {
    List<Item> search(String query, String author, String category, String type);
    Item addItem(String type, String title, Map<String, String> options);
    void retireItem(String itemId);
    void addCopies(String itemId, int count);
    Item getItemById(String itemId);
}
