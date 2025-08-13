package com.vitruv.methodologist.general.controller.responsedto;

import lombok.*;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LatestVersionResponse {
    private Boolean forceUpdate;
    private String version;
}