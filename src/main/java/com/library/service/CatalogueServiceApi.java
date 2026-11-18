package com.library.service;

import com.library.model.Item;
import com.library.model.ItemType;
import java.util.List;

public interface CatalogueServiceApi {
    List<Item> search(String query, ItemType typeFilter, boolean availableOnly);
    Item addItem(Item item);
    void retireItem(String itemId);
}
