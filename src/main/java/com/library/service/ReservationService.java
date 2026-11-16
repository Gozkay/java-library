package com.library.service;

public interface ReservationService {
    void reserveItem(String memberId, String itemId);
    void cancelReservation(String memberId, String itemId);
    void processExpiredHolds();
}
