
package com.library.service;

import com.library.factory.MembershipPolicyFactory;
import com.library.model.Fine;
import com.library.model.FineReason;
import com.library.model.Loan;
import com.library.model.Member;
import com.library.policy.DailyRateFineStrategy;
import com.library.policy.FineStrategy;
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

public class FineService implements FineServiceApi {

    private final FineRepository fines;
    private final IdGenerator ids;
    private final Clock clock;
    private final FineStrategy fineStrategy;

    public FineService(
            FineRepository fines,
            IdGenerator ids,
            Clock clock
    ) {
        this.fines = Objects.requireNonNull(fines);
        this.ids = Objects.requireNonNull(ids);
        this.clock = Objects.requireNonNull(clock);
        this.fineStrategy = new DailyRateFineStrategy(new MembershipPolicyFactory());
    }

    public FineService(FineRepository fines, LoanRepository loans, IdGenerator ids, Clock clock) {
        this(fines, ids, clock);
        Objects.requireNonNull(loans, "loans repository required");
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

        return fineStrategy.calculate(overdueDays, member);
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

    @Override
    public Fine payFine(String fineId, BigDecimal amount) {
        Objects.requireNonNull(amount, "payment amount required");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Payment amount must be greater than zero.");
        }
        Fine fine = fines.findById(fineId)
                .orElseThrow(() -> new IllegalArgumentException("Fine not found: " + fineId));
        if (!fine.isUnpaid()) {
            throw new IllegalStateException("This fine has already been paid.");
        }
        if (fine.getAmount().compareTo(amount) != 0) {
            throw new IllegalArgumentException("Payment must equal the full fine amount of " + fine.getAmount() + ".");
        }
        return payFine(fineId);
    }

    /**
     * Retrieves all fines belonging to a member.
     */
    public List<Fine> getMemberFines(String memberId) {
        return fines.findByMemberId(memberId);
    }

    @Override
    public BigDecimal getUnpaidTotal(String memberId) {
        return getTotalUnpaidAmount(memberId);
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
