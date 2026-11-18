package com.library.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Reservation {
    private String reservationId;
    private String itemId;
    private String memberId;
    private LocalDateTime createdAt;   // FIFO order (tie-break: numeric part of reservationId)
    private ReservationStatus status = ReservationStatus.WAITING;
    private String copyId;             // set when READY: the copy held for this member
    private LocalDate holdExpiresOn;   // set when READY: readyDate + 3 days

    private Reservation() { } // Gson

    public Reservation(String reservationId, String itemId, String memberId, LocalDateTime createdAt) {
        this.reservationId = reservationId;
        this.itemId = itemId;
        this.memberId = memberId;
        this.createdAt = createdAt;
    }

    public String getReservationId() { return reservationId; }
    public String getItemId() { return itemId; }
    public String getMemberId() { return memberId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public ReservationStatus getStatus() { return status; }
    public void setStatus(ReservationStatus status) { this.status = status; }
    public String getCopyId() { return copyId; }
    public void setCopyId(String copyId) { this.copyId = copyId; }
    public LocalDate getHoldExpiresOn() { return holdExpiresOn; }
    public void setHoldExpiresOn(LocalDate d) { this.holdExpiresOn = d; }
}
