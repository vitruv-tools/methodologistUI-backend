package tools.vitruv.methodologist.vsum;

import lombok.Getter;

public enum BuildStatus {
  STARTED("Started"),
  IN_PROGRESS("In Progress");

  @Getter private final String name;

  private BuildStatus(final String name) {
    this.name = name;
  }
}
