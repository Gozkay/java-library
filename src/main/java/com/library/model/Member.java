package com.library.model;

public class Member extends User {
    private MembershipType membershipType;
    private MemberStatus status = MemberStatus.ACTIVE;

    protected Member() { } // Gson

    public Member(String userId, String name, MembershipType type, String passwordHash, String salt) {
        super(userId, name, Role.MEMBER, passwordHash, salt);
        this.membershipType = type;
    }

    public MembershipType getMembershipType() { return membershipType; }
    public MemberStatus getStatus() { return status; }
    public void setStatus(MemberStatus status) { this.status = status; }
    public boolean isActive() { return status == MemberStatus.ACTIVE; }
}
