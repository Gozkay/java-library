package com.library.service;

import com.library.model.Notification;
import com.library.repository.NotificationRepository;

import java.util.List;
import java.util.Objects;

public final class NotificationService implements NotificationServiceApi {
    private final NotificationRepository notifications;

    public NotificationService(NotificationRepository notifications) {
        this.notifications = Objects.requireNonNull(notifications, "notifications repository required");
    }

    @Override
    public List<Notification> getForMember(String memberId) {
        return notifications.findByMemberId(memberId);
    }

    @Override
    public List<Notification> getUnreadForMember(String memberId) {
        return notifications.findUnreadByMemberId(memberId);
    }

    @Override
    public void markRead(String notificationId) {
        Notification notification = notifications.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + notificationId));
        if (!notification.isRead()) {
            notification.markRead();
            notifications.save(notification);
        }
    }
}
