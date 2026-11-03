package tools.vitruv.methodologist.vsum.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A utility method of a VSUM: a named Xtend method body with typed parameters. All utility methods
 * of a VSUM are compiled into one Java utility class that the VSUM's reactions can call.
 */
@Entity
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UtilityMethod {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "vsum_id")
  private Vsum vsum;

  @NotNull private String name;

  private String description;

  /** The Xtend/Java return type, e.g. {@code List<SubSystem>}. */
  @NotNull private String returnType;

  /** The Xtend method body, without the surrounding braces. */
  @NotNull private String body;

  @Builder.Default
  @ElementCollection
  @CollectionTable(
      name = "utility_method_parameter",
      joinColumns = @JoinColumn(name = "utility_method_id"))
  @OrderColumn(name = "param_order")
  private List<UtilityMethodParameter> parameters = new ArrayList<>();

  /** Java imports, e.g. {@code tools.vitruv.methodologisttemplate.model.simulink.*}. */
  @Builder.Default @NotNull private List<String> imports = new ArrayList<>();

  @CreationTimestamp private Instant createdAt;

  @UpdateTimestamp private Instant updatedAt;
}
