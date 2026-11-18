package com.library.repository.memory;

import com.library.model.User;
import com.library.repository.UserRepository;

public class InMemoryUserRepository extends InMemoryRepository<User, String> implements UserRepository {
    public InMemoryUserRepository() { super(User::getUserId); }
}
