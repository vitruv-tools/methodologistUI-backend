package com.vituv.methodologist.general.service;

import com.vituv.methodologist.exception.NotFoundException;
import com.vituv.methodologist.general.controller.responsedto.LatestVersionResponse;
import com.vituv.methodologist.general.mapper.VersioningMapper;
import com.vituv.methodologist.general.model.repository.VersioningRepository;
import io.investino.ResponseTemplateDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.vituv.methodologist.messages.Error.CLIENT_NOT_FOUND_ERROR;

@Service
public class GeneralService {

    private final VersioningRepository versioningRepository;
    private final VersioningMapper versioningMapper;

    public GeneralService(VersioningRepository versioningRepository, VersioningMapper versioningMapper) {
        this.versioningRepository = versioningRepository;
        this.versioningMapper = versioningMapper;
    }

    @Transactional
    public ResponseTemplateDto<LatestVersionResponse> getLatestVersion(String clientName) {
        var client = versioningRepository.findTopByAppNameOrderByIdDesc(clientName)
                .orElseThrow(() -> new NotFoundException(CLIENT_NOT_FOUND_ERROR));
        return ResponseTemplateDto.<LatestVersionResponse>builder()
                .data(versioningMapper.toLatestVersionResponse(client))
                .build();
    }
}