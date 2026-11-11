package com.library.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class AtomicFileWriterTest {
    @TempDir Path dir;

    @Test void writesAndReplacesWithoutLeavingTempFiles() throws Exception {
        Path f = dir.resolve("sub/items.json");
        AtomicFileWriter.write(f, "first");
        AtomicFileWriter.write(f, "second");
        assertEquals("second", Files.readString(f));
        try (Stream<Path> s = Files.list(f.getParent())) {
            assertEquals(1, s.count(), "only the real file should remain");
        }
    }

    @Test void cleansStaleTempFiles() throws Exception {
        Files.writeString(dir.resolve("loans.json.123.tmp"), "half");
        Files.writeString(dir.resolve("loans.json"), "[]");
        AtomicFileWriter.cleanStaleTempFiles(dir);
        assertFalse(Files.exists(dir.resolve("loans.json.123.tmp")));
        assertTrue(Files.exists(dir.resolve("loans.json")));
    }
}
