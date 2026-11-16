package com.library.model;
import java.math.BigDecimal;
import java.time.LocalDate;

public class Fine {
    private final String fineId;
    private final String memberId;
    private final String loanId;
    private final BigDecimal amount;
    private final String reason;
    private final LocalDate issuedDate;
    private FineStatus status;

    public Fine(String fineId, String memberId, String loanId, BigDecimal amount, String reason, LocalDate issuedDate) {
        this.fineId = fineId;
        this.memberId = memberId;
        this.loanId = loanId;
        this.amount = amount;
        this.reason = reason;
        this.issuedDate = issuedDate;
        this.status = FineStatus.UNPAID;
    }

    public String getFineId() { return fineId; }
    public String getMemberId() { return memberId; }
    public String getLoanId() { return loanId; }
    public BigDecimal getAmount() { return amount; }
    public String getReason() { return reason; }
    public LocalDate getIssuedDate() { return issuedDate; }
    public FineStatus getStatus() { return status; }
    public void setStatus(FineStatus status) { this.status = status; }
}
