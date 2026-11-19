# Console Library Management System

A Java 17+ command-line library system built with Maven and Gson. It has no GUI or database; the application stores its data in JSON files under `data/`.

## Build and run

Install a JDK 17+ and Maven, then run:

```shell
mvn clean test
mvn package
java -jar target/console-library-system-1.0.0.jar
```

The shaded executable JAR includes Gson. To run from Maven during development:

```shell
mvn exec:java
```

Use an isolated data directory with `-Dlibrary.data.dir=<path>`. For example:

```shell
java -Dlibrary.data.dir=demo-data -jar target/console-library-system-1.0.0.jar
```

`library.clock.fixed` can override the clock with an ISO-8601 instant for repeatable demos and tests. Do not use a fixed clock for normal operation:

```shell
java -Dlibrary.data.dir=demo-data -Dlibrary.clock.fixed=2026-11-01T10:00:00Z -jar target/console-library-system-1.0.0.jar
```

## First run

The first launch seeds 22 items across all four types, five members, and a librarian account. The sample credentials are for local demonstration only.

| User ID | Password | Role |
| --- | --- | --- |
| `admin` | `admin123` | Librarian |
| `M-1`, `M-2` | `pass123` | Student |
| `M-3`, `M-4` | `pass123` | Staff |
| `M-5` | `pass123` | Senior |

Passwords are stored as per-user salted PBKDF2-HMAC-SHA256 hashes, never as plaintext. For production use, replace the sample credentials and tune the password-hashing parameters to your deployment's security requirements.

## Commands

Run `help` after login to see commands for the current role.

**General:** `login <userId> <password>`, `logout`, `whoami`, `search [text] [--author=...] [--category=...] [--type=BOOK|MAGAZINE|DVD|REFERENCE_BOOK] [--available]`, `help`, `exit`.

**Members:** `borrow <itemId>`, `return <copyId>`, `renew <loanId>`, `reserve <itemId>`, `collect <reservationId>`, `cancel <reservationId>`, `my loans`, `my history`, `my reservations`, `my fines`, `inbox`.

**Librarians:** `item add <type> "<title>" [--category=...] [--year=...] [--copies=...]` (also `--author`, `--isbn`, `--publisher`, `--pages`, `--issue`, `--month`, `--director`, `--runtime` as appropriate); `item edit <itemId> [--title=...] [--category=...] [--year=...]`; `item info <itemId>`; `item retire <itemId>`; `copy add <itemId> <count>`; `member add "<name>" <STUDENT|STAFF|SENIOR> [password]`; `member list`; `member suspend|activate <memberId>`; `member unlock <userId>`; `borrow <memberId> <itemId>`; `return <copyId>`; `renew <loanId>`; `lost <loanId>`; `reserve <memberId> <itemId>`; `cancel <reservationId>`; `fines <memberId>`; `fine pay <fineId> <amount>`; `report overdue`; `report popular [N] [--from=YYYY-MM-DD] [--to=YYYY-MM-DD]`; `report unpaid`; `report unused`; `report member <memberId>`; `report summary`.

Quoted arguments preserve spaces in names and titles. Invalid commands and business-rule violations are reported without terminating the interactive session.

## Design and data

- **Models and policies:** item inheritance (`Book`, `Magazine`, `DVD`, `ReferenceBook`) and membership strategies (`StudentPolicy`, `StaffPolicy`, `SeniorPolicy`) define loan terms.
- **Services and repositories:** the CLI delegates application operations to services; services use repository interfaces. JSON and in-memory repositories share the same interfaces.
- **Factories:** `ItemFactory` and `MembershipPolicyFactory` reconstruct and create domain types.
- **Fine strategy:** `DailyRateFineStrategy` calculates overdue amounts using a member's policy and `BigDecimal`. Lost items use a fixed replacement fee of 1000.00.
- **Events:** `EventBus` publishes events to `NotificationInbox` and `ActivityLog` observers. `data/activity.log` is append-only.
- **Persistence:** each JSON collection is loaded on startup and saved after changes using a temporary file and atomic replacement. Missing files are initialized; corrupt files stop startup with an error.
- **Dates and concurrency:** services receive an injected `Clock`; multi-step loan operations are synchronized.
- **IDs:** generated IDs advance past existing records on restart.

The JSON files are `users.json`, `items.json`, `loans.json`, `reservations.json`, `fines.json`, and `notifications.json`. The `data/` directory is ignored by Git so local library data is not submitted.

See [the class diagram](docs/CLASS_DIAGRAM.md), [the demo plan](docs/DEMO_PLAN.md), and the [repository contract](docs/REPOSITORY_CONTRACT.md).
