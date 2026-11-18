package com.library.repository;

import com.google.gson.Gson;
import com.library.model.*;
import com.library.repository.json.*;
import com.library.repository.memory.*;
import com.library.util.AtomicFileWriter;
import com.library.util.IdGenerator;

import java.nio.file.Path;

/**
 * Single place that knows which repository implementation is in use. Everything else (services, CLI)
 * receives the interfaces from here.
 *
 * <pre>
 *   DataContext ctx = DataContext.json(Path.of("data"));   // real app: files in data/
 *   DataContext test = DataContext.inMemory();             // tests / week-1 development
 *   LoanService loans = new LoanService(ctx.loans(), ctx.items(), ..., ctx.ids(), clock);
 * </pre>
 *
 * Building a JSON context loads every file, then moves the ID counters past the highest ID on disk,
 * so new IDs continue after a restart.
 */
public final class DataContext {
    private final UserRepository users;
    private final ItemRepository items;
    private final LoanRepository loans;
    private final ReservationRepository reservations;
    private final FineRepository fines;
    private final NotificationRepository notifications;
    private final IdGenerator ids = new IdGenerator();

    private DataContext(UserRepository u, ItemRepository i, LoanRepository l,
                        ReservationRepository r, FineRepository f, NotificationRepository n) {
        this.users = u; this.items = i; this.loans = l;
        this.reservations = r; this.fines = f; this.notifications = n;
        syncIds();
    }

    /** Loads (or creates) the JSON files in {@code dataDir}. Throws DataCorruptionException on a bad file. */
    public static DataContext json(Path dataDir) {
        AtomicFileWriter.cleanStaleTempFiles(dataDir);
        Gson gson = GsonFactory.create();
        return new DataContext(
                new JsonUserRepository(dataDir, gson),
                new JsonItemRepository(dataDir, gson),
                new JsonLoanRepository(dataDir, gson),
                new JsonReservationRepository(dataDir, gson),
                new JsonFineRepository(dataDir, gson),
                new JsonNotificationRepository(dataDir, gson));
    }

    public static DataContext inMemory() {
        return new DataContext(new InMemoryUserRepository(), new InMemoryItemRepository(), new InMemoryLoanRepository(),
                new InMemoryReservationRepository(), new InMemoryFineRepository(), new InMemoryNotificationRepository());
    }

    private void syncIds() {
        users.findAll().forEach(u -> ids.observe(u.getUserId()));
        items.findAll().forEach(i -> ids.observe(i.getItemId()));
        loans.findAll().forEach(l -> ids.observe(l.getLoanId()));
        reservations.findAll().forEach(r -> ids.observe(r.getReservationId()));
        fines.findAll().forEach(f -> ids.observe(f.getFineId()));
        notifications.findAll().forEach(n -> ids.observe(n.getNotificationId()));
    }

    public UserRepository users() { return users; }
    public ItemRepository items() { return items; }
    public LoanRepository loans() { return loans; }
    public ReservationRepository reservations() { return reservations; }
    public FineRepository fines() { return fines; }
    public NotificationRepository notifications() { return notifications; }
    public IdGenerator ids() { return ids; }
}
