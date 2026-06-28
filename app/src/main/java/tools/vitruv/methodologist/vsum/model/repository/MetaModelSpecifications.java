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
   * Builds a JPA {@link Specification} for filtering {@link MetaModel} entities according to the
   * provided caller context and filter request.
   *
   * <p>The resulting specification always excludes metamodels that are marked removed ({@code
   * removedAt != null}), excludes metamodels created from another source ({@code source != null}),
   * and excludes metamodels whose owning user is removed ({@code user.removedAt != null}).
   *
   * <p>Filtering behavior:
   *
   * <ul>
   *   <li>\`ownedByUser\` — when {@code metaModelFilterRequest.getOwnedByUser()} is {@code null} or
   *       {@code true} the specification restricts results to metamodels whose owner's email equals
   *       the supplied {@code callerEmail}. When {@code false} no owner-email restriction is
   *       applied.
   *   <li>\`name\` — if provided, performs a case-insensitive substring match against the metamodel
   *       name (the value is trimmed and wrapped with {\@code %} for LIKE).
   *   <li>\`description\` — if provided, performs a case-insensitive substring match against the
   *       metamodel description (trimmed and wrapped with {\@code %}).
   *   <li>\`createdFrom\` — if provided, includes metamodels with {@code createdAt >= createdFrom}.
   *   <li>\`createdTo\` — if provided, includes metamodels with {@code createdAt <= createdTo}.
   * </ul>
   *
   * @param callerEmail the email of the calling user used to restrict ownership when applicable
   * @param metaModelFilterRequest filter values (may contain {@code null} fields to skip criteria)
   * @return a {@link Specification} combining all applicable predicates
   */
  public static Specification<MetaModel> buildSpecification(
      String callerEmail, MetaModelFilterRequest metaModelFilterRequest) {

    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();

      predicates.add(cb.isNull(root.get("removedAt")));

      // ** We should don't return meta-models that created by deleted user **//
      predicates.add(cb.isNull(root.get("user").get("removedAt")));

      // ** Don,t return cloned meta-models **//
      predicates.add(cb.isNull(root.get("source")));

      if (metaModelFilterRequest.getOwnedByUser() == null
          || metaModelFilterRequest.getOwnedByUser()) {
        predicates.add(cb.equal(root.get("user").get("email"), callerEmail));
      }

      if (metaModelFilterRequest.getName() != null) {
        String name = "%" + metaModelFilterRequest.getName().trim().toLowerCase() + "%";
        Predicate nameLike = cb.like(cb.lower(root.get("name")), name);
        predicates.add(nameLike);
      }

      if (metaModelFilterRequest.getDescription() != null) {
        String description =
            "%" + metaModelFilterRequest.getDescription().trim().toLowerCase() + "%";
        Predicate descLike = cb.like(cb.lower(root.get("description")), description);
        predicates.add(descLike);
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
