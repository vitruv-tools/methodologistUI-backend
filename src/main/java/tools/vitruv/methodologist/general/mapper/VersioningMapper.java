package tools.vitruv.methodologist.general.mapper;

import tools.vitruv.methodologist.general.controller.responsedto.LatestVersionResponse;
import tools.vitruv.methodologist.general.model.Versioning;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper for converting {@link Versioning} entities to DTOs. Used to map versioning data
 * to response objects for API output.
 *
 * @see Versioning
 * @see LatestVersionResponse
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = "spring")
public interface VersioningMapper {
  /**
   * Maps a {@link Versioning} entity to a {@link LatestVersionResponse} DTO.
   *
   * @param versioning the versioning entity to map
   * @return the mapped LatestVersionResponse DTO
   */
  LatestVersionResponse toLatestVersionResponse(Versioning versioning);
}
