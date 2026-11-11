package com.library.model;

import java.time.LocalDate;

/** Loans are never deleted: a returned loan stays in history for reports. */
public class Loan {
    private String loanId;
    private String itemId;
    private String copyId;
    private String memberId;
    private LocalDate borrowDate;
    private LocalDate dueDate;
    private LocalDate returnDate;
    private int renewalCount;
    private LoanStatus status = LoanStatus.ACTIVE;

    private Loan() { } // Gson

    public Loan(String loanId, String itemId, String copyId, String memberId, LocalDate borrowDate, LocalDate dueDate) {
        this.loanId = loanId;
        this.itemId = itemId;
        this.copyId = copyId;
        this.memberId = memberId;
        this.borrowDate = borrowDate;
        this.dueDate = dueDate;
    }

    public String getLoanId() { return loanId; }
    public String getItemId() { return itemId; }
    public String getCopyId() { return copyId; }
    public String getMemberId() { return memberId; }
    public LocalDate getBorrowDate() { return borrowDate; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public LocalDate getReturnDate() { return returnDate; }
    public void setReturnDate(LocalDate returnDate) { this.returnDate = returnDate; }
    public int getRenewalCount() { return renewalCount; }
    public void setRenewalCount(int renewalCount) { this.renewalCount = renewalCount; }
    public LoanStatus getStatus() { return status; }
    public void setStatus(LoanStatus status) { this.status = status; }
    public boolean isActive() { return status == LoanStatus.ACTIVE; }
}
