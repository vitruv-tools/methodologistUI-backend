package tools.vitruv.methodologist.vsum.model.repository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.vitruv.methodologist.vsum.controller.dto.request.MetaModelFilterRequest;
import tools.vitruv.methodologist.vsum.model.MetaModel;

class MetaModelSpecificationsTest {

  private Root<MetaModel> root;
  private CriteriaQuery<?> query;
  private CriteriaBuilder cb;
  private Path<Object> domainPath;
  private Expression<String> lowerDomain;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    root = mock(Root.class, RETURNS_DEEP_STUBS);
    query = mock(CriteriaQuery.class);
    cb = mock(CriteriaBuilder.class);
    domainPath = mock(Path.class);
    lowerDomain = mock(Expression.class);
    when(root.get("domain")).thenReturn(domainPath);
    when(cb.lower(any())).thenReturn(mock(Expression.class));
    when(cb.lower((Expression<String>) (Expression<?>) domainPath)).thenReturn(lowerDomain);
  }

  @Test
  void buildSpecification_filtersByDomainCaseInsensitiveSubstring() {
    MetaModelFilterRequest request =
        MetaModelFilterRequest.builder().domain("  KIT ").ownedByUser(false).build();

    MetaModelSpecifications.buildSpecification("u@ex.com", request).toPredicate(root, query, cb);

    verify(cb).like(lowerDomain, "%kit%");
  }

  @Test
  void buildSpecification_skipsDomainFilter_whenDomainIsNull() {
    MetaModelFilterRequest request = MetaModelFilterRequest.builder().ownedByUser(false).build();

    MetaModelSpecifications.buildSpecification("u@ex.com", request).toPredicate(root, query, cb);

    verify(cb, never()).like(any(), anyString());
  }
}
