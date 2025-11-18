package tools.vitruv.methodologist.vsum.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import tools.vitruv.methodologist.vsum.controller.dto.response.MetaModelRelationResponse;
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
   * @param metaModelRelation the source entity; may be {@code null}
   * @return the mapped response DTO, or {@code null} if input is {@code null}
   */
  @Mapping(source = "source.id", target = "sourceId")
  @Mapping(source = "target.id", target = "targetId")
  @Mapping(source = "reactionFileStorage.id", target = "reactionFileStorageId")
  MetaModelRelationResponse toMetaModelRelationResponse(MetaModelRelation metaModelRelation);
}
