
package com.library.service;

import com.library.model.Role;
import com.library.model.User;
import com.library.repository.UserRepository;
import com.library.util.PasswordHasher;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {

    private AuthService authService;
    private FakeUserRepository users;

    @BeforeEach
    void setUp() {
        users = new FakeUserRepository();

        String salt = PasswordHasher.newSalt();
        String hash = PasswordHasher.hash("correct123", salt);

        users.save(new User("M001", hash, salt, Role.MEMBER));

        String librarianSalt = PasswordHasher.newSalt();
        String librarianHash =
                PasswordHasher.hash("admin123", librarianSalt);

        users.save(new User(
                "L001", librarianHash, librarianSalt, Role.LIBRARIAN
        ));

        authService = new AuthService(users);
    }

    @Test
    void shouldLoginWithCorrectPassword() {
        User user = authService.login("M001", "correct123");

        assertEquals("M001", user.getUserId());
        assertTrue(authService.isLoggedIn());
    }

    @Test
    void shouldRejectIncorrectPassword() {
        assertThrows(
                AuthService.AuthenticationException.class,
                () -> authService.login("M001", "wrong123")
        );

        assertFalse(authService.isLoggedIn());
    }

    @Test
    void shouldLockAccountAfterThreeFailedAttempts() {
        for (int i = 0; i < 3; i++) {
            assertThrows(
                    AuthService.AuthenticationException.class,
                    () -> authService.login("M001", "wrong123")
            );
        }

        assertTrue(users.findById("M001").orElseThrow().isLocked());
    }

    @Test
    void shouldResetFailedAttemptsAfterSuccessfulLogin() {
        authService.login("M001", "correct123");

        assertEquals(
                0,
                users.findById("M001").orElseThrow()
                        .getFailedLoginAttempts()
        );
    }

    @Test
    void shouldAllowLoggedInLibrarianToUnlockAccount() {
        User member = users.findById("M001").orElseThrow();

        member.recordFailedLogin();
        member.recordFailedLogin();
        member.recordFailedLogin();

        users.save(member);

        authService.login("L001", "admin123");
        authService.unlockAccount("M001");

        assertFalse(
                users.findById("M001").orElseThrow().isLocked()
        );
    }

    @Test
    void shouldRejectMemberTryingToUnlockAccount() {
        authService.login("M001", "correct123");

        assertThrows(
                AuthService.AuthenticationException.class,
                () -> authService.unlockAccount("L001")
        );
    }

    @Test
    void shouldRejectUnlockWhenNobodyIsLoggedIn() {
        assertThrows(
                AuthService.AuthenticationException.class,
                () -> authService.unlockAccount("M001")
        );
    }

    // In-memory repository for testing
    private static class FakeUserRepository implements UserRepository {

        private final Map<String, User> data = new HashMap<>();

        @Override
        public Optional<User> findById(String userId) {
            return Optional.ofNullable(data.get(userId));
        }

        @Override
        public void save(User user) {
            data.put(user.getUserId(), user);
        }
    }
}
