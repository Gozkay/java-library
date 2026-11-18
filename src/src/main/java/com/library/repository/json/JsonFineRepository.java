package com.library.repository.json;

import com.google.gson.Gson;
import com.library.model.Fine;
import com.library.repository.FineRepository;

import java.nio.file.Path;

/** Persists to {@code <dataDir>/fines.json}. */
public class JsonFineRepository extends JsonFileRepository<Fine, String> implements FineRepository {
    public JsonFineRepository(Path dataDir, Gson gson) {
        super(dataDir.resolve("fines.json"), gson, Fine.class, Fine::getFineId);
    }
}
