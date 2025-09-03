package tools.vitruv.methodologist.vsum.model.repository;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;
import tools.vitruv.methodologist.vsum.controller.dto.request.MetaModelFilterRequest;
import tools.vitruv.methodologist.vsum.model.MetaModel;

/**
 * This class defines specifications for filtering and querying MetaModel entities. The
 * specifications are based on various filtering criteria provided through a filter request and
 * caller-specific context.
 */
public class MetaModelSpecifications {

  /**
   * Builds a JPA {@link Specification} for filtering {@link MetaModel} entities based on the
   * provided user email and filter request.
   *
   * <p>The generated specification always restricts results to metamodels owned by the specified
   * user (by email) and excludes cloned models. Additional conditions are applied if present in the
   * {@link MetaModelFilterRequest}:
   *
   * <ul>
   *   <li><b>Name</b> — case-insensitive substring match on the metamodel name.
   *   <li><b>Description</b> — case-insensitive substring match on the metamodel description.
   *   <li><b>CreatedFrom</b> — include only models created at or after the given timestamp.
   *   <li><b>CreatedTo</b> — include only models created at or before the given timestamp.
   * </ul>
   *
   * @param callerEmail the email of the user whose metamodels should be retrieved
   * @param metaModelFilterRequest object containing optional filter values for name, description,
   *     and creation date ranges
   * @return a {@link Specification} combining all applicable filter predicates
   */
  public static Specification<MetaModel> buildSpecification(
      String callerEmail, MetaModelFilterRequest metaModelFilterRequest) {

    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<Predicate>();
      predicates.add(cb.equal(root.get("user").get("email"), callerEmail));
      predicates.add(cb.equal(root.get("isClone"), false));

      if (metaModelFilterRequest.getName() != null) {
        String name = "%" + metaModelFilterRequest.getName().trim().toLowerCase() + "%";
        Predicate nameLike = cb.like(cb.lower(root.get("name")), name);
        predicates.add(cb.or(nameLike));
      }

      if (metaModelFilterRequest.getDescription() != null) {
        String description =
            "%" + metaModelFilterRequest.getDescription().trim().toLowerCase() + "%";
        Predicate descLike = cb.like(cb.lower(root.get("description")), description);
        predicates.add(cb.or(descLike));
      }

      if (metaModelFilterRequest.getCreatedFrom() != null) {
        predicates.add(
            cb.greaterThanOrEqualTo(
                root.get("createdAt"), metaModelFilterRequest.getCreatedFrom()));
      }

      if (metaModelFilterRequest.getCreatedTo() != null) {
        predicates.add(
            cb.lessThanOrEqualTo(root.get("createdAt"), metaModelFilterRequest.getCreatedTo()));
      }

      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }
}
