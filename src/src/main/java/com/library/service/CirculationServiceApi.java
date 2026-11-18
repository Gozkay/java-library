package com.library.service;

import com.library.model.Loan;
import java.util.List;

public interface CirculationServiceApi {
    Loan borrowItem(String memberId, String itemId);
    Loan returnCopy(String copyId);
    Loan renewLoan(String loanId, String actorUserId);
    Loan markLost(String loanId);
    List<Loan> getMemberLoans(String memberId);
}
