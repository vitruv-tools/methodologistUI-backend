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
 * A helper of a VSUM: a named OCL expression with typed parameters. All helpers of a VSUM are
 * translated into one Java utility class that the VSUM's reactions can call.
 */
@Entity
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Helper {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "vsum_id")
  private Vsum vsum;

  @NotNull private String name;

  private String description;

  /** The OCL return type, e.g. {@code Sequence(simulink::SubSystem)}. */
  @NotNull private String returnType;

  /** The OCL expression. */
  @NotNull private String body;

  @Builder.Default
  @ElementCollection
  @CollectionTable(name = "helper_parameter", joinColumns = @JoinColumn(name = "helper_id"))
  @OrderColumn(name = "param_order")
  private List<HelperParameter> parameters = new ArrayList<>();

  /** Metamodel types usable by their simple name, e.g. {@code simulink::SubSystem}. */
  @Builder.Default @NotNull private List<String> imports = new ArrayList<>();

  @CreationTimestamp private Instant createdAt;

  @UpdateTimestamp private Instant updatedAt;
}
