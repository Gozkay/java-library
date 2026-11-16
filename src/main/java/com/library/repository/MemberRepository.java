package com.library.repository;
import com.library.model.Member;
import java.util.*;

public interface MemberRepository {
    Optional<Member> findById(String memberId);
    List<Member> findAll();
    void save(Member member);
}
