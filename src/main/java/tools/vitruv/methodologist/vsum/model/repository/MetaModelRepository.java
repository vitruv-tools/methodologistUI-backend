package tools.vitruv.methodologist.vsum.model.repository;

import tools.vitruv.methodologist.vsum.model.MetaModel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data repository interface for managing MetaModel entities. Provides standard CRUD
 * operations for MetaModel persistence. Extends CrudRepository to leverage basic database
 * operations.
 */
@Repository
public interface MetaModelRepository extends CrudRepository<MetaModel, Long> {
  /**
   * Finds a metamodel by its name, ignoring case sensitivity.
   *
   * @param name the name of the metamodel to find (case-insensitive)
   * @return Optional containing the found MetaModel or empty if not found
   * @throws IllegalArgumentException if name is null or blank
   */
  Optional<MetaModel> findByNameIgnoreCase(@NotNull @NotBlank String name);

  /**
   * Finds all metamodels associated with a specific user's email.
   *
   * @param userEmail the email of the user whose metamodels to find
   * @return Optional containing the found MetaModels or empty if none found
   * @throws IllegalArgumentException if userEmail is null or blank
   */
  Optional<MetaModel> findAllByUser_email(@NotNull @NotBlank String userEmail);
}
