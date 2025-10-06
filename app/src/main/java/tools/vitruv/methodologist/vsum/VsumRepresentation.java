package tools.vitruv.methodologist.vsum;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Serializable DTO representing a VSUM configuration: meta models, their relations, and users.
 *
 * <p>Used to persist or transfer a snapshot of a VSUM in JSON form and to rebuild VSUM state.
 *
 * <p>Lombok generates getters, setters, builders, and constructors.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class VsumRepresentation implements Serializable {
  @Serial private static final long serialVersionUID = 1L;
  private Set<Long> metaModels;
  private Set<MetaModelRelation> metaModelsRealation;
  private Set<Long> vsumUsers;

  /** Describes a relation between two meta models and its backing file. */
  @Getter
  @Setter
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class MetaModelRelation {
    private Long sourceId;
    private Long targetId;
    private Long relationFileStorage;
  }
}
