package com.vituv.methodologist.general.mapper;

import com.vituv.methodologist.general.controller.responsedto.LatestVersionResponse;
import com.vituv.methodologist.general.model.Versioning;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = "spring")
public interface VersioningMapper {
    LatestVersionResponse toLatestVersionResponse(Versioning versioning);
}
