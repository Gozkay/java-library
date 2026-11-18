package com.library.util;

import com.library.util.IdGenerator.Kind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class IdGeneratorTest {
    @Test void startsAtOneAndIncrements() {
        IdGenerator g = new IdGenerator();
        assertEquals("L-1", g.next(Kind.LOAN));
        assertEquals("L-2", g.next(Kind.LOAN));
        assertEquals("I-1", g.next(Kind.ITEM));
    }

    @Test void continuesAfterRestart() {
        IdGenerator g = new IdGenerator();
        g.observeAll(List.of("L-31", "L-7", "M-5", "admin", "I-12"));
        assertEquals("L-32", g.next(Kind.LOAN));
        assertEquals("M-6", g.next(Kind.MEMBER));
        assertEquals("I-13", g.next(Kind.ITEM));
    }

    @Test void numericPart() {
        assertEquals(31, IdGenerator.numericPart("L-31"));
        assertEquals(-1, IdGenerator.numericPart("admin"));
    }

    @Test void uniqueUnderConcurrency() throws Exception {
        IdGenerator g = new IdGenerator();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        Set<String> ids = ConcurrentHashMap.newKeySet();
        List<Future<?>> fs = new java.util.ArrayList<>();
        for (int t = 0; t < 8; t++) fs.add(pool.submit(() -> { for (int i = 0; i < 500; i++) ids.add(g.next(Kind.LOAN)); }));
        for (Future<?> f : fs) f.get();
        pool.shutdown();
        assertEquals(4000, ids.size());
    }
}
