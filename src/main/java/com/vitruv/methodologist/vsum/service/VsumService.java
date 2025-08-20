package com.vitruv.methodologist.vsum.service;

import static com.vitruv.methodologist.messages.Error.VSUM_ID_NOT_FOUND_ERROR;

import com.vitruv.methodologist.exception.ConflictException;
import com.vitruv.methodologist.exception.NotFoundException;
import com.vitruv.methodologist.vsum.controller.dto.request.VsumPostRequest;
import com.vitruv.methodologist.vsum.controller.dto.request.VsumPutRequest;
import com.vitruv.methodologist.vsum.controller.dto.response.VsumResponse;
import com.vitruv.methodologist.vsum.mapper.VsumMapper;
import com.vitruv.methodologist.vsum.model.Vsum;
import com.vitruv.methodologist.vsum.model.repository.VsumRepository;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class VsumService {
  private final VsumMapper vsumMapper;
  private final VsumRepository vsumRepository;

    public VsumService(VsumMapper vsumMapper, VsumRepository vsumRepository) {
        this.vsumMapper = vsumMapper;
        this.vsumRepository = vsumRepository;
    }

  @Transactional
  public Vsum create(VsumPostRequest vsumPostRequest) {
    vsumRepository
        .findByNameIgnoreCase(vsumPostRequest.getName())
        .ifPresent(
            user -> {
              throw new ConflictException(vsumPostRequest.getName());
            });
    var vsum = vsumMapper.toVsum(vsumPostRequest);
    vsumRepository.save(vsum);

    return vsum;
  }

  @Transactional
  public Vsum update(Long id, VsumPutRequest vsumPutRequest) {
    var vsum =
        vsumRepository
            .findByIdAndRemovedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException(VSUM_ID_NOT_FOUND_ERROR));
    vsumMapper.updateByVsumPutRequest(vsumPutRequest, vsum);
    vsumRepository.save(vsum);

    return vsum;
  }

  @Transactional
  public VsumResponse findById(Long id) {
    var vsum =
        vsumRepository
            .findByIdAndRemovedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException(VSUM_ID_NOT_FOUND_ERROR));
    return vsumMapper.toVsumResponse(vsum);
  }

  @Transactional
  public Vsum remove(Long id) {
    var vsum =
        vsumRepository
            .findByIdAndRemovedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException(VSUM_ID_NOT_FOUND_ERROR));
    vsum.setRemovedAt(Instant.now());
    vsumRepository.save(vsum);

    return vsum;
  }
}
