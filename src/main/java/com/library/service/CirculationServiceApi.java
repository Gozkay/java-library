package com.library.service;

import com.library.model.Loan;
import java.util.List;

public interface CirculationServiceApi {
    Loan borrowItem(String memberId, String itemId);
    Loan returnCopy(String copyId, String actorUserId);
    Loan renewLoan(String loanId, String actorUserId);
    Loan markLost(String loanId, String actorUserId);
    Loan collectReservation(String reservationId, String actorUserId);
    List<Loan> getMemberLoans(String memberId);
}
