package com.vitruv.methodologist.vsum.service;

import static com.vitruv.methodologist.messages.Error.FILE_STORAGE_ID_NOT_FOUND_ERROR;
import static com.vitruv.methodologist.messages.Error.USER_EMAIL_NOT_FOUND_ERROR;

import com.vitruv.methodologist.exception.ConflictException;
import com.vitruv.methodologist.exception.NotFoundException;
import com.vitruv.methodologist.general.model.repository.FileStorageRepository;
import com.vitruv.methodologist.general.service.FileStorageService;
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

@Service
@Slf4j
public class MetaModelService {
  private final MetaModelMapper metaModelMapper;
  private final MetaModelRepository metaModelRepository;
  private final FileStorageRepository fileStorageRepository;
  private final UserRepository userRepository;

  public MetaModelService(
          MetaModelMapper metaModelMapper,
          MetaModelRepository metaModelRepository,
          FileStorageRepository fileStorageRepository, UserRepository userRepository) {
    this.metaModelMapper = metaModelMapper;
    this.metaModelRepository = metaModelRepository;
    this.fileStorageRepository = fileStorageRepository;
    this.userRepository = userRepository;
  }

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

    var user = userRepository.findByEmailIgnoreCaseAndRemovedAtIsNull(callerEmail)
            .orElseThrow(() -> new NotFoundException(USER_EMAIL_NOT_FOUND_ERROR));
    metaModel.setFileStorage(fileStorage);
    metaModel.setUser(user);
    metaModelRepository.save(metaModel);

    return metaModel;
  }

  @Transactional
  public List<MetaModelResponse> findAllByUser(String callerEmail) {
    var metaModels = metaModelRepository.findAllByUser_email(callerEmail);
    return metaModels.stream().map(metaModelMapper::toMetaModelResponse).toList();
  }
}
