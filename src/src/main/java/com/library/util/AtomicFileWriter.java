package com.library.util;

import com.library.exception.DataStorageException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

/**
 * Crash-safe file writing: write the full content to a temporary file in the SAME folder, flush it to disk,
 * then move it over the real file. A crash at any moment leaves either the complete old file or the complete
 * new file, never a half-written one.
 */
public final class AtomicFileWriter {
    private static final ConcurrentMap<Path, ReentrantLock> WRITE_LOCKS = new ConcurrentHashMap<>();

    private AtomicFileWriter() { }

    public static void write(Path target, String content) {
        Path abs = target.toAbsolutePath();
        Path dir = abs.getParent();
        ReentrantLock lock = WRITE_LOCKS.computeIfAbsent(abs, ignored -> new ReentrantLock());
        lock.lock();
        Path tmp = null;
        try {
            Files.createDirectories(dir);
            tmp = Files.createTempFile(dir, abs.getFileName() + ".", ".tmp");
            ByteBuffer buf = ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8));
            try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                while (buf.hasRemaining()) ch.write(buf);
                ch.force(true);
            }

            for (int attempt = 1; ; attempt++) {
                try {
                    Files.move(tmp, abs, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                    return;
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tmp, abs, StandardCopyOption.REPLACE_EXISTING);
                    return;
                } catch (AccessDeniedException e) {
                    if (attempt >= 10) throw e;
                    try { Thread.sleep(25L * attempt); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                }
            }
        } catch (IOException e) {
            if (tmp != null) {
                try { Files.deleteIfExists(tmp); } catch (IOException ignored) { }
            }
            throw new DataStorageException("Could not save " + target + ": " + e.getMessage(), e);
        } finally {
            lock.unlock();
            WRITE_LOCKS.remove(abs, lock);
        }
    }

    /** Remove leftover *.tmp files from a crash during a previous save. Safe to call at startup. */
    public static void cleanStaleTempFiles(Path dir) {
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".tmp")).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }
}
