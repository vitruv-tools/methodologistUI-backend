package tools.vitruv.methodologist.vsum;

import lombok.Getter;

/**
 * Enumeration representing roles that users can have within a VSUM (Virtual System Under
 * Modification). Defines possible user roles and their string representations.
 */
public enum VsumRole {
  OWNER("Owner"),
  MEMBER("Member"),
  VIEWER("Viewer");

  @Getter private final String name;

  /**
   * Constructs a new VSUM role with the given name.
   *
   * @param name the string representation of the role
   */
  private VsumRole(final String name) {
    this.name = name;
  }

  /**
   * Tells whether this role may change a VSUM or start builds; viewers are read-only.
   *
   * @return {@code true} for every role except {@link #VIEWER}
   */
  public boolean canWrite() {
    return this != VIEWER;
  }
}
