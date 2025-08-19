package com.vitruv.methodologist.vsum.model.repository;

import com.vitruv.methodologist.user.model.User;
import com.vitruv.methodologist.vsum.model.MetaModel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data repository interface for managing MetaModel entities.
 * Provides standard CRUD operations for MetaModel persistence.
 * Extends CrudRepository to leverage basic database operations.
 */
@Repository
public interface MetaModelRepository extends CrudRepository<MetaModel, Long> {
    Optional<MetaModel> findByNameIgnoreCase(@NotNull @NotBlank String name);
    Optional<MetaModel> findAllByUser_email(@NotNull @NotBlank String userEmail);
}
