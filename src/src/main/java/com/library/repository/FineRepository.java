package com.library.repository;

import com.library.model.Fine;

import java.util.List;

public interface FineRepository extends CrudRepository<Fine, String> {

    default List<Fine> findByMemberId(String memberId) {
        return findAll().stream().filter(f -> f.getMemberId().equals(memberId)).toList();
    }

    default List<Fine> findUnpaid() {
        return findAll().stream().filter(Fine::isUnpaid).toList();
    }

    default List<Fine> findUnpaidByMemberId(String memberId) {
        return findAll().stream().filter(f -> f.isUnpaid() && f.getMemberId().equals(memberId)).toList();
    }

    default List<Fine> findByLoanId(String loanId) {
        return findAll().stream().filter(f -> loanId.equals(f.getLoanId())).toList();
    }
}
