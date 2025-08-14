package com.vitruv.methodologist.user.model.repository;

import com.vitruv.methodologist.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByIdAndRemovedAtIsNull(Long id);
    Optional<User> findByEmailIgnoreCase(String email);
}