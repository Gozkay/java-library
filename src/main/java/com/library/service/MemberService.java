package com.library.service;
import com.library.model.Member;
import com.library.model.MembershipType;
import java.util.List;

public interface MemberService {
    Member addMember(String name, MembershipType type, String initialPassword);
    void suspendMember(String memberId);
    void activateMember(String memberId);
    List<String> getInboxMessages(String memberId);
}
