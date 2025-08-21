package com.vitruv.methodologist.general.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity representing version information for an application. Stores the application name, version
 * string, and force update flag.
 */
@Data
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class Versioning {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull @NotBlank private String appName;

  @NotNull @NotBlank private String version;

  @NotNull private Boolean forceUpdate;
}
