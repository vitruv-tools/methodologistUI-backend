package tools.vitruv.methodologist.vsum.controller.dto.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Response DTO describing a VSUM (project) that uses a library metamodel through one of its clones.
 *
 * <p>All fields are {@code null} if the caller is not a member of the VSUM, so other users' project
 * names are not disclosed while the entry still counts towards the usage. A non-null {@code
 * removedAt} means the VSUM is in the trash; it still blocks the deletion of the library metamodel
 * until it is deleted permanently.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MetaModelVsumUsageResponse {
  private Long id;
  private String name;
  private Instant removedAt;
}
