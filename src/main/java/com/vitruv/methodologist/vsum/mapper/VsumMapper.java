package com.vitruv.methodologist.vsum.mapper;

import com.vitruv.methodologist.user.model.User;
import com.vitruv.methodologist.vsum.controller.dto.request.VsumPostRequest;
import com.vitruv.methodologist.vsum.controller.dto.request.VsumPutRequest;
import com.vitruv.methodologist.vsum.controller.dto.response.VsumResponse;
import com.vitruv.methodologist.vsum.model.Vsum;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper interface for converting between VSUM domain and DTO objects.
 * Uses Spring component model for dependency injection and ignores unmapped target properties.
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = "spring")
public interface VsumMapper {

  /**
   * Converts a VSUM creation request DTO to a VSUM domain entity.
   *
   * @param vsumPostRequest the DTO containing VSUM creation data
   * @return the mapped VSUM domain entity
   */
  Vsum toVsum(VsumPostRequest vsumPostRequest);

  /**
   * Updates an existing VSUM domain entity with data from a VSUM update request DTO.
   *
   * @param vsumPutRequest the DTO containing VSUM update data
   * @param vsum the existing VSUM entity to be updated
   */
  void updateByVsumPutRequest(VsumPutRequest vsumPutRequest, @MappingTarget Vsum vsum);

  /**
   * Converts a VSUM domain entity to a VSUM response DTO.
   *
   * @param vsum the VSUM domain entity to convert
   * @return the mapped VSUM response DTO
   */
  VsumResponse toVsumResponse(Vsum vsum);
}
