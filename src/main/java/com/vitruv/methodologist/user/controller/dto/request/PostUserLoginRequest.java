package com.vitruv.methodologist.user.controller.dto.request;

import jakarta.persistence.Column;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostUserLoginRequest {
    @NotNull
    @NotBlank
    String email;
}
