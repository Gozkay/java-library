package com.library.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Fines are separate records (UNPAID/PAID) so history is never overwritten. Money is BigDecimal, stored as a JSON string. */
public class Fine {
    private String fineId;
    private String memberId;
    private String loanId;
    private BigDecimal amount;
    private FineReason reason;
    private FineStatus status = FineStatus.UNPAID;
    private LocalDate createdOn;
    private LocalDate paidOn;

    private Fine() { } // Gson

    public Fine(String fineId, String memberId, String loanId, BigDecimal amount, FineReason reason, LocalDate createdOn) {
        this.fineId = fineId;
        this.memberId = memberId;
        this.loanId = loanId;
        this.amount = amount;
        this.reason = reason;
        this.createdOn = createdOn;
    }

    public String getFineId() { return fineId; }
    public String getMemberId() { return memberId; }
    public String getLoanId() { return loanId; }
    public BigDecimal getAmount() { return amount; }
    public FineReason getReason() { return reason; }
    public FineStatus getStatus() { return status; }
    public LocalDate getCreatedOn() { return createdOn; }
    public LocalDate getPaidOn() { return paidOn; }
    public boolean isUnpaid() { return status == FineStatus.UNPAID; }

    public void markPaid(LocalDate on) { this.status = FineStatus.PAID; this.paidOn = on; }
}
