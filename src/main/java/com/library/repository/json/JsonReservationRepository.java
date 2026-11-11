package com.library.repository.json;

import com.google.gson.Gson;
import com.library.model.Reservation;
import com.library.repository.ReservationRepository;

import java.nio.file.Path;

/** Persists to {@code <dataDir>/reservations.json}. */
public class JsonReservationRepository extends JsonFileRepository<Reservation, String> implements ReservationRepository {
    public JsonReservationRepository(Path dataDir, Gson gson) {
        super(dataDir.resolve("reservations.json"), gson, Reservation.class, Reservation::getReservationId);
    }
}
