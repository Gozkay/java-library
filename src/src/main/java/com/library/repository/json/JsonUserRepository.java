package com.library.repository.json;

import com.google.gson.Gson;
import com.library.model.User;
import com.library.repository.UserRepository;

import java.nio.file.Path;

/** Persists to {@code <dataDir>/users.json}. */
public class JsonUserRepository extends JsonFileRepository<User, String> implements UserRepository {
    public JsonUserRepository(Path dataDir, Gson gson) {
        super(dataDir.resolve("users.json"), gson, User.class, User::getUserId);
    }
}
