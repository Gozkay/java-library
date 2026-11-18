package com.library.repository.memory;

import com.library.model.Notification;
import com.library.repository.NotificationRepository;

public class InMemoryNotificationRepository extends InMemoryRepository<Notification, String> implements NotificationRepository {
    public InMemoryNotificationRepository() { super(Notification::getNotificationId); }
}
