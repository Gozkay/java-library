package com.library.repository.memory;

import com.library.model.Fine;
import com.library.repository.FineRepository;

public class InMemoryFineRepository extends InMemoryRepository<Fine, String> implements FineRepository {
    public InMemoryFineRepository() { super(Fine::getFineId); }
}
