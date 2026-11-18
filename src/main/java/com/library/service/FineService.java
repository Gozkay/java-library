
package com.library.service;

import com.library.factory.MembershipPolicyFactory;
import com.library.model.Fine;
import com.library.model.FineReason;
import com.library.model.FineStatus;
import com.library.model.Loan;
import com.library.model.Member;
import com.library.policy.MembershipPolicy;
import com.library.repository.FineRepository;
import com.library.repository.LoanRepository;
import com.library.util.IdGenerator;
import com.library.util.IdGenerator.Kind;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

public class FineService {

    private final FineRepository fines;
    private final LoanRepository loans;
    private final IdGenerator ids;
    private final Clock clock;
    private final MembershipPolicyFactory policyFactory;

    public FineService(
            FineRepository fines,
            LoanRepository loans,
            IdGenerator ids,
            Clock clock
    ) {
        this.fines = Objects.requireNonNull(fines);
        this.loans = Objects.requireNonNull(loans);
        this.ids = Objects.requireNonNull(ids);
        this.clock = Objects.requireNonNull(clock);
        this.policyFactory = new MembershipPolicyFactory();
    }

    /**
     * Calculates the overdue fine for a loan.
     */
    public BigDecimal calculateOverdueFine(
            Loan loan,
            Member member
    ) {
        Objects.requireNonNull(loan);
        Objects.requireNonNull(member);

        LocalDate today = LocalDate.now(clock);

        if (!today.isAfter(loan.getDueDate())) {
            return BigDecimal.ZERO;
        }

        long overdueDays = ChronoUnit.DAYS.between(
                loan.getDueDate(),
                today
        );

        MembershipPolicy policy = policyFactory.createPolicy(
                member.getMembershipType()
        );

        return policy.getDailyFineRate()
                .multiply(BigDecimal.valueOf(overdueDays));
    }

    /**
     * Creates and saves an overdue fine.
     */
    public Fine chargeLateFine(
            Loan loan,
            Member member
    ) {
        BigDecimal amount = calculateOverdueFine(loan, member);

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        Fine fine = new Fine(
                ids.next(Kind.FINE),
                member.getUserId(),
                loan.getLoanId(),
                amount,
                FineReason.LATE,
                LocalDate.now(clock)
        );

        fines.save(fine);

        return fine;
    }

    /**
     * Creates a replacement fine for a lost item.
     */
    public Fine chargeLostItemFine(
            Loan loan,
            Member member,
            BigDecimal replacementFee
    ) {
        Objects.requireNonNull(loan);
        Objects.requireNonNull(member);
        Objects.requireNonNull(replacementFee);

        if (replacementFee.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Replacement fee must be greater than zero."
            );
        }

        Fine fine = new Fine(
                ids.next(Kind.FINE),
                member.getUserId(),
                loan.getLoanId(),
                replacementFee,
                FineReason.LOST,
                LocalDate.now(clock)
        );

        fines.save(fine);

        return fine;
    }

    /**
     * Marks an unpaid fine as paid.
     */
    public Fine payFine(String fineId) {

        Fine fine = fines.findById(fineId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Fine not found: " + fineId
                        )
                );

        if (!fine.isUnpaid()) {
            throw new IllegalStateException(
                    "This fine has already been paid."
            );
        }

        fine.markPaid(LocalDate.now(clock));

        fines.save(fine);

        return fine;
    }

    /**
     * Retrieves all fines belonging to a member.
     */
    public List<Fine> getMemberFines(String memberId) {
        return fines.findByMemberId(memberId);
    }

    /**
     * Retrieves unpaid fines belonging to a member.
     */
    public List<Fine> getUnpaidFines(String memberId) {
        return fines.findUnpaidByMemberId(memberId);
    }

    /**
     * Calculates the total unpaid fine amount for a member.
     */
    public BigDecimal getTotalUnpaidAmount(String memberId) {
        return getUnpaidFines(memberId)
                .stream()
                .map(Fine::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
