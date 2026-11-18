package com.library.model;

import java.time.LocalDateTime;

/** One inbox message for a member (written by the NotificationInbox listener, shown at login / `inbox`). */
public class Notification {
    private String notificationId;
    private String memberId;
    private String message;
    private LocalDateTime createdAt;
    private boolean read;

    private Notification() { } // Gson

    public Notification(String notificationId, String memberId, String message, LocalDateTime createdAt) {
        this.notificationId = notificationId;
        this.memberId = memberId;
        this.message = message;
        this.createdAt = createdAt;
    }

    public String getNotificationId() { return notificationId; }
    public String getMemberId() { return memberId; }
    public String getMessage() { return message; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public boolean isRead() { return read; }
    public void markRead() { this.read = true; }
}
