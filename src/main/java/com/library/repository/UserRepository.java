package com.library.repository;
import com.library.model.User;
import java.util.Optional;
public interface UserRepository { Optional<User> findById(String userId); void save(User user); }
