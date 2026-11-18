package com.library.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Minimal data-access contract. Services depend on THIS (and the entity interfaces below), never on JSON classes.
 *
 * <ul>
 *   <li>{@link #save} inserts or updates (matched by the entity's ID) and persists immediately.</li>
 *   <li>Repositories hand out the SAME object instances they hold, so after changing an entity
 *       (e.g. {@code copy.moveTo(...)}, {@code loan.setReturnDate(...)}) you MUST call {@code save(...)} again
 *       on the owning entity (the Item, the Loan...) or the change will not reach the disk.</li>
 *   <li>There is deliberately no delete: loans/fines/reservations are history, items are retired, not removed.</li>
 * </ul>
 */
public interface CrudRepository<T, ID> {
    void save(T entity);
    void saveAll(Collection<T> entities);
    Optional<T> findById(ID id);
    List<T> findAll();
    boolean existsById(ID id);
    long count();
}
