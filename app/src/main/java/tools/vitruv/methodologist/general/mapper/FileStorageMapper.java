package tools.vitruv.methodologist.general.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import tools.vitruv.methodologist.general.model.FileStorage;

/**
 * MapStruct mapper for handling operations related to {@link
 * tools.vitruv.methodologist.general.model.FileStorage}. This mapper provides methods for cloning
 * `FileStorage` entities, with specific fields being ignored during the mapping.
 *
 * <p>The mapper is implemented with the `spring` component model and ignores unmapped target
 * properties.
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE, componentModel = "spring")
public interface FileStorageMapper {

  /**
   * Creates and returns a new instance of {@link FileStorage} by copying the values of the provided
   * {@code fileStorage} while ignoring specific fields such as {@code id} and {@code createdAt}.
   *
   * @param fileStorage the source {@link FileStorage} instance to be cloned
   * @return a new {@link FileStorage} instance with copied properties, excluding {@code id} and
   *     {@code createdAt}
   */
  @Mapping(ignore = true, target = "id")
  @Mapping(ignore = true, target = "createdAt")
  FileStorage clone(FileStorage fileStorage);
}
