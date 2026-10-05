package tools.vitruv.methodologist.vsum.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumBuildResponse;
import tools.vitruv.methodologist.vsum.model.VsumBuild;

/** Maps {@link VsumBuild} entities to the DTOs returned by the build endpoints. */
@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = "spring")
public interface VsumBuildMapper {

  /**
   * Converts a build into its response representation.
   *
   * @param build the build to convert
   * @return the build with its VSUM id, the requester's email and whether its JAR is still stored
   */
  @Mapping(source = "vsum.id", target = "vsumId")
  @Mapping(source = "requestedBy.email", target = "requestedBy")
  @Mapping(target = "artifactAvailable", expression = "java(build.getArtifact() != null)")
  VsumBuildResponse toResponse(VsumBuild build);
}
