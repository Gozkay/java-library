package com.library.repository.memory;

import com.library.model.Reservation;
import com.library.repository.ReservationRepository;

public class InMemoryReservationRepository extends InMemoryRepository<Reservation, String> implements ReservationRepository {
    public InMemoryReservationRepository() { super(Reservation::getReservationId); }
}
