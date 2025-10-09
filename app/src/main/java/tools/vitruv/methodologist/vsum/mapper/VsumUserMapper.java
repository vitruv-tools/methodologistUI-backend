package tools.vitruv.methodologist.vsum.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumUserResponse;
import tools.vitruv.methodologist.vsum.model.VsumUser;

/**
 * MapStruct mapper for converting {@link VsumUser} entities to {@link VsumUserResponse} DTOs.
 *
 * <p>Maps VSUM user details, including VSUM ID, user information, and role name.
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = "spring")
public interface VsumUserMapper {
  /**
   * Maps a {@link VsumUser} entity to a {@link VsumUserResponse} DTO.
   *
   * <ul>
   *   <li>Maps VSUM ID from {@code vsum.id} to {@code vsumId}
   *   <li>Maps user's first and last name, and email
   *   <li>Maps role name from {@code role.name} to {@code roleEn}
   * </ul>
   *
   * @param vsumUser the VSUM user entity to map
   * @return the mapped VSUM user response DTO
   */
  @Mapping(source = "vsum.id", target = "vsumId")
  @Mapping(source = "user.firstName", target = "firstName")
  @Mapping(source = "user.lastName", target = "lastName")
  @Mapping(source = "user.email", target = "email")
  @Mapping(source = "role.name", target = "roleEn")
  VsumUserResponse toVsumUserResponse(VsumUser vsumUser);
}
