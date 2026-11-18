package com.library.service;

import com.library.exception.AuthenticationException;
import com.library.exception.MemberNotFoundException;
import com.library.exception.UnauthorizedActionException;
import com.library.model.Member;
import com.library.model.MemberStatus;
import com.library.model.MembershipType;
import com.library.model.Role;
import com.library.model.User;
import com.library.repository.UserRepository;
import com.library.util.IdGenerator;
import com.library.util.PasswordHasher;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Handles authentication, brute-force lockout (3 failed attempts), session tracking,
 * role-based access enforcement, and member registration/account status updates.
 */
public class AuthService {
    public static final int MAX_FAILED_ATTEMPTS = 3;

    private final UserRepository users;
    private final IdGenerator ids;
    private User currentUser;

    public AuthService(UserRepository users, IdGenerator ids) {
        this.users = Objects.requireNonNull(users, "users repository required");
        this.ids = Objects.requireNonNull(ids, "id generator required");
    }

    public User login(String userId, String password) {
        if (userId == null || userId.isBlank() || password == null) {
            throw new AuthenticationException("User ID and password are required.");
        }
        User user = users.findById(userId.trim())
                .orElseThrow(() -> new AuthenticationException("Invalid user ID or password."));

        if (user.isLocked()) {
            throw new AuthenticationException("Account is locked after " + MAX_FAILED_ATTEMPTS
                    + " failed login attempts. Contact a librarian to unlock.");
        }

        if (!PasswordHasher.matches(password, user.getSalt(), user.getPasswordHash())) {
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                user.setLocked(true);
            }
            users.save(user);
            if (user.isLocked()) {
                throw new AuthenticationException("Invalid password. Account is now locked after "
                        + MAX_FAILED_ATTEMPTS + " failed attempts.");
            }
            int remaining = MAX_FAILED_ATTEMPTS - attempts;
            throw new AuthenticationException("Invalid user ID or password (" + remaining + " attempt(s) remaining).");
        }

        if (user.getFailedLoginAttempts() != 0) {
            user.setFailedLoginAttempts(0);
            users.save(user);
        }
        this.currentUser = user;
        return user;
    }

    public void logout() {
        this.currentUser = null;
    }

    public Optional<User> getCurrentUser() {
        return Optional.ofNullable(currentUser);
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    public User requireLoggedIn() {
        if (currentUser == null) {
            throw new UnauthorizedActionException("You must be logged in to perform this action.");
        }
        return currentUser;
    }

    public User requireLibrarian() {
        User user = requireLoggedIn();
        if (user.getRole() != Role.LIBRARIAN) {
            throw new UnauthorizedActionException("This command is restricted to librarians.");
        }
        return user;
    }

    public Member requireMember() {
        User user = requireLoggedIn();
        if (!(user instanceof Member member) || user.getRole() != Role.MEMBER) {
            throw new UnauthorizedActionException("This command is restricted to library members.");
        }
        return member;
    }

    public Member registerMember(String name, MembershipType membershipType, String initialPassword) {
        requireLibrarian();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Member name must not be blank.");
        }
        Objects.requireNonNull(membershipType, "Membership type is required.");
        String memberId = ids.next(IdGenerator.Kind.MEMBER);
        String effectivePassword = (initialPassword == null || initialPassword.isBlank())
                ? memberId
                : initialPassword;
        String salt = PasswordHasher.generateSalt();
        String hash = PasswordHasher.hash(effectivePassword, salt);
        Member member = new Member(memberId, name.trim(), membershipType, hash, salt);
        users.save(member);
        return member;
    }

    public Member suspendMember(String memberId) {
        requireLibrarian();
        Member member = findMemberOrThrow(memberId);
        member.setStatus(MemberStatus.SUSPENDED);
        users.save(member);
        return member;
    }

    public Member activateMember(String memberId) {
        requireLibrarian();
        Member member = findMemberOrThrow(memberId);
        member.setStatus(MemberStatus.ACTIVE);
        users.save(member);
        return member;
    }

    public User unlockAccount(String userId) {
        requireLibrarian();
        User user = users.findById(userId)
                .orElseThrow(() -> new MemberNotFoundException("No account found with ID: " + userId));
        user.setLocked(false);
        user.setFailedLoginAttempts(0);
        users.save(user);
        return user;
    }

    public List<Member> listAllMembers() {
        requireLibrarian();
        return users.findAllMembers();
    }

    public Member getMember(String memberId) {
        return findMemberOrThrow(memberId);
    }

    private Member findMemberOrThrow(String memberId) {
        return users.findMemberById(memberId)
                .orElseThrow(() -> new MemberNotFoundException("Member not found: " + memberId));
    }
}
