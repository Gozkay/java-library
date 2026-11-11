package com.library.repository.json;

import com.google.gson.Gson;
import com.library.model.Item;
import com.library.repository.ItemRepository;

import java.nio.file.Path;

/** Persists to {@code <dataDir>/items.json}. */
public class JsonItemRepository extends JsonFileRepository<Item, String> implements ItemRepository {
    public JsonItemRepository(Path dataDir, Gson gson) {
        super(dataDir.resolve("items.json"), gson, Item.class, Item::getItemId);
    }
}
