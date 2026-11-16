package com.library.repository;
import com.library.model.Item;
import java.util.*;

public interface ItemRepository {
    Optional<Item> findById(String itemId);
    List<Item> findAll();
    void save(Item item);
}
