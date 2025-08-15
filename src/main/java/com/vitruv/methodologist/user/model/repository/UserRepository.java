package com.vitruv.methodologist.user.model.repository;

import com.vitruv.methodologist.user.model.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for accessing and managing {@link User} entities.
 * Extends {@link JpaRepository} to provide CRUD operations.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

  /**
   * Finds a user by ID if the user has not been removed.
   *
   * @param id the unique identifier of the user
   * @return an {@link Optional} containing the user if found and not removed, otherwise empty
   */
  Optional<User> findByIdAndRemovedAtIsNull(Long id);

  /**
   * Finds a user by email address, ignoring case sensitivity.
   *
   * @param email the email address to search for
   * @return an {@link Optional} containing the user if found, otherwise empty
   */
  Optional<User> findByEmailIgnoreCase(String email);
}
