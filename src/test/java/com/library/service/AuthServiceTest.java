package com.library.service;

import com.library.exception.AuthenticationException;
import com.library.exception.UnauthorizedActionException;
import com.library.model.Member;
import com.library.model.MemberStatus;
import com.library.model.MembershipType;
import com.library.model.Role;
import com.library.model.User;
import com.library.repository.DataContext;
import com.library.repository.SeedDataLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {
    private DataContext ctx;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        ctx = DataContext.inMemory();
        SeedDataLoader.seedIfEmpty(ctx);
        authService = new AuthService(ctx.users(), ctx.ids());
    }

    @Test
    void seededLibrarianAndMemberCanLoginAndLogout() {
        User librarian = authService.login("admin", "admin123");
        assertEquals(Role.LIBRARIAN, librarian.getRole());
        assertTrue(authService.isLoggedIn());

        authService.logout();
        assertFalse(authService.isLoggedIn());

        User member = authService.login("M-1", "pass123");
        assertEquals(Role.MEMBER, member.getRole());
        assertInstanceOf(Member.class, member);
    }

    @Test
    void threeFailedLoginsLockAccountUntilLibrarianUnlocks() {
        assertThrows(AuthenticationException.class, () -> authService.login("M-1", "bad1"));
        assertThrows(AuthenticationException.class, () -> authService.login("M-1", "bad2"));
        assertThrows(AuthenticationException.class, () -> authService.login("M-1", "bad3"));

        User locked = ctx.users().findById("M-1").orElseThrow();
        assertTrue(locked.isLocked());
        assertEquals(3, locked.getFailedLoginAttempts());

        // Even the real password is rejected while locked
        assertThrows(AuthenticationException.class, () -> authService.login("M-1", "pass123"));

        // Librarian unlocks account
        authService.login("admin", "admin123");
        authService.unlockAccount("M-1");
        authService.logout();

        User unlocked = authService.login("M-1", "pass123");
        assertEquals("M-1", unlocked.getUserId());
        assertFalse(unlocked.isLocked());
        assertEquals(0, unlocked.getFailedLoginAttempts());
    }

    @Test
    void librarianRegistersSuspendsAndReactivatesMember() {
        assertThrows(UnauthorizedActionException.class,
                () -> authService.registerMember("Guest Try", MembershipType.STUDENT, "pw"));

        authService.login("admin", "admin123");
        Member created = authService.registerMember("Justina Dev", MembershipType.STAFF, "secret99");
        assertEquals(MembershipType.STAFF, created.getMembershipType());
        assertEquals(MemberStatus.ACTIVE, created.getStatus());

        authService.suspendMember(created.getUserId());
        assertEquals(MemberStatus.SUSPENDED, ctx.users().findMemberById(created.getUserId()).orElseThrow().getStatus());

        authService.activateMember(created.getUserId());
        assertEquals(MemberStatus.ACTIVE, ctx.users().findMemberById(created.getUserId()).orElseThrow().getStatus());
    }
}
