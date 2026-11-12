package tools.vitruv.methodologist.vsum.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumVersionResponse;
import tools.vitruv.methodologist.vsum.model.VsumVersion;

/** Maps {@link VsumVersion} entities to the DTOs returned by the version endpoints. */
@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = "spring")
public interface VsumVersionMapper {

  /**
   * Converts a version into its response representation.
   *
   * @param version the version to convert
   * @return the version with its number, the publisher's email and its build id
   */
  @Mapping(source = "createdBy.email", target = "createdBy")
  @Mapping(source = "build.id", target = "buildId")
  VsumVersionResponse toResponse(VsumVersion version);
}
