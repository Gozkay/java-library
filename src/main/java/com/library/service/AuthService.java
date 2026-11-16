package com.library.service;

import com.library.exception.LibraryException;
import com.library.model.Role;
import com.library.model.User;
import com.library.repository.UserRepository;
import com.library.util.PasswordHasher;

public class AuthService {

    private final UserRepository users;
    private User currentUser;

    public AuthService(UserRepository users) {
        this.users = users;
    }

    public User login(String id, String password) {
        currentUser = null;

        User user = users.findById(id)
                .orElseThrow(() -> new AuthenticationException("Invalid user ID or password."));

        if (user.isLocked()) {
            throw new AuthenticationException("Account locked. Contact a librarian.");
        }

        if (!PasswordHasher.verify(password, user.getSalt(), user.getPasswordHash())) {
            user.recordFailedLogin();
            users.save(user);

            if (user.isLocked()) {
                throw new AuthenticationException("Three failed attempts. Account locked.");
            }
            throw new AuthenticationException("Invalid user ID or password.");
        }

        user.recordSuccessfulLogin();
        users.save(user);
        currentUser = user;
        return user;
    }

    public void logout() {
        currentUser = null;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    public void unlockAccount(String targetId) {
        if (currentUser == null || currentUser.getRole() != Role.LIBRARIAN) {
            throw new AuthenticationException("Only a logged-in librarian can unlock accounts.");
        }

        User target = users.findById(targetId)
                .orElseThrow(() -> new AuthenticationException("Account not found."));

        target.unlock();
        users.save(target);
    }

    public static class AuthenticationException extends LibraryException {
        public AuthenticationException(String message) {
            super(message);
        }
    }
}
