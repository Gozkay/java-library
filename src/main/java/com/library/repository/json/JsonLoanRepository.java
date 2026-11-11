package com.library.repository.json;

import com.google.gson.Gson;
import com.library.model.Loan;
import com.library.repository.LoanRepository;

import java.nio.file.Path;

/** Persists to {@code <dataDir>/loans.json}. */
public class JsonLoanRepository extends JsonFileRepository<Loan, String> implements LoanRepository {
    public JsonLoanRepository(Path dataDir, Gson gson) {
        super(dataDir.resolve("loans.json"), gson, Loan.class, Loan::getLoanId);
    }
}
