# Five-minute demo plan

This scenario uses a separate `demo-data` directory and a controllable clock so it does not touch anyone's normal library data. Build first with `mvn package`.

## Session 1 — borrow and reserve (clock: 1 November 2026)

Start the application:

```shell
java -Dlibrary.data.dir=demo-data -Dlibrary.clock.fixed=2026-11-01T10:00:00Z -jar target/console-library-system-1.0.0.jar
```

Enter these commands:

```text
login admin admin123
search "Clean Code"
logout
login M-1 pass123
borrow I-1
borrow I-1
borrow I-1
logout
login M-2 pass123
reserve I-1
logout
exit
```

This shows seeded catalogue search, student borrowing limits, and a reservation created after all copies are checked out. `Clean Code` has a 21-day loan period.

## Session 2 — late return, hold, and collection (clock: 25 November 2026)

Restart with the same data directory and a later clock:

```shell
java -Dlibrary.data.dir=demo-data -Dlibrary.clock.fixed=2026-11-25T10:00:00Z -jar target/console-library-system-1.0.0.jar
```

Enter:

```text
login admin admin123
return I-1-C1
report overdue
logout
login M-1 pass123
my fines
inbox
logout
login M-2 pass123
inbox
my reservations
collect R-1
my loans
exit
```

The first loan is three days overdue and produces a `$150.00` fine for the student. The returned copy is placed on hold for the waiting member, who receives a notification and collects it within the three-day window.

## Session 3 — verify persistence

Restart using the same clock and data directory. Log in as `M-2` and run `my loans`; the collected loan remains on disk after the restart. The JSON files and append-only `activity.log` are in `demo-data/`.
