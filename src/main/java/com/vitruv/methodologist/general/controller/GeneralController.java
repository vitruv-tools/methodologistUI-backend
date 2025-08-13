package com.vitruv.methodologist.general.controller;

import com.vitruv.methodologist.ResponseTemplateDto;
import com.vitruv.methodologist.general.controller.responsedto.LatestVersionResponse;
import com.vitruv.methodologist.general.service.GeneralService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/")
@Validated
public class GeneralController {

    private final GeneralService generalService;
    public GeneralController(GeneralService generalService) {
        this.generalService = generalService;
    }

    @GetMapping("v1/general/latestVersion/{clientName}")
    public ResponseTemplateDto<LatestVersionResponse> getLatestVersion(@PathVariable(value = "clientName") String clientName) {
        return generalService.getLatestVersion(clientName);
    }
}
