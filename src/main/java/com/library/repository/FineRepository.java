package com.library.repository;
import com.library.model.Fine;
import java.util.*;

public interface FineRepository {
    Optional<Fine> findById(String fineId);
    List<Fine> findAll();
    List<Fine> findByMemberId(String memberId);
    void save(Fine fine);
}
