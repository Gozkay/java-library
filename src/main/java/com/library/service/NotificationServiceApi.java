package com.library.service;

import com.library.model.Notification;

import java.util.List;

public interface NotificationServiceApi {
    List<Notification> getForMember(String memberId);
    List<Notification> getUnreadForMember(String memberId);
    void markRead(String notificationId);
}
