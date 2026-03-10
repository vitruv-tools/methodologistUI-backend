package tools.vitruv.methodologist.vsum.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import tools.vitruv.methodologist.vsum.controller.dto.response.FineGranularMetaModelRelationResponse;
import tools.vitruv.methodologist.vsum.model.FineGranularMetaModelRelation;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = "spring")
public interface FineGranularMetaModelRelationMapper {
    @Mapping(source = "reactionFileStorage.id", target = "reactionFileStorageId")
    FineGranularMetaModelRelationResponse toMetaModelRelationResponse(FineGranularMetaModelRelation fineGranularMetaModelRelation);
}

