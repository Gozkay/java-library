package com.library.repository;

import com.library.model.Notification;

import java.util.Comparator;
import java.util.List;

public interface NotificationRepository extends CrudRepository<Notification, String> {

    /** A member's inbox, oldest first. */
    default List<Notification> findByMemberId(String memberId) {
        return findAll().stream()
                .filter(n -> n.getMemberId().equals(memberId))
                .sorted(Comparator.comparing(Notification::getCreatedAt))
                .toList();
    }

    default List<Notification> findUnreadByMemberId(String memberId) {
        return findByMemberId(memberId).stream().filter(n -> !n.isRead()).toList();
    }
}
