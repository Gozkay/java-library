package com.library.repository;

import com.library.model.Member;
import com.library.model.User;

import java.util.List;
import java.util.Optional;

/** Librarians and members live together in users.json (members are Users with role MEMBER). */
public interface UserRepository extends CrudRepository<User, String> {

    default List<Member> findAllMembers() {
        return findAll().stream().filter(Member.class::isInstance).map(Member.class::cast).toList();
    }

    default Optional<Member> findMemberById(String memberId) {
        return findById(memberId).filter(Member.class::isInstance).map(Member.class::cast);
    }
}
