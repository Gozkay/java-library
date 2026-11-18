package com.library.repository;

import com.library.model.*;
import com.library.util.IdGenerator.Kind;
import com.library.util.PasswordHasher;

import java.util.ArrayList;
import java.util.List;

/**
 * First-run seed data: a default librarian, 5 sample members and 22 catalogue items (9 books, 5 magazines,
 * 4 DVDs, 4 reference books). Each collection is seeded only if it is empty, so running this on every start-up is safe
 * and never duplicates or overwrites real data. All of it is sample data (ISBNs and runtimes are for demo use).
 */
public final class SeedDataLoader {
    public static final String LIBRARIAN_ID = "admin";
    public static final String LIBRARIAN_PASSWORD = "admin123";
    public static final String MEMBER_PASSWORD = "pass123";

    private SeedDataLoader() { }

    /** @return true if anything was seeded. */
    public static boolean seedIfEmpty(DataContext ctx) {
        boolean seeded = false;
        if (ctx.users().count() == 0) { seedUsers(ctx); seeded = true; }
        if (ctx.items().count() == 0) { seedItems(ctx); seeded = true; }
        return seeded;
    }

    private static void seedUsers(DataContext ctx) {
        List<User> users = new ArrayList<>();
        String salt = PasswordHasher.generateSalt();
        users.add(new User(LIBRARIAN_ID, "Head Librarian", Role.LIBRARIAN,
                PasswordHasher.hash(LIBRARIAN_PASSWORD, salt), salt));

        addMember(ctx, users, "Ngozi Eze", MembershipType.STUDENT);
        addMember(ctx, users, "Tunde Bello", MembershipType.STUDENT);
        addMember(ctx, users, "Amaka Okafor", MembershipType.STAFF);
        addMember(ctx, users, "Ibrahim Musa", MembershipType.STAFF);
        addMember(ctx, users, "Chief Nkechi Obi", MembershipType.SENIOR);
        ctx.users().saveAll(users);
    }

    private static void addMember(DataContext ctx, List<User> out, String name, MembershipType type) {
        String salt = PasswordHasher.generateSalt();
        out.add(new Member(ctx.ids().next(Kind.MEMBER), name, type, PasswordHasher.hash(MEMBER_PASSWORD, salt), salt));
    }

    private static void seedItems(DataContext ctx) {
        List<Item> items = new ArrayList<>();
        // ---- Books (21 days) ----
        items.add(book(ctx, "Clean Code", "Software Engineering", 2008, 3, "9780132350884", "Robert C. Martin", "Prentice Hall", 464));
        items.add(book(ctx, "Effective Java", "Software Engineering", 2018, 2, "9780134685991", "Joshua Bloch", "Addison-Wesley", 416));
        items.add(book(ctx, "Head First Java", "Software Engineering", 2005, 2, "9780596009205", "Kathy Sierra", "O'Reilly", 720));
        items.add(book(ctx, "The Pragmatic Programmer", "Software Engineering", 2019, 2, "9780135957059", "David Thomas", "Addison-Wesley", 352));
        items.add(book(ctx, "Introduction to Algorithms", "Computer Science", 2009, 1, "9780262033848", "Thomas H. Cormen", "MIT Press", 1312));
        items.add(book(ctx, "Things Fall Apart", "Fiction", 1958, 3, "9780385474542", "Chinua Achebe", "Heinemann", 209));
        items.add(book(ctx, "Half of a Yellow Sun", "Fiction", 2006, 2, "9780007200283", "Chimamanda Ngozi Adichie", "Fourth Estate", 433));
        items.add(book(ctx, "Purple Hibiscus", "Fiction", 2003, 1, "9781616202415", "Chimamanda Ngozi Adichie", "Algonquin", 307));
        items.add(book(ctx, "Atomic Habits", "Self-Help", 2018, 2, "9780735211292", "James Clear", "Avery", 320));
        // ---- Magazines (7 days) ----
        items.add(mag(ctx, "National Geographic", "Science", 2026, 2, 112, "October"));
        items.add(mag(ctx, "Time", "News", 2026, 2, 45, "October"));
        items.add(mag(ctx, "The Economist", "News", 2026, 1, 9, "October"));
        items.add(mag(ctx, "Wired", "Technology", 2026, 2, 34, "September"));
        items.add(mag(ctx, "Forbes Africa", "Business", 2026, 2, 18, "September"));
        // ---- DVDs (5 days) ----
        items.add(dvd(ctx, "Interstellar", "Sci-Fi", 2014, 2, "Christopher Nolan", 169));
        items.add(dvd(ctx, "The Social Network", "Drama", 2010, 1, "David Fincher", 120));
        items.add(dvd(ctx, "Hidden Figures", "Drama", 2016, 2, "Theodore Melfi", 127));
        items.add(dvd(ctx, "Queen of Katwe", "Drama", 2016, 1, "Mira Nair", 124));
        // ---- Reference books (in-library use only) ----
        items.add(ref(ctx, "Oxford Advanced Learner's Dictionary", "Reference", 2020, 2, "9780194798792", "Oxford University Press", "Oxford University Press", 1780));
        items.add(ref(ctx, "Encyclopaedia Britannica Ready Reference", "Reference", 2012, 1, "9781593398408", "Britannica Editors", "Britannica", 1200));
        items.add(ref(ctx, "Atlas of the World", "Reference", 2021, 1, "9780197623541", "Oxford Editors", "Oxford University Press", 416));
        items.add(ref(ctx, "Concise Medical Dictionary", "Reference", 2020, 1, "9780198836612", "Oxford Editors", "Oxford University Press", 800));
        ctx.items().saveAll(items);
    }

    private static Item book(DataContext c, String t, String cat, int y, int n, String isbn, String a, String p, int pg) {
        return new Book(c.ids().next(Kind.ITEM), t, cat, y, n, isbn, a, p, pg);
    }
    private static Item ref(DataContext c, String t, String cat, int y, int n, String isbn, String a, String p, int pg) {
        return new ReferenceBook(c.ids().next(Kind.ITEM), t, cat, y, n, isbn, a, p, pg);
    }
    private static Item mag(DataContext c, String t, String cat, int y, int n, int issue, String month) {
        return new Magazine(c.ids().next(Kind.ITEM), t, cat, y, n, issue, month);
    }
    private static Item dvd(DataContext c, String t, String cat, int y, int n, String director, int minutes) {
        return new DVD(c.ids().next(Kind.ITEM), t, cat, y, n, director, minutes);
    }
}
