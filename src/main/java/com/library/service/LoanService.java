package com.library.service;
import com.library.model.Loan;
import java.util.List;

public interface LoanService {
    Loan borrowItem(String memberId, String itemId);
    Loan renewLoan(String memberId, String itemId);
    Loan returnItem(String memberId, String itemId);
    void markLoanLost(String loanId);
    List<Loan> getMemberLoans(String memberId);
    void checkExpiries();
}
