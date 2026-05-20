package tools.vitruv.methodologist.vsum.mapper;

import java.util.List;
import java.util.Set;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import tools.vitruv.methodologist.vsum.controller.dto.response.MetaModelResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.ViewsResponse;
import tools.vitruv.methodologist.vsum.model.MetaModel;
import tools.vitruv.methodologist.vsum.model.VsumView;
import tools.vitruv.methodologist.vsum.model.VsumViewMetaModel;

/**
 * MapStruct mapper interface for converting between {@link VsumView} entities and {@link
 * ViewsResponse} DTOs. Handles mapping of view-related objects while ignoring unmapped target
 * properties.
 *
 * @see tools.vitruv.methodologist.vsum.model.VsumView
 * @see tools.vitruv.methodologist.vsum.controller.dto.response.ViewsResponse
 */
@Mapper(
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    componentModel = "spring",
    uses = MetaModelMapper.class)
public interface VsumViewMapper {

  /**
   * Converts a {@link VsumView} entity to a {@link ViewsResponse} DTO.
   *
   * <p>Maps the fileStorage.id to fileStorageId in the response.
   *
   * @param vsumView the VsumView entity to convert
   * @return the mapped ViewsResponse DTO
   */
  @Mapping(source = "fileStorage.id", target = "fileStorageId")
  @Mapping(
      source = "viewMetaModels",
      target = "assignedModels",
      qualifiedByName = "toAssignedModels")
  ViewsResponse toViewsResponse(VsumView vsumView);

  /**
   * Maps view-to-metamodel links to metamodel response payloads.
   *
   * @param viewMetaModels the link entities between a view and metamodels
   * @return the metamodel response list
   */
  @Named("toAssignedModels")
  @SuppressWarnings("unused")
  default List<MetaModelResponse> toAssignedModels(Set<VsumViewMetaModel> viewMetaModels) {
    if (viewMetaModels == null || viewMetaModels.isEmpty()) {
      return List.of();
    }
    return viewMetaModels.stream()
        .map(VsumViewMetaModel::getMetaModel)
        .map(this::toMetaModelResponse)
        .toList();
  }

  /**
   * Maps a metamodel entity to its API response representation.
   *
   * @param metaModel the metamodel entity
   * @return the metamodel response
   */
  MetaModelResponse toMetaModelResponse(MetaModel metaModel);
}

