
package com.library.event;

import com.library.model.Notification;
import com.library.repository.NotificationRepository;
import com.library.util.IdGenerator;
import com.library.util.IdGenerator.Kind;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Listens for library events and creates inbox notifications for members.
 */
public class NotificationInbox implements Consumer<LibraryEvent> {

    private final NotificationRepository notifications;
    private final IdGenerator ids;

    public NotificationInbox(
            NotificationRepository notifications,
            IdGenerator ids
    ) {
        this.notifications = Objects.requireNonNull(notifications);
        this.ids = Objects.requireNonNull(ids);
    }

    /**
     * Receives a library event and creates a notification for its member.
     */
    @Override
    public void accept(LibraryEvent event) {
        Objects.requireNonNull(event, "Event cannot be null");

        // Events without a member cannot generate member notifications.
        if (event.memberId() == null || event.memberId().isBlank()) {
            return;
        }

        String notificationId = ids.next(Kind.NOTIFICATION);

        Notification notification = new Notification(
                notificationId,
                event.memberId(),
                event.message(),
                event.occurredAt()
        );

        notifications.save(notification);
    }
}
