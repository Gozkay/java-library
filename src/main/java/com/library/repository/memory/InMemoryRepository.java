package com.library.repository.memory;

import com.library.repository.CrudRepository;

import java.util.*;
import java.util.function.Function;

/**
 * Map-backed repository. Used directly for the week-1 "in-memory" milestone and in tests, and as the base class of the
 * JSON repositories (which override {@link #onChange()} to write the file). All methods are synchronized, so concurrent
 * save/find calls are safe. If {@code onChange()} fails (e.g. disk error) the in-memory map is rolled back.
 */
public class InMemoryRepository<T, ID> implements CrudRepository<T, ID> {
    protected final Map<ID, T> store = new LinkedHashMap<>();
    private final Function<T, ID> idOf;

    public InMemoryRepository(Function<T, ID> idOf) { this.idOf = idOf; }

    /** Hook called (under the lock) after every state change. JSON repositories persist here. */
    protected void onChange() { }

    @Override
    public synchronized void save(T entity) {
        Objects.requireNonNull(entity, "entity");
        saveAll(List.of(entity));
    }

    @Override
    public synchronized void saveAll(Collection<T> entities) {
        Map<ID, T> backup = new LinkedHashMap<>(store);
        try {
            for (T e : entities) store.put(idOf.apply(Objects.requireNonNull(e, "entity")), e);
            onChange();
        } catch (RuntimeException ex) {
            store.clear();
            store.putAll(backup);
            throw ex;
        }
    }

    @Override public synchronized Optional<T> findById(ID id) { return Optional.ofNullable(store.get(id)); }
    @Override public synchronized List<T> findAll() { return List.copyOf(store.values()); }
    @Override public synchronized boolean existsById(ID id) { return store.containsKey(id); }
    @Override public synchronized long count() { return store.size(); }
}
