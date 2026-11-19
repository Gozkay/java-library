
package com.library.event;

import com.library.model.Notification;
import com.library.repository.DataContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EventSystemTest {

    @TempDir
    Path tempDir;

    private DataContext ctx;
    private LocalDateTime eventTime;
    private LibraryEvent event;

    @BeforeEach
    void setUp() {
        ctx = DataContext.inMemory();

        eventTime = LocalDateTime.of(
                2026, 11, 19, 9, 0
        );

        event = new LibraryEvent(
                LibraryEvent.EventType.ITEM_RETURNED,
                "M-1",
                "I-1",
                "L-1",
                "Book returned successfully",
                eventTime
        );
    }

    // LIBRARY EVENT TESTS

    @Test
    void libraryEventStoresAllDetails() {
        assertEquals(
                LibraryEvent.EventType.ITEM_RETURNED,
                event.type()
        );
        assertEquals("M-1", event.memberId());
        assertEquals("I-1", event.itemId());
        assertEquals("L-1", event.loanId());
        assertEquals("Book returned successfully", event.message());
        assertEquals(eventTime, event.occurredAt());
    }

    @Test
    void libraryEventRejectsNullType() {
        assertThrows(
                NullPointerException.class,
                () -> new LibraryEvent(
                        null, "M-1", "I-1", "L-1",
                        "Test", eventTime
                )
        );
    }

    @Test
    void libraryEventRejectsNullMessage() {
        assertThrows(
                NullPointerException.class,
                () -> new LibraryEvent(
                        LibraryEvent.EventType.ITEM_RETURNED,
                        "M-1", "I-1", "L-1",
                        null, eventTime
                )
        );
    }

    // EVENT BUS TESTS

    @Test
    void eventBusDeliversEventToSubscriber() {
        EventBus bus = new EventBus();
        List<LibraryEvent> received = new ArrayList<>();

        bus.subscribe(received::add);
        bus.publish(event);

        assertEquals(1, received.size());
        assertEquals(event, received.get(0));
    }

    @Test
    void eventBusDeliversEventToAllSubscribers() {
        EventBus bus = new EventBus();
        AtomicInteger counter = new AtomicInteger();

        bus.subscribe(e -> counter.incrementAndGet());
        bus.subscribe(e -> counter.incrementAndGet());
        bus.subscribe(e -> counter.incrementAndGet());

        bus.publish(event);

        assertEquals(3, counter.get());
    }

    @Test
    void unsubscribedListenerDoesNotReceiveEvents() {
        EventBus bus = new EventBus();
        AtomicInteger counter = new AtomicInteger();

        java.util.function.Consumer<LibraryEvent> listener =
                e -> counter.incrementAndGet();

        bus.subscribe(listener);
        bus.unsubscribe(listener);
        bus.publish(event);

        assertEquals(0, counter.get());
    }

    @Test
    void eventBusRejectsNullEvent() {
        EventBus bus = new EventBus();

        assertThrows(
                IllegalArgumentException.class,
                () -> bus.publish(null)
        );
    }

    // NOTIFICATION INBOX TESTS

    @Test
    void notificationInboxSavesMemberNotification() {
        NotificationInbox inbox = new NotificationInbox(
                ctx.notifications(),
                ctx.ids()
        );

        inbox.accept(event);

        List<Notification> notifications =
                ctx.notifications().findByMemberId("M-1");

        assertEquals(1, notifications.size());
        assertEquals(
                "Book returned successfully",
                notifications.get(0).getMessage()
        );
    }

    @Test
    void notificationInboxIgnoresEventsWithoutMember() {
        NotificationInbox inbox = new NotificationInbox(
                ctx.notifications(),
                ctx.ids()
        );

        LibraryEvent eventWithoutMember = new LibraryEvent(
                LibraryEvent.EventType.ITEM_RETURNED,
                null,
                "I-1",
                "L-1",
                "Book returned",
                eventTime
        );

        inbox.accept(eventWithoutMember);

        assertTrue(
                ctx.notifications().findByMemberId("M-1").isEmpty()
        );
    }

    // ACTIVITY LOG TESTS

    @Test
    void activityLogCreatesLogFile() throws Exception {
        ActivityLog log = new ActivityLog(tempDir);

        log.accept(event);

        Path logFile = tempDir.resolve("activity.log");

        assertTrue(Files.exists(logFile));
    }

    @Test
    void activityLogRecordsEventDetails() throws Exception {
        ActivityLog log = new ActivityLog(tempDir);

        log.accept(event);

        String content = Files.readString(
                tempDir.resolve("activity.log")
        );

        assertTrue(content.contains("ITEM_RETURNED"));
        assertTrue(content.contains("M-1"));
        assertTrue(content.contains("I-1"));
        assertTrue(content.contains("L-1"));
        assertTrue(content.contains("Book returned successfully"));
    }

    @Test
    void activityLogAppendsMultipleEvents() throws Exception {
        ActivityLog log = new ActivityLog(tempDir);

        log.accept(event);

        LibraryEvent secondEvent = new LibraryEvent(
                LibraryEvent.EventType.FINE_CHARGED,
                "M-2",
                "I-2",
                "L-2",
                "Fine charged",
                eventTime.plusMinutes(10)
        );

        log.accept(secondEvent);

        String content = Files.readString(
                tempDir.resolve("activity.log")
        );

        assertTrue(content.contains("ITEM_RETURNED"));
        assertTrue(content.contains("FINE_CHARGED"));

        assertEquals(
                2,
                content.lines().count()
        );
    }
}
