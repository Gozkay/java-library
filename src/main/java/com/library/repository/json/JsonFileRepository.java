package com.library.repository.json;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.library.exception.DataCorruptionException;
import com.library.exception.DataStorageException;
import com.library.repository.memory.InMemoryRepository;
import com.library.util.AtomicFileWriter;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

/**
 * Keeps all entities in memory (fast reads) and rewrites the whole JSON file after EVERY state change,
 * using {@link AtomicFileWriter} (temp file, then move) so a crash can never leave a corrupt file.
 *
 * <ul>
 *   <li>Missing file: created as an empty list "[]".</li>
 *   <li>Unreadable / invalid file: {@link DataCorruptionException} with a clear message. The file is NOT touched.</li>
 *   <li>Thread safety: inherited synchronized methods; each save rewrites the file under that same lock.</li>
 * </ul>
 */
public abstract class JsonFileRepository<T, ID> extends InMemoryRepository<T, ID> {
    private final Path file;
    private final Gson gson;
    private final Type listType;

    protected JsonFileRepository(Path file, Gson gson, Class<T> elementType, Function<T, ID> idOf) {
        super(idOf);
        this.file = file;
        this.gson = gson;
        this.listType = TypeToken.getParameterized(List.class, elementType).getType();
        load(idOf);
    }

    private void load(Function<T, ID> idOf) {
        if (!Files.exists(file)) {
            AtomicFileWriter.write(file, "[]");
            return;
        }
        List<T> loaded;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            loaded = gson.fromJson(reader, listType);
        } catch (IOException e) {
            throw new DataStorageException("Could not read " + file + ": " + e.getMessage(), e);
        } catch (RuntimeException e) { // JsonParseException, bad date, unknown enum, ...
            throw corrupt(describe(e), e);
        }
        if (loaded == null) throw corrupt("the file is empty", null);
        for (T item : loaded) {
            if (item == null) throw corrupt("it contains an empty entry", null);
            ID id = idOf.apply(item);
            if (id == null) throw corrupt("an entry has no ID", null);
            if (store.put(id, item) != null) throw corrupt("duplicate ID \"" + id + "\"", null);
        }
    }

    private DataCorruptionException corrupt(String reason, Throwable cause) {
        String msg = "Data file '" + file + "' is corrupt (" + reason + "). It was left untouched. "
                + "Restore it from a backup, or delete it to start with an empty file.";
        return cause == null ? new DataCorruptionException(msg) : new DataCorruptionException(msg, cause);
    }

    private static String describe(RuntimeException e) {
        Throwable root = e;
        while (root.getCause() != null) root = root.getCause();
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }

    @Override
    protected void onChange() {
        AtomicFileWriter.write(file, gson.toJson(List.copyOf(store.values()), listType));
    }

    public Path getFile() { return file; }
}
