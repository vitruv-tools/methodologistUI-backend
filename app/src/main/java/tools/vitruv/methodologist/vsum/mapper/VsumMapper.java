package tools.vitruv.methodologist.vsum.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;
import tools.vitruv.methodologist.vsum.controller.dto.request.VsumPostRequest;
import tools.vitruv.methodologist.vsum.controller.dto.request.VsumPutRequest;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumMetaModelResponse;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumResponse;
import tools.vitruv.methodologist.vsum.model.Vsum;

/**
 * MapStruct mapper interface for converting between VSUM domain and DTO objects. Uses Spring
 * component model for dependency injection and ignores unmapped target properties.
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
   * Updates the given {@link Vsum} in place from the fields of the provided {@link VsumPutRequest}
   * using MapStruct.
   *
   * <p>Unmapped target properties are ignored. If {@code vsumPutRequest} is {@code null}, no
   * changes are applied. By default, {@code null} values in the request overwrite the corresponding
   * properties of {@code vsum}.
   *
   * @param vsumPutRequest the source update request; may be {@code null}
   * @param vsum the existing entity to mutate; must not be {@code null}
   */
  void updateByVsumPutRequest(VsumPutRequest vsumPutRequest, @MappingTarget Vsum vsum);

  /**
   * Converts a VSUM domain entity to a VSUM response DTO.
   *
   * @param vsum the VSUM domain entity to convert
   * @return the mapped VSUM response DTO
   */
  VsumResponse toVsumResponse(Vsum vsum);

  /**
   * Maps a Vsum entity to its corresponding response DTO representation.
   *
   * @param vsum the Vsum entity to map
   * @return a populated VsumMetaModelResponse containing the entity's data
   */
  VsumMetaModelResponse toVsumMetaModelResponse(Vsum vsum);
}
