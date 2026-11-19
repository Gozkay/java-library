
package com.library.service;

import com.library.model.*;
import com.library.repository.DataContext;
import com.library.repository.SeedDataLoader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class FineServiceTest {

    private DataContext ctx;
    private Clock clock;
    private FineService fineService;

    @BeforeEach
    void setUp() {
        ctx = DataContext.inMemory();
        SeedDataLoader.seedIfEmpty(ctx);

        clock = Clock.fixed(
                Instant.parse("2026-11-19T10:00:00Z"),
                ZoneOffset.UTC
        );

        fineService = new FineService(
                ctx.fines(),
                ctx.loans(),
                ctx.ids(),
                clock
        );
    }

    @Test
    void overdueLoanCalculatesCorrectFine() {
        Loan loan = new Loan(
                "TEST-LOAN-1",
                "I-1",
                "I-1-C1",
                "M-1",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 11, 17)
        );

        Member member = ctx.users()
                .findMemberById("M-1")
                .orElseThrow();

        BigDecimal fine = fineService.calculateOverdueFine(loan, member);

        assertEquals(new BigDecimal("100.00"), fine);
    }

    @Test
    void loanThatIsNotOverdueHasNoFine() {
        Loan loan = new Loan(
                "TEST-LOAN-2",
                "I-1",
                "I-1-C1",
                "M-1",
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 20)
        );

        Member member = ctx.users()
                .findMemberById("M-1")
                .orElseThrow();

        BigDecimal fine = fineService.calculateOverdueFine(loan, member);

        assertEquals(BigDecimal.ZERO, fine);
    }

    @Test
    void chargingLateFineSavesFineRecord() {
        Loan loan = new Loan(
                "TEST-LOAN-3",
                "I-1",
                "I-1-C1",
                "M-1",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 11, 17)
        );

        Member member = ctx.users()
                .findMemberById("M-1")
                .orElseThrow();

        Fine fine = fineService.chargeLateFine(loan, member);

        assertNotNull(fine);
        assertEquals(new BigDecimal("100.00"), fine.getAmount());
        assertEquals(FineReason.LATE, fine.getReason());
        assertTrue(fine.isUnpaid());

        assertEquals(
                1,
                ctx.fines().findByLoanId("TEST-LOAN-3").size()
        );
    }

    @Test
    void seniorMemberDoesNotReceiveOverdueFine() {
        Loan loan = new Loan(
                "TEST-LOAN-4",
                "I-1",
                "I-1-C1",
                "M-5",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 11, 17)
        );

        Member member = ctx.users()
                .findMemberById("M-5")
                .orElseThrow();

        Fine fine = fineService.chargeLateFine(loan, member);

        assertNull(fine);
        assertTrue(ctx.fines().findByLoanId("TEST-LOAN-4").isEmpty());
    }

    @Test
    void lostItemFineUsesReplacementFee() {
        Loan loan = new Loan(
                "TEST-LOAN-5",
                "I-1",
                "I-1-C1",
                "M-1",
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 22)
        );

        Member member = ctx.users()
                .findMemberById("M-1")
                .orElseThrow();

        Fine fine = fineService.chargeLostItemFine(
                loan,
                member,
                new BigDecimal("2500.00")
        );

        assertEquals(new BigDecimal("2500.00"), fine.getAmount());
        assertEquals(FineReason.LOST, fine.getReason());
    }

    @Test
    void memberCanPayUnpaidFine() {
        Loan loan = new Loan(
                "TEST-LOAN-6",
                "I-1",
                "I-1-C1",
                "M-1",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 11, 17)
        );

        Member member = ctx.users()
                .findMemberById("M-1")
                .orElseThrow();

        Fine fine = fineService.chargeLateFine(loan, member);

        fineService.payFine(fine.getFineId());

        Fine paidFine = ctx.fines()
                .findByLoanId("TEST-LOAN-6")
                .get(0);

        assertFalse(paidFine.isUnpaid());
        assertEquals(
                LocalDate.of(2026, 11, 19),
                paidFine.getPaidOn()
        );
    }

    @Test
    void unpaidFineTotalIsCalculatedCorrectly() {
        Loan loan1 = new Loan(
                "TEST-LOAN-7",
                "I-1",
                "I-1-C1",
                "M-1",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 11, 17)
        );

        Loan loan2 = new Loan(
                "TEST-LOAN-8",
                "I-2",
                "I-2-C1",
                "M-1",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 11, 16)
        );

        Member member = ctx.users()
                .findMemberById("M-1")
                .orElseThrow();

        fineService.chargeLateFine(loan1, member);
        fineService.chargeLateFine(loan2, member);

        assertEquals(
                new BigDecimal("250.00"),
                fineService.getTotalUnpaidAmount("M-1")
        );
    }

    @Test
    void invalidReplacementFeeIsRejected() {
        Loan loan = new Loan(
                "TEST-LOAN-9",
                "I-1",
                "I-1-C1",
                "M-1",
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 22)
        );

        Member member = ctx.users()
                .findMemberById("M-1")
                .orElseThrow();

        assertThrows(
                IllegalArgumentException.class,
                () -> fineService.chargeLostItemFine(
                        loan,
                        member,
                        BigDecimal.ZERO
                )
        );
    }
}
