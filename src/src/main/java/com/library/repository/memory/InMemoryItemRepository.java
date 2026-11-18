package com.library.repository.memory;

import com.library.model.Item;
import com.library.repository.ItemRepository;

public class InMemoryItemRepository extends InMemoryRepository<Item, String> implements ItemRepository {
    public InMemoryItemRepository() { super(Item::getItemId); }
}
