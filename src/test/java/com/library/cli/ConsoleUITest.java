package com.library.cli;

import com.library.model.*;
import com.library.policy.MembershipPolicy;
import com.library.repository.*;
import com.library.service.*;
import com.library.util.PasswordHasher;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ConsoleUITest {
    @Test
    void enforcesRoleBasedHelpAndHandlesInvalidCommandsGracefully() {
        Map<String, User> users = new HashMap<>();
        UserRepository userRepo = new UserRepository() {
            public Optional<User> findById(String id) { return Optional.ofNullable(users.get(id)); }
            public void save(User u) { users.put(u.getUserId(), u); }
        };
        String salt = PasswordHasher.newSalt();
        userRepo.save(new User("admin", PasswordHasher.hash("admin123", salt), salt, Role.LIBRARIAN));
        String mSalt = PasswordHasher.newSalt();
        userRepo.save(new User("M-1", PasswordHasher.hash("pass123", mSalt), mSalt, Role.MEMBER));

        AuthService auth = new AuthService(userRepo);
        ItemRepository items = new ItemRepository() { public Optional<Item> findById(String id){return Optional.empty();} public List<Item> findAll(){return List.of();} public void save(Item i){} };
        MemberRepository members = new MemberRepository() { public Optional<Member> findById(String id){return Optional.empty();} public List<Member> findAll(){return List.of();} public void save(Member m){} };
        LoanRepository loans = new LoanRepository() { public Optional<Loan> findById(String id){return Optional.empty();} public List<Loan> findAll(){return List.of();} public List<Loan> findByMemberId(String id){return List.of();} public void save(Loan l){} };
        FineRepository fines = new FineRepository() { public Optional<Fine> findById(String id){return Optional.empty();} public List<Fine> findAll(){return List.of();} public List<Fine> findByMemberId(String id){return List.of();} public void save(Fine f){} };
        java.util.function.Function<MembershipType, MembershipPolicy> policies = t -> new MembershipPolicy() {
            public int getMaxItems(){return 3;} public int getLoanExtensionDays(){return 0;} public BigDecimal getFinePerDay(){return BigDecimal.TEN;} public int getMaxRenewals(){return 1;}
        };
        ReportService reports = new ReportService(loans, items, members, fines, policies, Clock.systemUTC());
        CatalogueService cat = new CatalogueService() { public List<Item> search(String q,String a,String c,String t){return List.of();} public Item addItem(String t,String ti,Map<String,String> o){return null;} public void retireItem(String id){} public void addCopies(String id,int c){} public Item getItemById(String id){return null;} };
        LoanService loanSvc = new LoanService() { public Loan borrowItem(String m,String i){return null;} public Loan renewLoan(String m,String i){return null;} public Loan returnItem(String m,String i){return null;} public void markLoanLost(String l){} public List<Loan> getMemberLoans(String m){return List.of();} public void checkExpiries(){} };
        ReservationService resSvc = new ReservationService() { public void reserveItem(String m,String i){} public void cancelReservation(String m,String i){} public void processExpiredHolds(){} };
        FineService fineSvc = new FineService() { public List<Fine> getMemberFines(String m){return List.of();} public void recordPayment(String m,BigDecimal a){} };
        MemberService memSvc = new MemberService() { public Member addMember(String n,MembershipType t,String p){return null;} public void suspendMember(String m){} public void activateMember(String m){} public List<String> getInboxMessages(String m){return List.of();} };

        String script = "help\nlogin M-1 pass123\nhelp\nreport overdue\nlogout\nlogin admin admin123\nreport overdue\nexit\n";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream orig = System.out;
        System.setOut(new PrintStream(out));
        try {
            new ConsoleUI(auth, cat, loanSvc, resSvc, fineSvc, memSvc, reports, Clock.systemUTC(), new Scanner(script)).run();
        } finally {
            System.setOut(orig);
        }
        String output = out.toString();
        assertTrue(output.contains("Login successful! Welcome, M-1 (MEMBER)."));
        assertTrue(output.contains("Access denied: requires LIBRARIAN role."));
        assertTrue(output.contains("No overdue loans."));
    }
}
