
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
import com.library.event.EventBus;
import com.library.event.LibraryEvent;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

public class LoanService {

    private static final BigDecimal FINE_BLOCK_THRESHOLD =
            new BigDecimal("1000.00");

    private final LoanRepository loans;
    private final ItemRepository items;
    private final UserRepository users;
    private final FineRepository fines;
    private final ReservationRepository reservations;

    private final MembershipPolicyFactory policyFactory;
    private final IdGenerator ids;
    private final Clock clock;

    private final FineService fineService;
    private final ReservationService reservationService;
    private final EventBus eventBus;

    public LoanService(
            LoanRepository loans,
            ItemRepository items,
            UserRepository users,
            FineRepository fines,
            ReservationRepository reservations,
            MembershipPolicyFactory policyFactory,
            IdGenerator ids,
            Clock clock,
            FineService fineService,
            ReservationService reservationService,
            EventBus eventBus
    ) {
        this.loans = Objects.requireNonNull(loans);
        this.items = Objects.requireNonNull(items);
        this.users = Objects.requireNonNull(users);
        this.fines = Objects.requireNonNull(fines);
        this.reservations = Objects.requireNonNull(reservations);

        this.policyFactory = Objects.requireNonNull(policyFactory);
        this.ids = Objects.requireNonNull(ids);
        this.clock = Objects.requireNonNull(clock);

        this.fineService = Objects.requireNonNull(fineService);
        this.reservationService = Objects.requireNonNull(reservationService);
        this.eventBus = Objects.requireNonNull(eventBus);
    }

    /**
     * Borrows an available item for a library member.
     */
    public synchronized Loan borrowItem(
            String memberId,
            String itemId
    ) {

        Member member = users.findMemberById(memberId)
                .orElseThrow(() ->
                        new MemberNotFoundException(
                                "Member not found: " + memberId
                        )
                );

        if (!member.isActive()) {
            throw new IllegalStateException(
                    "This member account cannot borrow items."
            );
        }

        Item item = items.findById(itemId)
                .orElseThrow(() ->
                        new ItemNotFoundException(
                                "Item not found: " + itemId
                        )
                );

        if (!item.isBorrowable()) {
            throw new ItemNotBorrowableException(
                    "This item cannot be borrowed."
            );
        }

        MembershipPolicy policy =
                policyFactory.createPolicy(member.getMembershipType());

        int activeLoans =
                loans.findActiveByMemberId(memberId).size();

        if (activeLoans >= policy.getLoanLimit()) {
            throw new LoanLimitExceededException(
                    "Member has reached their borrowing limit."
            );
        }

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

        Copy copy = item.findAvailableCopy()
                .orElseThrow(() ->
                        new NoCopyAvailableException(
                                "No available copy of this item."
                        )
                );

        LocalDate borrowDate = LocalDate.now(clock);

        int loanPeriod =
                item.getBaseLoanPeriodDays()
                + policy.getAdditionalLoanPeriodDays();

        LocalDate dueDate = borrowDate.plusDays(loanPeriod);

        String loanId = ids.next(Kind.LOAN);

        Loan loan = new Loan(
                loanId,
                itemId,
                copy.getCopyId(),
                memberId,
                borrowDate,
                dueDate
        );

        copy.moveTo(CopyStatus.ON_LOAN);

        items.save(item);
        loans.save(loan);

        return loan;
    }

    /**
     * Returns a borrowed item, calculates overdue fines
     * and processes waiting reservations.
     */
    public synchronized Loan returnItem(String loanId) {

        Loan loan = loans.findById(loanId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Loan not found: " + loanId
                        )
                );

        if (!loan.isActive()) {
            throw new IllegalStateException(
                    "This loan is already returned or marked as lost."
            );
        }

        Item item = items.findById(loan.getItemId())
                .orElseThrow(() ->
                        new ItemNotFoundException(
                                "Item not found: " + loan.getItemId()
                        )
                );

        Copy copy = item.findCopy(loan.getCopyId())
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Physical copy not found: " + loan.getCopyId()
                        )
                );

        Member member = users.findMemberById(loan.getMemberId())
                .orElseThrow(() ->
                        new MemberNotFoundException(
                                "Member not found: " + loan.getMemberId()
                        )
                );

        LocalDate today = LocalDate.now(clock);

        loan.setReturnDate(today);
        loan.setStatus(LoanStatus.RETURNED);

        copy.moveTo(CopyStatus.AVAILABLE);

        items.save(item);
        loans.save(loan);

        Fine fine = fineService.chargeLateFine(loan, member);

        if (fine != null) {

            eventBus.publish(new LibraryEvent(
                    LibraryEvent.EventType.LOAN_OVERDUE,
                    member.getUserId(),
                    item.getItemId(),
                    loan.getLoanId(),
                    "Your loan is overdue.",
                    LocalDateTime.now(clock)
            ));

            eventBus.publish(new LibraryEvent(
                    LibraryEvent.EventType.FINE_CHARGED,
                    member.getUserId(),
                    item.getItemId(),
                    loan.getLoanId(),
                    "An overdue fine of " + fine.getAmount()
                            + " has been charged.",
                    LocalDateTime.now(clock)
            ));
        }

        eventBus.publish(new LibraryEvent(
                LibraryEvent.EventType.ITEM_RETURNED,
                member.getUserId(),
                item.getItemId(),
                loan.getLoanId(),
                "The item has been returned successfully.",
                LocalDateTime.now(clock)
        ));

        reservationService.makeNextReservationReady(
                item.getItemId(),
                copy.getCopyId()
        );

        return loan;
    }

    /**
     * Renews an active loan if the member has renewals remaining,
     * the loan is not overdue and nobody is waiting for the item.
     */
    public synchronized Loan renewLoan(String loanId) {

        Loan loan = loans.findById(loanId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Loan not found: " + loanId
                        )
                );

        if (!loan.isActive()) {
            throw new IllegalStateException(
                    "Only active loans can be renewed."
            );
        }

        LocalDate today = LocalDate.now(clock);

        if (today.isAfter(loan.getDueDate())) {
            throw new IllegalStateException(
                    "Overdue loans cannot be renewed."
            );
        }

        Member member = users.findMemberById(loan.getMemberId())
                .orElseThrow(() ->
                        new MemberNotFoundException(
                                "Member not found: " + loan.getMemberId()
                        )
                );

        Item item = items.findById(loan.getItemId())
                .orElseThrow(() ->
                        new ItemNotFoundException(
                                "Item not found: " + loan.getItemId()
                        )
                );

        MembershipPolicy policy =
                policyFactory.createPolicy(member.getMembershipType());

        if (loan.getRenewalCount() >= policy.getRenewalLimit()) {
            throw new IllegalStateException(
                    "Member has reached the maximum renewal limit."
            );
        }

        boolean waitingReservation = reservations
                .findActiveQueueForItem(item.getItemId())
                .stream()
                .anyMatch(r -> r.getStatus() == ReservationStatus.WAITING);

        if (waitingReservation) {
            throw new IllegalStateException(
                    "This item has a waiting reservation and cannot be renewed."
            );
        }

        int loanPeriod =
                item.getBaseLoanPeriodDays()
                + policy.getAdditionalLoanPeriodDays();

        loan.setDueDate(loan.getDueDate().plusDays(loanPeriod));

        loan.setRenewalCount(loan.getRenewalCount() + 1);

        loans.save(loan);

        return loan;
    }

    /**
     * Marks a borrowed item as lost and charges its replacement fee.
     */
    public synchronized Loan markLost(
            String loanId,
            BigDecimal replacementFee
    ) {

        Objects.requireNonNull(replacementFee,
                "Replacement fee cannot be null.");

        if (replacementFee.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Replacement fee must be greater than zero."
            );
        }

        Loan loan = loans.findById(loanId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Loan not found: " + loanId
                        )
                );

        if (!loan.isActive()) {
            throw new IllegalStateException(
                    "Only active loans can be marked as lost."
            );
        }

        Item item = items.findById(loan.getItemId())
                .orElseThrow(() ->
                        new ItemNotFoundException(
                                "Item not found: " + loan.getItemId()
                        )
                );

        Copy copy = item.findCopy(loan.getCopyId())
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Physical copy not found: " + loan.getCopyId()
                        )
                );

        Member member = users.findMemberById(loan.getMemberId())
                .orElseThrow(() ->
                        new MemberNotFoundException(
                                "Member not found: " + loan.getMemberId()
                        )
                );

        loan.setStatus(LoanStatus.LOST);

        copy.moveTo(CopyStatus.LOST);

        items.save(item);
        loans.save(loan);

        Fine fine = fineService.chargeLostItemFine(
                loan,
                member,
                replacementFee
        );

        eventBus.publish(new LibraryEvent(
                LibraryEvent.EventType.FINE_CHARGED,
                member.getUserId(),
                item.getItemId(),
                loan.getLoanId(),
                "A replacement fine of " + fine.getAmount()
                        + " has been charged for a lost item.",
                LocalDateTime.now(clock)
        ));

        return loan;
    }
}
