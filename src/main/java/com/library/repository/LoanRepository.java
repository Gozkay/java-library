package com.library.repository;
import com.library.model.Loan;
import java.util.*;

public interface LoanRepository {
    Optional<Loan> findById(String loanId);
    List<Loan> findAll();
    List<Loan> findByMemberId(String memberId);
    void save(Loan loan);
}
