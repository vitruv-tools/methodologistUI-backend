package tools.vitruv.methodologist.vsum.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A parameter of an {@link Utility method}: its name and OCL type. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UtilityMethodParameter {

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "type", nullable = false)
  private String type;
}
