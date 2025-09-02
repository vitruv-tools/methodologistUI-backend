package tools.vitruv.methodologist.vsum.model.repository;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import org.springframework.data.jpa.domain.Specification;
import tools.vitruv.methodologist.vsum.controller.dto.request.MetaModelFilterRequest;
import tools.vitruv.methodologist.vsum.model.MetaModel;

/**
 * This class defines specifications for filtering and querying MetaModel entities. The
 * specifications are based on various filtering criteria provided through a filter request and
 * caller-specific context.
 */
public class MetaModelSpecifications {

  public static Specification<MetaModel> buildSpecification(
      String callerEmail, MetaModelFilterRequest metaModelFilterRequest) {

    return (root, query, cb) -> {
      var predicates = new ArrayList<Predicate>();
      predicates.add(cb.equal(root.get("user").get("email"), callerEmail));
      predicates.add(cb.equal(root.get("isClone"), false));

      if (metaModelFilterRequest.getName() != null) {
        String name = "%" + metaModelFilterRequest.getName().trim().toLowerCase() + "%";
        var nameLike = cb.like(cb.lower(root.get("name")), name);
        predicates.add(cb.or(nameLike));
      }

      if (metaModelFilterRequest.getDescription() != null) {
        String description =
            "%" + metaModelFilterRequest.getDescription().trim().toLowerCase() + "%";
        var descLike = cb.like(cb.lower(root.get("description")), description);
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
