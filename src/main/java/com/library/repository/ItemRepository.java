package com.library.repository;

import com.library.model.Item;
import com.library.model.ItemType;

import java.util.List;

public interface ItemRepository extends CrudRepository<Item, String> {

    default List<Item> findByType(ItemType type) {
        return findAll().stream().filter(i -> i.getType() == type).toList();
    }
}
