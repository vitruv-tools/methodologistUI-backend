package com.vitruv.methodologist.vsum.mapper;

import com.vitruv.methodologist.vsum.controller.dto.request.MetaModelPostRequest;
import com.vitruv.methodologist.vsum.controller.dto.response.MetaModelResponse;
import com.vitruv.methodologist.vsum.model.MetaModel;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper interface for converting between MetaModel entities and DTOs.
 * Handles the mapping of MetaModel-related objects while ignoring unmapped target properties.
 *
 * @see MetaModel
 * @see MetaModelPostRequest
 * @see MetaModelResponse
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
   * Converts a MetaModel entity to a MetaModelResponse DTO.
   * Maps the fileStorage.id to storageFileId in the response.
   *
   * @param metaModel the metamodel entity to convert
   * @return the mapped MetaModelResponse DTO
   */
  @Mapping(source = "fileStorage.id", target = "storageFileId")
  MetaModelResponse toMetaModelResponse(MetaModel metaModel);
}
