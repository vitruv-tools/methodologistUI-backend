package tools.vitruv.methodologist.vsum.mapper;

import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import tools.vitruv.methodologist.vsum.controller.dto.response.FineGranularMetaModelRelationResponse;
import tools.vitruv.methodologist.vsum.model.FineGranularMetaModelRelation;

/** Mapper for fine-granular meta-model relations. */
@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = "spring")
public interface FineGranularMetaModelRelationMapper {
  /**
   * Maps a {@link FineGranularMetaModelRelation} to a {@link
   * FineGranularMetaModelRelationResponse}.
   *
   * @param fineGranularMetaModelRelation the source entity
   * @param lowCodeReactionRequestMapper mapper used to rebuild stored template params
   * @return the mapped response DTO
   */
  @Mapping(source = "reactionFileStorage.id", target = "reactionFileStorageId")
  @Mapping(
      target = "lowCodeReactionRequestBase",
      expression =
          "java(lowCodeReactionRequestMapper.map(fineGranularMetaModelRelation"
              + ".getLowCodeReactionTemplate(),"
              + " fineGranularMetaModelRelation.getLowCodeReactionTemplateParams()))")
  FineGranularMetaModelRelationResponse toFineGranularMetaModelRelationResponse(
      FineGranularMetaModelRelation fineGranularMetaModelRelation,
      @Context LowCodeReactionRequestMapper lowCodeReactionRequestMapper);
}
