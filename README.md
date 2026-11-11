# Console Library Management System

Java 17+ / Maven / Gson command-line library. No GUI, no database: all data lives in JSON files in `data/`.

> **Status:** storage layer, models, seed data, exceptions and project scaffold are done (Gozie).
> Services, policies, events, CLI and reports are in progress by the rest of the team. See `docs/REPOSITORY_CONTRACT.md`.

## Build, test, run
```bash
mvn test                  # runs the storage / persistence tests
mvn compile exec:java     # runs com.library.Main (creates data/ on first run)
mvn package && java -jar target/console-library-system-1.0.0.jar
```
Use another data folder with `-Dlibrary.data.dir=/path/to/data`.

## First run and default logins
On first run `data/` is created and seeded with 22 items (9 books, 5 magazines, 4 DVDs, 4 reference books) and 6 accounts.

| User ID | Password | Role |
|---|---|---|
| `admin` | `admin123` | LIBRARIAN |
| `M-1`, `M-2` | `pass123` | MEMBER (STUDENT) |
| `M-3`, `M-4` | `pass123` | MEMBER (STAFF) |
| `M-5` | `pass123` | MEMBER (SENIOR) |

Passwords are stored as SHA-256 with a per-user random salt, never in plain text.

## Project layout
```
com.library
  model/       Item, Book, Magazine, DVD, ReferenceBook, Copy, User, Member, Loan, Reservation, Fine, Notification + enums
  service/     (Sommy, Chioma, Justina)     policy/ (Chioma)     event/ (Sommy)     cli/ (Justina)
  repository/  interfaces, DataContext, SeedDataLoader
    json/      Gson + file-based implementations
    memory/    in-memory implementations (week-1 work and tests)
  factory/     ItemFactory (+ MembershipPolicyFactory, Chioma)
  exception/   custom exceptions (all extend LibraryException)
  util/        IdGenerator, PasswordHasher, AtomicFileWriter
```

## Data storage design decisions
- **One file per collection** in `data/`: `users.json`, `items.json`, `loans.json`, `reservations.json`, `fines.json`, `notifications.json`
  (plus `activity.log`, written by the ActivityLog listener).
- **Repository pattern:** services use interfaces only. `DataContext` is the single place that chooses JSON or in-memory.
- **Load at startup, save after every change.** Data is cached in memory; each `save` rewrites that collection's file.
- **Crash-safe saving:** content goes to a temporary file in the same folder, is flushed to disk, then atomically moved over the real
  file (`AtomicFileWriter`). Stale `.tmp` files left by a crash are removed at startup.
- **Missing file:** created empty. **Corrupt file** (bad JSON, empty file, unknown type, bad date, duplicate ID): start-up stops with a
  clear message and exit code 1, and the file is left untouched.
- **Polymorphic loading:** items store a `type` (BOOK, MAGAZINE, DVD, REFERENCE_BOOK) and users a `role`. A Gson adapter asks
  `ItemFactory` which subclass to rebuild, so `Book`, `ReferenceBook` and the rest come back as the right class.
- **Money** is `BigDecimal` written as a JSON string (`"200.00"`). **Dates** are ISO-8601 strings (`"2026-10-29"`).
- **IDs** (`I-12`, `M-3`, `L-31`, `R-7`, `F-9`) continue after a restart: counters move past the highest ID found on disk.
  Copy IDs are derived from the item (`I-12-C2`).
- **Thread safety:** repository methods are synchronized and the ID generator is atomic. Locking a whole multi-step borrow/return is the job of `LoanService`.

## Command list
_To be completed by Justina when the CLI is wired in (brief section 4.8)._

## UML diagram
_To be added by Justina with contributions from everyone._
