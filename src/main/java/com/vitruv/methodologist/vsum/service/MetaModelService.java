package com.vitruv.methodologist.vsum.service;

import static com.vitruv.methodologist.messages.Error.FILE_STORAGE_ID_NOT_FOUND_ERROR;
import static com.vitruv.methodologist.messages.Error.USER_EMAIL_NOT_FOUND_ERROR;

import com.vitruv.methodologist.exception.ConflictException;
import com.vitruv.methodologist.exception.NotFoundException;
import com.vitruv.methodologist.general.model.repository.FileStorageRepository;
import com.vitruv.methodologist.user.model.repository.UserRepository;
import com.vitruv.methodologist.vsum.controller.dto.request.MetaModelPostRequest;
import com.vitruv.methodologist.vsum.controller.dto.response.MetaModelResponse;
import com.vitruv.methodologist.vsum.mapper.MetaModelMapper;
import com.vitruv.methodologist.vsum.model.MetaModel;
import com.vitruv.methodologist.vsum.model.repository.MetaModelRepository;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service class for managing metamodel operations including creation and retrieval. Handles the
 * business logic for metamodel management while ensuring proper validation and relationships with
 * files and users.
 *
 * @see MetaModel
 * @see MetaModelRepository
 * @see FileStorageRepository
 * @see UserRepository
 */
@Service
@Slf4j
public class MetaModelService {
  private final MetaModelMapper metaModelMapper;
  private final MetaModelRepository metaModelRepository;
  private final FileStorageRepository fileStorageRepository;
  private final UserRepository userRepository;

  /**
   * Constructs a new MetaModelService with the specified dependencies.
   *
   * @param metaModelMapper mapper for MetaModel conversions
   * @param metaModelRepository repository for metamodel operations
   * @param fileStorageRepository repository for file storage operations
   * @param userRepository repository for user operations
   */
  public MetaModelService(
      MetaModelMapper metaModelMapper,
      MetaModelRepository metaModelRepository,
      FileStorageRepository fileStorageRepository,
      UserRepository userRepository) {
    this.metaModelMapper = metaModelMapper;
    this.metaModelRepository = metaModelRepository;
    this.fileStorageRepository = fileStorageRepository;
    this.userRepository = userRepository;
  }

  /**
   * Creates a new metamodel with the specified details and associates it with a file and user.
   *
   * @param callerEmail email of the user creating the metamodel
   * @param metaModelPostRequest DTO containing the metamodel details
   * @return the created MetaModel entity
   * @throws ConflictException if a metamodel with the same name already exists
   * @throws NotFoundException if the file storage ID or user email is not found
   */
  @Transactional
  public MetaModel create(String callerEmail, MetaModelPostRequest metaModelPostRequest) {
    metaModelRepository
        .findByNameIgnoreCase(metaModelPostRequest.getName())
        .ifPresent(
            metaModel -> {
              throw new ConflictException(metaModelPostRequest.getName());
            });
    var metaModel = metaModelMapper.toMetaModel(metaModelPostRequest);
    var fileStorage =
        fileStorageRepository
            .findById(metaModelPostRequest.getUploadedFileId())
            .orElseThrow(() -> new NotFoundException(FILE_STORAGE_ID_NOT_FOUND_ERROR));

    var user =
        userRepository
            .findByEmailIgnoreCaseAndRemovedAtIsNull(callerEmail)
            .orElseThrow(() -> new NotFoundException(USER_EMAIL_NOT_FOUND_ERROR));
    metaModel.setFileStorage(fileStorage);
    metaModel.setUser(user);
    metaModelRepository.save(metaModel);

    return metaModel;
  }

  /**
   * Retrieves all metamodels associated with a specific user.
   *
   * @param callerEmail email of the user whose metamodels to retrieve
   * @return list of MetaModelResponse DTOs containing the metamodel details
   */
  @Transactional
  public List<MetaModelResponse> findAllByUser(String callerEmail) {
    var metaModels = metaModelRepository.findAllByUser_email(callerEmail);
    return metaModels.stream().map(metaModelMapper::toMetaModelResponse).toList();
  }
}
