package com.library.service;

import com.library.model.Fine;
import com.library.model.FineReason;
import com.library.model.Item;
import com.library.model.Loan;
import com.library.model.LoanStatus;
import com.library.repository.DataContext;
import com.library.repository.SeedDataLoader;
import com.library.service.ReportService.CollectionSummary;
import com.library.service.ReportService.OverdueEntry;
import com.library.service.ReportService.PopularItemEntry;
import com.library.util.IdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReportServiceTest {
    private DataContext ctx;
    private Clock fixedClock;
    private ReportService reportService;

    @BeforeEach
    void setUp() {
        ctx = DataContext.inMemory();
        SeedDataLoader.seedIfEmpty(ctx);
        fixedClock = Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneOffset.UTC);
        reportService = new ReportService(ctx, fixedClock);
    }

    @Test
    void overdueLoansAndPopularItemsAreComputedAccurately() {
        LocalDate today = LocalDate.now(fixedClock);

        // Create an overdue loan (due 5 days ago) on I-1
        Loan overdueLoan = new Loan(
                ctx.ids().next(IdGenerator.Kind.LOAN), "I-1", "I-1-C1", "M-1",
                today.minusDays(19), today.minusDays(5));
        ctx.loans().save(overdueLoan);

        // Create a returned loan on I-1 (so I-1 has 2 total borrows) and 1 active on-time loan on I-2
        Loan returnedLoan = new Loan(
                ctx.ids().next(IdGenerator.Kind.LOAN), "I-1", "I-1-C2", "M-2",
                today.minusDays(30), today.minusDays(16));
        returnedLoan.setStatus(LoanStatus.RETURNED);
        returnedLoan.setReturnDate(today.minusDays(17));
        ctx.loans().save(returnedLoan);

        Loan activeOnTime = new Loan(
                ctx.ids().next(IdGenerator.Kind.LOAN), "I-2", "I-2-C1", "M-1",
                today.minusDays(2), today.plusDays(12));
        ctx.loans().save(activeOnTime);

        List<OverdueEntry> overdue = reportService.getOverdueLoans();
        assertEquals(1, overdue.size());
        assertEquals("I-1", overdue.get(0).itemId());
        assertEquals(5L, overdue.get(0).daysOverdue());

        List<PopularItemEntry> popular = reportService.getPopularItems(2);
        assertEquals(2, popular.size());
        assertEquals("I-1", popular.get(0).itemId());
        assertEquals(2L, popular.get(0).borrowCount());
    }

    @Test
    void collectionSummaryAggregatesTitlesCopiesLoansAndFines() {
        LocalDate today = LocalDate.now(fixedClock);
        Fine unpaid = new Fine(ctx.ids().next(IdGenerator.Kind.FINE), "M-1", "L-1",
                new BigDecimal("2.50"), FineReason.LATE, today);
        Fine paid = new Fine(ctx.ids().next(IdGenerator.Kind.FINE), "M-2", "L-2",
                new BigDecimal("4.00"), FineReason.LATE, today.minusDays(3));
        paid.markPaid(today);
        ctx.fines().save(unpaid);
        ctx.fines().save(paid);

        Item firstItem = ctx.items().findById("I-1").orElseThrow();
        firstItem.retire();
        ctx.items().save(firstItem);

        CollectionSummary summary = reportService.getCollectionSummary();
        assertEquals(22, summary.totalTitles());
        assertEquals(1, summary.retiredTitles());
        assertEquals(21, summary.activeTitles());
        assertEquals(new BigDecimal("2.50"), summary.totalUnpaidFines());
        assertEquals(new BigDecimal("4.00"), summary.totalCollectedFines());
    }
}
