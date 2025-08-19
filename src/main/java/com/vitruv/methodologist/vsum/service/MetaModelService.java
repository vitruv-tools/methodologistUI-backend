package com.vitruv.methodologist.vsum.service;

import static com.vitruv.methodologist.messages.Error.FILE_STORAGE_ID_NOT_FOUND_ERROR;

import com.vitruv.methodologist.exception.ConflictException;
import com.vitruv.methodologist.exception.NotFoundException;
import com.vitruv.methodologist.general.model.repository.FileStorageRepository;
import com.vitruv.methodologist.general.service.FileStorageService;
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

  public MetaModelService(
          MetaModelMapper metaModelMapper,
          MetaModelRepository metaModelRepository,
          FileStorageRepository fileStorageRepository) {
    this.metaModelMapper = metaModelMapper;
    this.metaModelRepository = metaModelRepository;
    this.fileStorageRepository = fileStorageRepository;
  }

  @Transactional
  public MetaModel create(MetaModelPostRequest metaModelPostRequest) {
    metaModelRepository
        .findByNameIgnoreCase(metaModelPostRequest.getName())
        .ifPresent(
            user -> {
              throw new ConflictException(metaModelPostRequest.getName());
            });
    var metaModel = metaModelMapper.toMetaModel(metaModelPostRequest);
    var fileStorage = fileStorageRepository.findById(metaModelPostRequest.getUploadedFileId())
                    .orElseThrow(() -> new NotFoundException(FILE_STORAGE_ID_NOT_FOUND_ERROR));

    metaModel.setFileStorage(fileStorage);
    metaModelRepository.save(metaModel);

    return metaModel;
  }

  @Transactional
  public List<MetaModelResponse> findAllByUser(String callerEmail) {
    var metaModels = metaModelRepository.findAllByUser_email(callerEmail);
    return metaModels.stream().map(metaModelMapper::toMetaModelResponse).toList();
  }
}
