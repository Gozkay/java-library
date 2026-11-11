# Storage contract (Gozie): what Chioma, Sommy and Justina code against

Everything below is implemented and tested. **Services depend only on the interfaces in `com.library.repository`.**
Get them from one place:

```java
DataContext ctx = DataContext.json(Path.of("data"));  // real app (Main does this)
DataContext ctx = DataContext.inMemory();             // week-1 development and unit tests
SeedDataLoader.seedIfEmpty(ctx);                      // optional: 22 items, 5 members, 1 librarian

ctx.users()  ctx.items()  ctx.loans()  ctx.reservations()  ctx.fines()  ctx.notifications()  ctx.ids()
```

## Golden rules
1. **Save after every change.** Repositories return the *same objects* they hold. `copy.moveTo(ON_LOAN)` changes memory only;
   call `ctx.items().save(item)` (copies are saved through their Item). `save` = insert-or-update and writes the JSON file right away.
2. **No delete.** Loans, fines and reservations are history; items are retired (`item.retire()`), not removed.
3. **IDs:** `ctx.ids().next(Kind.LOAN)` gives `L-1, L-2, ...` and continues after a restart. Kinds: ITEM, MEMBER, LOAN, RESERVATION, FINE, NOTIFICATION.
   Copy IDs are built by the item itself (`I-3-C2`). Do not invent IDs yourself.
4. **Never call `LocalDate.now()`** in a service. Inject a `Clock` (`Main` creates it).
5. **Thread safety:** each repository method is synchronized, but a borrow is several steps (check copy, move copy, save loan).
   Sommy: wrap the whole borrow/return in one `synchronized` block or `ReentrantLock` inside LoanService.
6. Files are written one at a time, so a crash *between* two saves is possible. Save the **item first, then the loan**,
   so a crash can never leave a loan pointing at a copy that was never marked ON_LOAN.

## Repository methods
All repositories have: `save(e)`, `saveAll(list)`, `findById(id)` (Optional), `findAll()`, `existsById(id)`, `count()`.

| Repository | Extra query methods |
|---|---|
| `UserRepository` | `findAllMembers()`, `findMemberById(id)` |
| `ItemRepository` | `findByType(ItemType)` |
| `LoanRepository` | `findByMemberId`, `findActiveByMemberId`, `findByItemId`, `findActive()`, `findActiveByCopyId`, `findBorrowedBetween(from, to)` (null = open ended) |
| `ReservationRepository` | `findByItemId`, `findActiveQueueForItem(itemId)` (WAITING + READY, FIFO), `findByMemberId`, `findActiveByMemberId`, `findReady()` |
| `FineRepository` | `findByMemberId`, `findUnpaid()`, `findUnpaidByMemberId`, `findByLoanId` |
| `NotificationRepository` | `findByMemberId` (oldest first), `findUnreadByMemberId` |

Need another query? Add a `default` method to the interface (it then works for both JSON and in-memory) and tell Gozie.
`activity.log` is **not** a repository: Sommy's `ActivityLog` listener just appends lines to `data/activity.log`.

## Model fields (shared; Chioma owns the classes, change them together)
| Class | Fields |
|---|---|
| `Item` (abstract) | itemId, **type** (ItemType), title, category, publicationYear, retired, copies |
| `Book` / `ReferenceBook` | + isbn, author, publisher, pages (ReferenceBook loan period is 0) |
| `Magazine` | + issueNumber, month |
| `DVD` | + director, runtimeMinutes |
| `Copy` | copyId, status (`moveTo()` enforces the life-of-a-copy table) |
| `User` | userId, name, **role**, passwordHash, salt, failedLoginAttempts, locked |
| `Member` (extends User) | + membershipType, status (ACTIVE / SUSPENDED) |
| `Loan` | loanId, itemId, copyId, memberId, borrowDate, dueDate, returnDate, renewalCount, status (ACTIVE / RETURNED / LOST) |
| `Reservation` | reservationId, itemId, memberId, createdAt, status (WAITING / READY / COLLECTED / EXPIRED / CANCELLED), copyId, holdExpiresOn |
| `Fine` | fineId, memberId, loanId, amount (BigDecimal), reason (LATE / LOST), status (UNPAID / PAID), createdOn, paidOn |
| `Notification` | notificationId, memberId, message, createdAt, read |

**Adding a field:** just add it. Old JSON files still load (missing fields become null / 0 / false).
**Adding an Item subclass:** add its value to `ItemType` and its class to `ItemFactory.classFor(...)`, otherwise loading fails with a "corrupt" message.
Keep a private no-argument constructor in every model class (Gson uses it, so field initialisers run).

## Things Gozie drafted that belong to someone else
- `ItemFactory.classFor` (Chioma adds `create(...)`), `PasswordHasher` (Justina may harden it, keeping the same 3 signatures), the model classes and enums.
- `Item.retire()` and `CopyStatus.canMoveTo` follow brief section 4.11. Chioma / Sommy: say so if you want them different.

## Open decision for the group
- Seeded logins are `admin / admin123` and `M-1..M-5 / pass123`. `member add` (Justina / Sommy) must choose an initial password
  for new members (suggestion: the librarian types it, or it defaults to the member ID).
