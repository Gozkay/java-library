package com.library.repository.json;

import com.google.gson.Gson;
import com.library.model.Notification;
import com.library.repository.NotificationRepository;

import java.nio.file.Path;

/** Persists to {@code <dataDir>/notifications.json}. */
public class JsonNotificationRepository extends JsonFileRepository<Notification, String> implements NotificationRepository {
    public JsonNotificationRepository(Path dataDir, Gson gson) {
        super(dataDir.resolve("notifications.json"), gson, Notification.class, Notification::getNotificationId);
    }
}
