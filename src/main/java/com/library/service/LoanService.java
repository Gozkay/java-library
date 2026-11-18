
package com.library.service;

import com.library.exception.ItemNotBorrowableException;
import com.library.exception.ItemNotFoundException;
import com.library.exception.LoanLimitExceededException;
import com.library.exception.MemberNotFoundException;
import com.library.exception.NoCopyAvailableException;
import com.library.exception.OutstandingFinesException;

import com.library.factory.MembershipPolicyFactory;

import com.library.model.*;
import com.library.policy.MembershipPolicy;

import com.library.repository.*;
import com.library.util.IdGenerator;
import com.library.util.IdGenerator.Kind;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;

public class LoanService {

    private static final BigDecimal FINE_BLOCK_THRESHOLD =
            new BigDecimal("1000.00");

    private final LoanRepository loans;
    private final ItemRepository items;
    private final UserRepository users;
    private final FineRepository fines;
    private final MembershipPolicyFactory policyFactory;
    private final IdGenerator ids;
    private final Clock clock;

    public LoanService(
            LoanRepository loans,
            ItemRepository items,
            UserRepository users,
            FineRepository fines,
            MembershipPolicyFactory policyFactory,
            IdGenerator ids,
            Clock clock
    ) {
        this.loans = Objects.requireNonNull(loans);
        this.items = Objects.requireNonNull(items);
        this.users = Objects.requireNonNull(users);
        this.fines = Objects.requireNonNull(fines);
        this.policyFactory = Objects.requireNonNull(policyFactory);
        this.ids = Objects.requireNonNull(ids);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Borrows an available item for a library member.
     */
    public synchronized Loan borrowItem(
            String memberId,
            String itemId
    ) {

        // 1. Find the member.
        Member member = users.findMemberById(memberId)
                .orElseThrow(() ->
                        new MemberNotFoundException(
                                "Member not found: " + memberId
                        )
                );

        // 2. Check whether the member is active.
        if (!member.isActive() || member.isLocked()) {
            throw new IllegalStateException(
                    "This member account cannot borrow items."
            );
        }

        // 3. Find the item.
        Item item = items.findById(itemId)
                .orElseThrow(() ->
                        new ItemNotFoundException(
                                "Item not found: " + itemId
                        )
                );

        // 4. Check whether the item can be borrowed.
        if (!item.isBorrowable()) {
            throw new ItemNotBorrowableException(
                    "This item cannot be borrowed."
            );
        }

        // 5. Get the member's membership policy.
        MembershipPolicy policy =
                policyFactory.createPolicy(member.getMembershipType());

        // 6. Check the member's active loan limit.
        int activeLoans =
                loans.findActiveByMemberId(memberId).size();

        if (activeLoans >= policy.getLoanLimit()) {
            throw new LoanLimitExceededException(
                    "Member has reached their borrowing limit."
            );
        }

        // 7. Calculate the member's unpaid fines.
        BigDecimal unpaidTotal = fines
                .findUnpaidByMemberId(memberId)
                .stream()
                .map(Fine::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (unpaidTotal.compareTo(FINE_BLOCK_THRESHOLD) > 0) {
            throw new OutstandingFinesException(
                    "Outstanding fines exceed the borrowing limit."
            );
        }

        // 8. Find an available physical copy.
        Copy copy = item.findAvailableCopy()
                .orElseThrow(() ->
                        new NoCopyAvailableException(
                                "No available copy of this item."
                        )
                );

        // 9. Calculate borrowing and due dates.
        LocalDate borrowDate = LocalDate.now(clock);

        int loanPeriod =
                item.getBaseLoanPeriodDays()
                + policy.getAdditionalLoanPeriodDays();

        LocalDate dueDate = borrowDate.plusDays(loanPeriod);

        // 10. Generate a loan ID and create the loan.
        String loanId = ids.next(Kind.LOAN);

        Loan loan = new Loan(
                loanId,
                itemId,
                copy.getCopyId(),
                memberId,
                borrowDate,
                dueDate
        );

        // 11. Change the copy's status.
        copy.moveTo(CopyStatus.ON_LOAN);

        // 12. Save the item first, then the loan.
        items.save(item);
        loans.save(loan);

        return loan;
    }
}
