package com.library;

import com.library.cli.ConsoleUI;
import com.library.exception.ItemNotFoundException;
import com.library.model.*;
import com.library.policy.MembershipPolicy;
import com.library.repository.*;
import com.library.service.*;
import com.library.util.PasswordHasher;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        Clock clock = Clock.systemDefaultZone();
        Map<String, User> userStore = new LinkedHashMap<>();
        UserRepository userRepository = new UserRepository() {
            public Optional<User> findById(String userId) { return Optional.ofNullable(userStore.get(userId)); }
            public void save(User user) { userStore.put(user.getUserId(), user); }
        };
        String adminSalt = PasswordHasher.newSalt();
        userRepository.save(new User("admin", PasswordHasher.hash("admin123", adminSalt), adminSalt, Role.LIBRARIAN));

        List<Item> itemStore = new ArrayList<>();
        ItemRepository itemRepository = new ItemRepository() {
            public Optional<Item> findById(String id) { return itemStore.stream().filter(i -> i.getItemId().equals(id)).findFirst(); }
            public List<Item> findAll() { return List.copyOf(itemStore); }
            public void save(Item item) { itemStore.removeIf(i -> i.getItemId().equals(item.getItemId())); itemStore.add(item); }
        };
        List<Member> memberStore = new ArrayList<>();
        MemberRepository memberRepository = new MemberRepository() {
            public Optional<Member> findById(String id) { return memberStore.stream().filter(m -> m.getMemberId().equals(id)).findFirst(); }
            public List<Member> findAll() { return List.copyOf(memberStore); }
            public void save(Member m) { memberStore.removeIf(x -> x.getMemberId().equals(m.getMemberId())); memberStore.add(m); }
        };
        List<Loan> loanStore = new ArrayList<>();
        LoanRepository loanRepository = new LoanRepository() {
            public Optional<Loan> findById(String id) { return loanStore.stream().filter(l -> l.getLoanId().equals(id)).findFirst(); }
            public List<Loan> findAll() { return List.copyOf(loanStore); }
            public List<Loan> findByMemberId(String id) { return loanStore.stream().filter(l -> l.getMemberId().equals(id)).toList(); }
            public void save(Loan l) { loanStore.removeIf(x -> x.getLoanId().equals(l.getLoanId())); loanStore.add(l); }
        };
        List<Fine> fineStore = new ArrayList<>();
        FineRepository fineRepository = new FineRepository() {
            public Optional<Fine> findById(String id) { return fineStore.stream().filter(f -> f.getFineId().equals(id)).findFirst(); }
            public List<Fine> findAll() { return List.copyOf(fineStore); }
            public List<Fine> findByMemberId(String id) { return fineStore.stream().filter(f -> f.getMemberId().equals(id)).toList(); }
            public void save(Fine f) { fineStore.removeIf(x -> x.getFineId().equals(f.getFineId())); fineStore.add(f); }
        };

        java.util.function.Function<MembershipType, MembershipPolicy> policyResolver = type -> switch (type) {
            case STUDENT -> new MembershipPolicy() { public int getMaxItems(){return 3;} public int getLoanExtensionDays(){return 0;} public BigDecimal getFinePerDay(){return new BigDecimal("50");} public int getMaxRenewals(){return 1;} };
            case STAFF -> new MembershipPolicy() { public int getMaxItems(){return 6;} public int getLoanExtensionDays(){return 7;} public BigDecimal getFinePerDay(){return new BigDecimal("30");} public int getMaxRenewals(){return 2;} };
            case SENIOR -> new MembershipPolicy() { public int getMaxItems(){return 4;} public int getLoanExtensionDays(){return 7;} public BigDecimal getFinePerDay(){return BigDecimal.ZERO;} public int getMaxRenewals(){return 2;} };
        };

        AuthService authService = new AuthService(userRepository);
        ReportService reportService = new ReportService(loanRepository, itemRepository, memberRepository, fineRepository, policyResolver, clock);
        CatalogueService catalogueService = new CatalogueService() {
            public List<Item> search(String q, String a, String c, String t) { return itemRepository.findAll(); }
            public Item addItem(String t, String title, Map<String, String> o) { throw new UnsupportedOperationException("Pending Chioma's Item subclasses."); }
            public void retireItem(String id) { Item i = getItemById(id); i.setRetired(true); itemRepository.save(i); }
            public void addCopies(String id, int c) { throw new UnsupportedOperationException("Pending Chioma's implementation."); }
            public Item getItemById(String id) { return itemRepository.findById(id).orElseThrow(() -> new ItemNotFoundException("Item not found: " + id)); }
        };
        LoanService loanService = new LoanService() {
            public Loan borrowItem(String m, String i) { throw new UnsupportedOperationException("Pending Sommy's LoanService."); }
            public Loan renewLoan(String m, String i) { throw new UnsupportedOperationException("Pending Sommy's LoanService."); }
            public Loan returnItem(String m, String i) { throw new UnsupportedOperationException("Pending Sommy's LoanService."); }
            public void markLoanLost(String l) { throw new UnsupportedOperationException("Pending Sommy's LoanService."); }
            public List<Loan> getMemberLoans(String m) { return loanRepository.findByMemberId(m); }
            public void checkExpiries() {}
        };
        ReservationService reservationService = new ReservationService() {
            public void reserveItem(String m, String i) { throw new UnsupportedOperationException("Pending Sommy's ReservationService."); }
            public void cancelReservation(String m, String i) { throw new UnsupportedOperationException("Pending Sommy's ReservationService."); }
            public void processExpiredHolds() {}
        };
        FineService fineService = new FineService() {
            public List<Fine> getMemberFines(String m) { return fineRepository.findByMemberId(m); }
            public void recordPayment(String m, BigDecimal a) { throw new UnsupportedOperationException("Pending Sommy's FineService."); }
        };
        MemberService memberService = new MemberService() {
            public Member addMember(String name, MembershipType type, String pwd) {
                String id = "M-" + (memberStore.size() + 1);
                Member m = new Member(id, name, type); memberRepository.save(m);
                String salt = PasswordHasher.newSalt();
                userRepository.save(new User(id, PasswordHasher.hash(pwd, salt), salt, Role.MEMBER));
                return m;
            }
            public void suspendMember(String id) { Member m = memberRepository.findById(id).orElseThrow(); m.setSuspended(true); memberRepository.save(m); }
            public void activateMember(String id) { Member m = memberRepository.findById(id).orElseThrow(); m.setSuspended(false); memberRepository.save(m); }
            public List<String> getInboxMessages(String id) { return List.of(); }
        };

        System.out.println("Default Librarian Login -> ID: admin | Password: admin123");
        new ConsoleUI(authService, catalogueService, loanService, reservationService, fineService, memberService, reportService, clock, new Scanner(System.in)).run();
    }
}
