package tools.vitruv.methodologist.messages;

/** Contains constant messages used throughout the application for user operations. */
public class Message {
  public static final String FOUND_ISSUE_IN_GEN_MODEL =
      "GenModel precheck status: ISSUES_FOUND."
          + " Validation issues were found in the GenModel file.";
  public static final String PRE_CHECK_GEN_MODEL_ABORTED =
      "GenModel precheck status: ABORTED. Precheck was aborted."
          + " Set applyGenModelFixes=true to apply fixes automatically.";
  public static final String PRE_CHECK_GEN_MODEL_UNKNOWN =
      "GenModel precheck did not return a valid status. ";
  public static final String PRE_CHECK_GEN_MODEL_FAILED = "GenModel precheck failed with status: ";
  public static final String LOGIN_USER_SUCCESSFULLY = "User successfully logged in";
  public static final String SIGNUP_USER_SUCCESSFULLY = "User successfully signed up";
  public static final String VERIFIED_USER_SUCCESSFULLY = "User successfully verified.";
  public static final String RESEND_OTP_WAS_SUCCESSFULLY = "New otp code sent to your email.";
  public static final String NEW_PASSWORD_SENT_SUCCESSFULLY = "New password sent to your email.";
  public static final String YOUR_PASSWORD_CHANGE_WAS_SUCCESSFUL =
      "Your password change was successful.";
  public static final String USER_UPDATED_SUCCESSFULLY = "User successfully updated";
  public static final String USER_REMOVED_SUCCESSFULLY = "User successfully removed";
  public static final String VSUM_CREATED_SUCCESSFULLY = "Vsum successfully created";
  public static final String META_MODEL_CREATED_SUCCESSFULLY = "Meta model successfully created";
  public static final String VSUM_UPDATED_SUCCESSFULLY = "Vsum successfully updated";
  public static final String VSUM_REMOVED_SUCCESSFULLY = "Vsum successfully removed";
  public static final String FILE_REMOVED_SUCCESSFULLY = "File successfully removed";
  public static final String FILE_UPLOADED_SUCCESSFULLY = "File uploaded successfully";
  public static final String META_MODEL_REMOVED_SUCCESSFULLY = "Meta model successfully removed";
  public static final String META_MODEL_UPDATED_SUCCESSFULLY = "Meta model successfully updated";
  public static final String VSUM_RECOVERY_WAS_SUCCESSFULLY = "Recovery was successful";
  public static final String VSUM_BUILD_WAS_SUCCESSFULLY = "The build was successful.";
  public static final String VSUM_HISTORY_REVERT_WAS_SUCCESSFULLY =
      "The vsum history was reverted.";
  public static final String VSUM_USER_DELETED_SUCCESSFULLY =
      "Member successfully removed from the Vsum";
  public static final String VSUM_USER_CREATED_SUCCESSFULLY =
      "Member successfully added into the Vsum.";
}
