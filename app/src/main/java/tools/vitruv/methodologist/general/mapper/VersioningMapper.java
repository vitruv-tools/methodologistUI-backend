package tools.vitruv.methodologist.general.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import tools.vitruv.methodologist.general.controller.responsedto.LatestVersionResponse;
import tools.vitruv.methodologist.general.model.Versioning;

/**
 * MapStruct mapper for converting {@link tools.vitruv.methodologist.general.model.Versioning}
 * entities to DTOs. Used to map versioning data to response objects for API output.
 *
 * @see tools.vitruv.methodologist.general.model.Versioning
 * @see tools.vitruv.methodologist.general.controller.responsedto.LatestVersionResponse
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = "spring")
public interface VersioningMapper {
  /**
   * Maps a {@link tools.vitruv.methodologist.general.model.Versioning} entity to a {@link
   * tools.vitruv.methodologist.general.controller.responsedto.LatestVersionResponse} DTO.
   *
   * @param versioning the versioning entity to map
   * @return the mapped LatestVersionResponse DTO
   */
  LatestVersionResponse toLatestVersionResponse(Versioning versioning);
}
