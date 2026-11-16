package com.library.service;

import com.library.model.*;
import com.library.policy.MembershipPolicy;
import com.library.repository.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ReportServiceTest {
    private static class StubItem extends Item {
        StubItem(String id, String title) { super(id, title, "Tech", 2024); }
        public String getItemType() { return "BOOK"; }
        public int getBaseLoanDays() { return 21; }
        public List<Copy> getCopies() { return List.of(); }
    }

    @Test
    void verifiesAllFourReports() {
        var loans = List.of(new Loan("L-1", "M-1", "I-1", "C-1", LocalDate.of(2026, 10, 4), LocalDate.of(2026, 10, 25)));
        var items = List.of((Item) new StubItem("I-1", "Clean Code"), new StubItem("I-2", "Unread Tome"));
        var members = List.of(new Member("M-1", "Ada", MembershipType.STUDENT));
        var fines = List.of(new Fine("F-1", "M-1", "L-1", new BigDecimal("200"), "Late", LocalDate.of(2026, 10, 26)));

        Clock clock = Clock.fixed(Instant.parse("2026-10-30T10:00:00Z"), ZoneOffset.UTC);
        ItemRepository iRepo = new ItemRepository() { public Optional<Item> findById(String id){return Optional.empty();} public List<Item> findAll(){return items;} public void save(Item i){} };
        MemberRepository mRepo = new MemberRepository() { public Optional<Member> findById(String id){return Optional.empty();} public List<Member> findAll(){return members;} public void save(Member m){} };
        LoanRepository lRepo = new LoanRepository() { public Optional<Loan> findById(String id){return Optional.empty();} public List<Loan> findAll(){return loans;} public List<Loan> findByMemberId(String id){return List.of();} public void save(Loan l){} };
        FineRepository fRepo = new FineRepository() { public Optional<Fine> findById(String id){return Optional.empty();} public List<Fine> findAll(){return fines;} public List<Fine> findByMemberId(String id){return List.of();} public void save(Fine f){} };
        java.util.function.Function<MembershipType, MembershipPolicy> policies = t -> new MembershipPolicy() {
            public int getMaxItems(){return 3;} public int getLoanExtensionDays(){return 0;} public BigDecimal getFinePerDay(){return new BigDecimal("50");} public int getMaxRenewals(){return 1;}
        };

        ReportService service = new ReportService(lRepo, iRepo, mRepo, fRepo, policies, clock);
        assertEquals(new BigDecimal("250"), service.getOverdueLoans().get(0).estimatedFine());
        assertEquals("I-1", service.getTopBorrowedItems(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), 5).get(0).itemId());
        assertEquals(new BigDecimal("200"), service.getMembersWithUnpaidFines().get(0).totalUnpaid());
        assertEquals("I-2", service.getItemsNeverBorrowed().get(0).getItemId());
    }
}
