package tools.vitruv.methodologist.general.controller.responsedto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Response DTO for file storage operations. Contains basic file metadata for API responses. */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FileStorageResponse {
  private Long id;
}
