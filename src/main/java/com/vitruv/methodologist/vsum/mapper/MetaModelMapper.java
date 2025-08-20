package com.vitruv.methodologist.vsum.mapper;

import com.vitruv.methodologist.vsum.controller.dto.request.MetaModelPostRequest;
import com.vitruv.methodologist.vsum.controller.dto.response.MetaModelResponse;
import com.vitruv.methodologist.vsum.model.MetaModel;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = "spring")
public interface MetaModelMapper {
  MetaModel toMetaModel(MetaModelPostRequest metaModelPostRequest);

  @Mapping(source = "fileStorage.id", target = "storageFileId")
  MetaModelResponse toMetaModelResponse(MetaModel metaModel);
}
