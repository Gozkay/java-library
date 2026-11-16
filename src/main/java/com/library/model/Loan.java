package com.library.model;
import java.time.LocalDate;

public class Loan {
    private final String loanId;
    private final String memberId;
    private final String itemId;
    private final String copyId;
    private final LocalDate borrowDate;
    private LocalDate dueDate;
    private LocalDate returnDate;
    private int renewalCount;
    private boolean lost;

    public Loan(String loanId, String memberId, String itemId, String copyId, LocalDate borrowDate, LocalDate dueDate) {
        this.loanId = loanId;
        this.memberId = memberId;
        this.itemId = itemId;
        this.copyId = copyId;
        this.borrowDate = borrowDate;
        this.dueDate = dueDate;
    }

    public String getLoanId() { return loanId; }
    public String getMemberId() { return memberId; }
    public String getItemId() { return itemId; }
    public String getCopyId() { return copyId; }
    public LocalDate getBorrowDate() { return borrowDate; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public LocalDate getReturnDate() { return returnDate; }
    public void setReturnDate(LocalDate returnDate) { this.returnDate = returnDate; }
    public int getRenewalCount() { return renewalCount; }
    public void incrementRenewalCount() { this.renewalCount++; }
    public boolean isLost() { return lost; }
    public void setLost(boolean lost) { this.lost = lost; }
    public boolean isActive() { return returnDate == null && !lost; }
}
