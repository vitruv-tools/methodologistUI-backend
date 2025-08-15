package com.vitruv.methodologist.user.model.repository;

import com.vitruv.methodologist.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for User entity operations.
 * Extends JpaRepository to provide CRUD and custom query methods.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
  Optional<User> findByIdAndRemovedAtIsNull(Long id);

  Optional<User> findByEmailIgnoreCase(String email);
}
