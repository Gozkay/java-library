package com.library.util;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe ID generator whose counters CONTINUE after a restart: after loading the data files,
 * {@link #observeAll} bumps each counter past the highest existing ID (loans are never deleted,
 * so the highest ID on disk is always the last one handed out).
 * IDs look like I-12, M-3, L-31, R-7, F-9, N-40. Copy IDs are derived from the item ID (I-12-C2).
 */
public final class IdGenerator {

    public enum Kind {
        ITEM("I-"), MEMBER("M-"), LOAN("L-"), RESERVATION("R-"), FINE("F-"), NOTIFICATION("N-");
        private final String prefix;
        Kind(String prefix) { this.prefix = prefix; }
        public String prefix() { return prefix; }
    }

    private final Map<Kind, AtomicLong> last = new EnumMap<>(Kind.class);

    public IdGenerator() {
        for (Kind k : Kind.values()) last.put(k, new AtomicLong(0));
    }

    public String next(Kind kind) { return kind.prefix + last.get(kind).incrementAndGet(); }

    /** Raise the matching counter so it never re-issues {@code existingId}. Unknown formats (e.g. "admin") are ignored. */
    public void observe(String existingId) {
        if (existingId == null) return;
        for (Kind k : Kind.values()) {
            if (existingId.startsWith(k.prefix)) {
                long n = numericPart(existingId);
                if (n > 0) last.get(k).accumulateAndGet(n, Math::max);
                return;
            }
        }
    }

    public void observeAll(Collection<String> ids) { ids.forEach(this::observe); }

    /** Number after the last '-' ("L-31" -> 31). Returns -1 when there is none. Handy for FIFO tie-breaks. */
    public static long numericPart(String id) {
        if (id == null) return -1;
        int dash = id.lastIndexOf('-');
        try {
            return Long.parseLong(id.substring(dash + 1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
