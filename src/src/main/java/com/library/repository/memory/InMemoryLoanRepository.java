package com.library.repository.memory;

import com.library.model.Loan;
import com.library.repository.LoanRepository;

public class InMemoryLoanRepository extends InMemoryRepository<Loan, String> implements LoanRepository {
    public InMemoryLoanRepository() { super(Loan::getLoanId); }
}
