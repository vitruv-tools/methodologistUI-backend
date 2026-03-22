package tools.vitruv.methodologist.vsum.mapper;

import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import tools.vitruv.methodologist.vsum.controller.dto.response.FineGranularMetaModelRelationResponse;
import tools.vitruv.methodologist.vsum.model.FineGranularMetaModelRelation;

@Mapper(
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    componentModel = "spring",
    uses = LowCodeReactionRequestMapper.class)
public interface FineGranularMetaModelRelationMapper {
  @Mapping(source = "reactionFileStorage.id", target = "reactionFileStorageId")
  @Mapping(
      target = "lowCodeReactionRequestBase",
      expression =
          "java(lowCodeReactionRequestMapper.map(fineGranularMetaModelRelation.getLowCodeReactionTemplate(), fineGranularMetaModelRelation.getLowCodeReactionTemplateParams()))")
  FineGranularMetaModelRelationResponse toMetaModelRelationResponse(
      FineGranularMetaModelRelation fineGranularMetaModelRelation,
      @Context LowCodeReactionRequestMapper lowCodeReactionRequestMapper);
}
