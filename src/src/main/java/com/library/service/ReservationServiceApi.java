package com.library.service;

import com.library.model.Reservation;
import java.util.List;

public interface ReservationServiceApi {
    Reservation placeReservation(String memberId, String itemId);
    void cancelReservation(String reservationId, String actorUserId);
    List<Reservation> getMemberReservations(String memberId);
}
