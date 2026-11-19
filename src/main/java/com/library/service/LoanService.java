package com.library.service;

import com.library.event.EventBus;
import com.library.event.LibraryEvent;
import com.library.exception.ItemNotBorrowableException;
import com.library.exception.ItemNotFoundException;
import com.library.exception.LoanLimitExceededException;
import com.library.exception.MemberNotFoundException;
import com.library.exception.NoCopyAvailableException;
import com.library.exception.OutstandingFinesException;
import com.library.exception.RenewalNotAllowedException;
import com.library.exception.UnauthorizedActionException;
import com.library.factory.MembershipPolicyFactory;
import com.library.model.Copy;
import com.library.model.CopyStatus;
import com.library.model.Fine;
import com.library.model.Item;
import com.library.model.Loan;
import com.library.model.LoanStatus;
import com.library.model.Member;
import com.library.model.Reservation;
import com.library.model.ReservationStatus;
import com.library.model.Role;
import com.library.model.User;
import com.library.policy.MembershipPolicy;
import com.library.repository.FineRepository;
import com.library.repository.ItemRepository;
import com.library.repository.LoanRepository;
import com.library.repository.ReservationRepository;
import com.library.repository.UserRepository;
import com.library.util.IdGenerator;
import com.library.util.IdGenerator.Kind;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public class LoanService implements CirculationServiceApi {
    private static final BigDecimal FINE_BLOCK_THRESHOLD = new BigDecimal("1000.00");
    private static final BigDecimal LOST_ITEM_REPLACEMENT_FEE = new BigDecimal("1000.00");

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

    public LoanService(LoanRepository loans, ItemRepository items, UserRepository users,
                       FineRepository fines, MembershipPolicyFactory policyFactory,
                       IdGenerator ids, Clock clock) {
        this(loans, items, users, fines, null, policyFactory, ids, clock,
                new FineService(fines, ids, clock), null, new EventBus());
    }

    public LoanService(LoanRepository loans, ItemRepository items, UserRepository users,
                       FineRepository fines, ReservationRepository reservations,
                       MembershipPolicyFactory policyFactory, IdGenerator ids, Clock clock,
                       FineService fineService, ReservationService reservationService,
                       EventBus eventBus) {
        this.loans = Objects.requireNonNull(loans, "loans repository required");
        this.items = Objects.requireNonNull(items, "items repository required");
        this.users = Objects.requireNonNull(users, "users repository required");
        this.fines = Objects.requireNonNull(fines, "fines repository required");
        this.reservations = reservations;
        this.policyFactory = Objects.requireNonNull(policyFactory, "membership policy factory required");
        this.ids = Objects.requireNonNull(ids, "ID generator required");
        this.clock = Objects.requireNonNull(clock, "clock required");
        this.fineService = Objects.requireNonNull(fineService, "fine service required");
        this.reservationService = reservationService;
        this.eventBus = Objects.requireNonNull(eventBus, "event bus required");
    }

    @Override
    public synchronized Loan borrowItem(String memberId, String itemId) {
        Member member = findMember(memberId);
        Item item = findItem(itemId);
        requireBorrowable(item);
        if (reservations != null && reservations.findActiveQueueForItem(itemId).stream()
                .anyMatch(reservation -> reservation.getStatus() == ReservationStatus.WAITING)) {
            throw new NoCopyAvailableException("This item is reserved for a member in the waiting queue.");
        }
        Copy copy = item.findAvailableCopy()
                .orElseThrow(() -> new NoCopyAvailableException("No available copy of this item."));
        return createLoan(member, item, copy);
    }

    @Override
    public synchronized Loan returnCopy(String copyId, String actorUserId) {
        Loan loan = loans.findActiveByCopyId(copyId)
                .orElseThrow(() -> new IllegalArgumentException("No active loan found for copy: " + copyId));
        authorizeMemberOrLibrarian(actorUserId, loan.getMemberId());
        Item item = findItem(loan.getItemId());
        Copy copy = item.findCopy(copyId)
                .orElseThrow(() -> new IllegalArgumentException("Copy not found: " + copyId));
        Member member = findMember(loan.getMemberId());

        boolean hasWaitingReservation = reservations != null
                && reservations.findActiveQueueForItem(item.getItemId()).stream()
                .anyMatch(reservation -> reservation.getStatus() == ReservationStatus.WAITING);
        copy.moveTo(hasWaitingReservation ? CopyStatus.ON_HOLD : CopyStatus.AVAILABLE);
        loan.setReturnDate(LocalDate.now(clock));
        loan.setStatus(LoanStatus.RETURNED);
        items.save(item);
        loans.save(loan);

        Fine lateFine = fineService.chargeLateFine(loan, member);
        if (hasWaitingReservation && reservationService != null) {
            reservationService.makeNextReservationReady(item.getItemId(), copyId);
        }
        if (lateFine != null) {
            publish(LibraryEvent.EventType.FINE_CHARGED, member.getUserId(), item.getItemId(),
                    loan.getLoanId(), "A late-return fine of " + lateFine.getAmount() + " was charged.");
        }
        publish(LibraryEvent.EventType.ITEM_RETURNED, member.getUserId(), item.getItemId(),
                loan.getLoanId(), "Item " + item.getTitle() + " was returned.");
        return loan;
    }

    @Override
    public synchronized Loan renewLoan(String loanId, String actorUserId) {
        Loan loan = findActiveLoan(loanId);
        authorizeMemberOrLibrarian(actorUserId, loan.getMemberId());
        if (LocalDate.now(clock).isAfter(loan.getDueDate())) {
            throw new RenewalNotAllowedException("An overdue loan cannot be renewed.");
        }
        Item item = findItem(loan.getItemId());
        Member member = findMember(loan.getMemberId());
        MembershipPolicy policy = policyFactory.createPolicy(member.getMembershipType());
        if (loan.getRenewalCount() >= policy.getRenewalLimit()) {
            throw new RenewalNotAllowedException("The renewal limit has been reached.");
        }
        if (reservations != null && reservations.findActiveQueueForItem(loan.getItemId()).stream()
                .anyMatch(reservation -> reservation.getStatus() == ReservationStatus.WAITING)) {
            throw new RenewalNotAllowedException("This item has a waiting reservation.");
        }

        loan.setDueDate(loan.getDueDate().plusDays(
                item.getBaseLoanPeriodDays() + policy.getAdditionalLoanPeriodDays()));
        loan.setRenewalCount(loan.getRenewalCount() + 1);
        loans.save(loan);
        return loan;
    }

    @Override
    public synchronized Loan markLost(String loanId, String actorUserId) {
        User actor = users.findById(actorUserId)
                .orElseThrow(() -> new UnauthorizedActionException("Account not found: " + actorUserId));
        if (actor.getRole() != Role.LIBRARIAN) {
            throw new UnauthorizedActionException("Only a librarian can mark an item lost.");
        }
        Loan loan = findActiveLoan(loanId);
        Item item = findItem(loan.getItemId());
        Member member = findMember(loan.getMemberId());
        Copy copy = item.findCopy(loan.getCopyId())
                .orElseThrow(() -> new IllegalArgumentException("Copy not found: " + loan.getCopyId()));

        copy.moveTo(CopyStatus.LOST);
        loan.setStatus(LoanStatus.LOST);
        loan.setReturnDate(LocalDate.now(clock));
        items.save(item);
        loans.save(loan);

        Fine fine = fineService.chargeLostItemFine(loan, member, LOST_ITEM_REPLACEMENT_FEE);
        publish(LibraryEvent.EventType.FINE_CHARGED, member.getUserId(), item.getItemId(),
                loan.getLoanId(), "A lost-item replacement fine of " + fine.getAmount() + " was charged.");
        return loan;
    }

    @Override
    public synchronized Loan collectReservation(String reservationId, String actorUserId) {
        if (reservations == null) {
            throw new IllegalStateException("Reservation repository is not configured.");
        }
        Reservation reservation = reservations.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found: " + reservationId));
        authorizeMemberOrLibrarian(actorUserId, reservation.getMemberId());
        if (reservation.getStatus() != ReservationStatus.READY) {
            throw new IllegalArgumentException("This reservation is not ready for collection.");
        }
        if (reservation.getHoldExpiresOn() == null
                || LocalDate.now(clock).isAfter(reservation.getHoldExpiresOn())) {
            if (reservationService != null) {
                reservationService.expireReservations();
            }
            throw new IllegalArgumentException("This reservation has expired.");
        }

        Member member = findMember(reservation.getMemberId());
        Item item = findItem(reservation.getItemId());
        requireBorrowable(item);
        Copy copy = item.findCopy(reservation.getCopyId())
                .orElseThrow(() -> new IllegalArgumentException("Reserved copy not found."));
        if (copy.getStatus() != CopyStatus.ON_HOLD) {
            throw new IllegalStateException("The reserved copy is no longer on hold.");
        }
        checkBorrowingEligibility(member);

        MembershipPolicy policy = policyFactory.createPolicy(member.getMembershipType());
        LocalDate borrowDate = LocalDate.now(clock);
        Loan loan = new Loan(ids.next(Kind.LOAN), item.getItemId(), copy.getCopyId(),
                member.getUserId(), borrowDate,
                borrowDate.plusDays(item.getBaseLoanPeriodDays() + policy.getAdditionalLoanPeriodDays()));
        copy.moveTo(CopyStatus.ON_LOAN);
        reservation.setStatus(ReservationStatus.COLLECTED);
        items.save(item);
        loans.save(loan);
        reservations.save(reservation);
        return loan;
    }

    @Override
    public List<Loan> getMemberLoans(String memberId) {
        return loans.findByMemberId(memberId);
    }

    private Loan createLoan(Member member, Item item, Copy copy) {
        checkBorrowingEligibility(member);
        MembershipPolicy policy = policyFactory.createPolicy(member.getMembershipType());
        LocalDate borrowDate = LocalDate.now(clock);
        Loan loan = new Loan(ids.next(Kind.LOAN), item.getItemId(), copy.getCopyId(),
                member.getUserId(), borrowDate,
                borrowDate.plusDays(item.getBaseLoanPeriodDays() + policy.getAdditionalLoanPeriodDays()));
        copy.moveTo(CopyStatus.ON_LOAN);
        items.save(item);
        loans.save(loan);
        return loan;
    }

    private void checkBorrowingEligibility(Member member) {
        if (!member.isActive() || member.isLocked()) {
            throw new IllegalStateException("This member account cannot borrow items.");
        }
        MembershipPolicy policy = policyFactory.createPolicy(member.getMembershipType());
        if (loans.findActiveByMemberId(member.getUserId()).size() >= policy.getLoanLimit()) {
            throw new LoanLimitExceededException("Member has reached their borrowing limit.");
        }
        BigDecimal unpaidTotal = fines.findUnpaidByMemberId(member.getUserId()).stream()
                .map(Fine::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (unpaidTotal.compareTo(FINE_BLOCK_THRESHOLD) > 0) {
            throw new OutstandingFinesException("Outstanding fines exceed the borrowing limit.");
        }
    }

    private void authorizeMemberOrLibrarian(String actorUserId, String memberId) {
        User actor = users.findById(actorUserId)
                .orElseThrow(() -> new UnauthorizedActionException("Account not found: " + actorUserId));
        if (actor.getRole() != Role.LIBRARIAN && !actor.getUserId().equals(memberId)) {
            throw new UnauthorizedActionException("You cannot perform this action for another member.");
        }
    }

    private Member findMember(String memberId) {
        return users.findMemberById(memberId)
                .orElseThrow(() -> new MemberNotFoundException("Member not found: " + memberId));
    }

    private Item findItem(String itemId) {
        return items.findById(itemId)
                .orElseThrow(() -> new ItemNotFoundException("Item not found: " + itemId));
    }

    private void requireBorrowable(Item item) {
        if (!item.isBorrowable()) {
            throw new ItemNotBorrowableException("This item cannot be borrowed.");
        }
    }

    private Loan findActiveLoan(String loanId) {
        Loan loan = loans.findById(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Loan not found: " + loanId));
        if (!loan.isActive()) {
            throw new IllegalArgumentException("Loan is not active: " + loanId);
        }
        return loan;
    }

    private void publish(LibraryEvent.EventType type, String memberId, String itemId,
                         String loanId, String message) {
        eventBus.publish(new LibraryEvent(type, memberId, itemId, loanId, message, LocalDateTime.now(clock)));
    }
}
