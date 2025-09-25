package tools.vitruv.methodologist.vsum;

import lombok.Getter;

/**
 * Enumeration representing roles that users can have within a VSUM (Virtual System Under
 * Modification). Defines possible user roles and their string representations.
 */
public enum VsumRole {
  OWNER("owner"),
  MEMBER("member");

  @Getter private final String name;

  /**
   * Constructs a new VSUM role with the given name.
   *
   * @param name the string representation of the role
   */
  private VsumRole(final String name) {
    this.name = name;
  }
}
