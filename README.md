# Console Library Management System (Java 17 + Maven + Gson)

## Build & Run
mvn test
mvn compile exec:java -Dexec.mainClass="com.library.Main"

Default librarian credentials on first run: `admin` / `admin123`

## Team Integration Guide
- **Justina (Auth, CLI, Reports, Integration):** Completed `AuthService`, `PasswordHasher`, `CommandParser`, `ConsoleUI`, `ReportService`, custom exceptions (`com.library.exception`), and `Main.java` wiring.
- **Chioma (Models, Policies, Catalogue):** Implement `Book`, `Magazine`, `DVD`, `ReferenceBook` extending `Item`, concrete `MembershipPolicy` classes, factories, and `CatalogueService`.
- **Gozie (JSON Storage & Seed Data):** Implement repository interfaces using Gson with atomic temp-file writes and seed data (20+ items, 5+ members) in `data/`.
- **Sommy (Loans, Fines, Reservations, Events):** Implement `LoanService`, `ReservationService`, `FineService`, and Observer `EventBus` (`NotificationInbox`, `ActivityLog`).
