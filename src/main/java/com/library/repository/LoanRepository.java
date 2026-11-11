package com.library.repository;

import com.library.model.Loan;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LoanRepository extends CrudRepository<Loan, String> {

    default List<Loan> findByMemberId(String memberId) {
        return findAll().stream().filter(l -> l.getMemberId().equals(memberId)).toList();
    }

    /** Loans the member still has out (status ACTIVE). */
    default List<Loan> findActiveByMemberId(String memberId) {
        return findAll().stream().filter(l -> l.isActive() && l.getMemberId().equals(memberId)).toList();
    }

    default List<Loan> findByItemId(String itemId) {
        return findAll().stream().filter(l -> l.getItemId().equals(itemId)).toList();
    }

    default List<Loan> findActive() {
        return findAll().stream().filter(Loan::isActive).toList();
    }

    /** The active loan of one physical copy, if any. */
    default Optional<Loan> findActiveByCopyId(String copyId) {
        return findAll().stream().filter(l -> l.isActive() && l.getCopyId().equals(copyId)).findFirst();
    }

    /** Loans borrowed in [from, to] inclusive (for "top 5 most borrowed"). Either bound may be null = open-ended. */
    default List<Loan> findBorrowedBetween(LocalDate from, LocalDate to) {
        return findAll().stream()
                .filter(l -> (from == null || !l.getBorrowDate().isBefore(from))
                          && (to == null || !l.getBorrowDate().isAfter(to)))
                .toList();
    }
}
