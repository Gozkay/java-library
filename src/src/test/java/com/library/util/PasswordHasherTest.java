package com.library.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {

    @Test
    void generateSaltProducesUniqueRandomSalts() {
        String s1 = PasswordHasher.generateSalt();
        String s2 = PasswordHasher.generateSalt();
        assertNotNull(s1);
        assertFalse(s1.isBlank());
        assertNotEquals(s1, s2);
    }

    @Test
    void hashAndMatchesVerifyCorrectPasswordAndRejectWrongOne() {
        String salt = PasswordHasher.generateSalt();
        String hash = PasswordHasher.hash("admin123", salt);

        assertEquals(64, hash.length());
        assertTrue(PasswordHasher.matches("admin123", salt, hash));
        assertFalse(PasswordHasher.matches("wrongpass", salt, hash));
        assertFalse(PasswordHasher.matches(null, salt, hash));
    }
}
