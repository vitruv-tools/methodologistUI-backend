package com.vitruv.methodologist.user.controller.dto.response;

import lombok.*;

/**
 * Data transfer object for user response.
 * Contains user identification and profile information returned by API endpoints.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
  private Long id;
  private String email;
  private String firstName;
  private String lastName;
}
