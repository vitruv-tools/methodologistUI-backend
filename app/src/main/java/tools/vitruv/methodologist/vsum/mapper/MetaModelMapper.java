package tools.vitruv.methodologist.vsum.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import tools.vitruv.methodologist.vsum.controller.dto.request.MetaModelPostRequest;
import tools.vitruv.methodologist.vsum.controller.dto.response.MetaModelResponse;
import tools.vitruv.methodologist.vsum.model.MetaModel;

/**
 * MapStruct mapper interface for converting between MetaModel entities and DTOs. Handles the
 * mapping of MetaModel-related objects while ignoring unmapped target properties.
 *
 * @see tools.vitruv.methodologist.vsum.model.MetaModel
 * @see tools.vitruv.methodologist.vsum.controller.dto.request.MetaModelPostRequest
 * @see tools.vitruv.methodologist.vsum.controller.dto.response.MetaModelResponse
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = "spring")
public interface MetaModelMapper {
  /**
   * Converts a MetaModelPostRequest DTO to a MetaModel entity.
   *
   * @param metaModelPostRequest the DTO containing metamodel creation data
   * @return the mapped MetaModel entity
   */
  MetaModel toMetaModel(MetaModelPostRequest metaModelPostRequest);

  /**
   * Converts a MetaModel entity to a MetaModelResponse DTO. Maps the fileStorage.id to
   * storageFileId in the response.
   *
   * @param metaModel the metamodel entity to convert
   * @return the mapped MetaModelResponse DTO
   */
  @Mapping(source = "ecoreFile.id", target = "ecoreFileId")
  @Mapping(source = "genModelFile.id", target = "genModelFileId")
  @Mapping(source = "source.id", target = "sourceId")
  MetaModelResponse toMetaModelResponse(MetaModel metaModel);

  /**
   * Creates a copy of the given {@link MetaModel} while ignoring system-managed fields such as
   * {@code createdAt}, {@code updatedAt} and {@code id}.
   *
   * @param metaModel the source metamodel to clone
   * @return a new {@link MetaModel} instance with copied values
   */
  @Mapping(ignore = true, target = "id")
  @Mapping(ignore = true, target = "createdAt")
  @Mapping(ignore = true, target = "updatedAt")
  MetaModel clone(MetaModel metaModel);
}
