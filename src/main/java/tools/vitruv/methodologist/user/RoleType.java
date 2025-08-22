package tools.vitruv.methodologist.user;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enumeration of user roles within the application.
 * <p>
 * Each role defines the level of access and permissions granted to the user.
 * The role is serialized to JSON using the value returned by {@link #getName()}.
 */
public enum RoleType {

  /** Standard application user with basic permissions. */
  USER("user");

  /** The string representation of the role. */
  private final String name;

  /**
   * Constructs a new {@code RoleType} with the given name.
   *
   * @param name the string value representing the role
   */
  private RoleType(final String name) {
    this.name = name;
  }

  /**
   * Returns the string representation of the role.
   * <p>
   * This value will be used when the enum is serialized to JSON.
   *
   * @return the role name
   */
  @JsonValue
  public String getName() {
    return name;
  }
}