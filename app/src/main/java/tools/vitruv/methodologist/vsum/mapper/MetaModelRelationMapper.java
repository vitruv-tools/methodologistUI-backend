package tools.vitruv.methodologist.vsum.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import tools.vitruv.methodologist.vsum.controller.dto.response.MetaModelRelationResponse;
import tools.vitruv.methodologist.vsum.model.MetaModel;
import tools.vitruv.methodologist.vsum.model.MetaModelRelation;

/**
 * MapStruct mapper for converting {@link MetaModelRelation} entities to {@link
 * MetaModelRelationResponse} DTOs.
 *
 * <p>Uses {@code componentModel\="spring"} for Spring DI and {@link ReportingPolicy#IGNORE} to
 * ignore unmapped targets.
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = "spring")
public interface MetaModelRelationMapper {
  /**
   * Maps a {@link MetaModelRelation} to its {@link MetaModelRelationResponse} representation.
   *
   * <p>{@code source}/{@code target} on the entity reference the VSUM-scoped {@link MetaModel}
   * clone created when the metamodel was linked to the VSUM (see {@code
   * VsumMetaModelService#create}), not the catalog metamodel a client can look up via {@code
   * /api/v1/meta-models}. {@code sourceId}/{@code targetId} resolve through the clone's {@code
   * source} back-reference to that original catalog id instead, so this DTO's ids line up with the
   * ones {@link tools.vitruv.methodologist.vsum.controller.dto.response.MetaModelResponse#getId()}
   * and {@code sync-changes} request payloads use elsewhere in the API.
   *
   * @param metaModelRelation the source entity; may be {@code null}
   * @return the mapped response DTO, or {@code null} if input is {@code null}
   */
  @Mapping(
      target = "sourceId",
      expression = "java(originalMetaModelId(metaModelRelation.getSource()))")
  @Mapping(
      target = "targetId",
      expression = "java(originalMetaModelId(metaModelRelation.getTarget()))")
  @Mapping(source = "reactionFileStorage.id", target = "reactionFileStorageId")
  MetaModelRelationResponse toMetaModelRelationResponse(MetaModelRelation metaModelRelation);

  /**
   * Resolves the catalog metamodel id for a (possibly VSUM-scoped clone) {@link MetaModel}.
   *
   * @param metaModel a relation's source or target metamodel; may be {@code null}
   * @return {@code metaModel.getSource().getId()} if {@code metaModel} is a clone, otherwise {@code
   *     metaModel.getId()} itself; {@code null} if {@code metaModel} is {@code null}
   */
  default Long originalMetaModelId(MetaModel metaModel) {
    if (metaModel == null) {
      return null;
    }
    return metaModel.getSource() != null ? metaModel.getSource().getId() : metaModel.getId();
  }
}
