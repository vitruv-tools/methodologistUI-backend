package tools.vitruv.methodologist.messages;

/** Contains error message constants used throughout the application. */
public class Error {
  public static final String CLIENT_NOT_FOUND_ERROR = "Client not found";
  public static final String USER_ID_NOT_FOUND_ERROR = "User id";
  public static final String VSUM_ID_NOT_FOUND_ERROR = "Vsum id";
  public static final String USER_DOSE_NOT_HAVE_ACCESS = "You don't have access";
  public static final String ECORE_FILE_ID_NOT_FOUND_ERROR = "Ecore file id";
  public static final String GEN_MODEL_FILE_ID_NOT_FOUND_ERROR = "Gen model file id";
  public static final String USER_EMAIL_NOT_FOUND_ERROR = "Email";
  public static final String USER_WRONG_PASSWORD_ERROR = "Wrong password";
  public static final String META_MODEL_ID_NOT_FOUND_ERROR = "Meta model id";
  public static final String FILE_HASHING_EXCEPTION = "Failed to compute SHA-256 hash";
  public static final String REACTION_FILE_IDS_ID_NOT_FOUND_ERROR = "Reaction files not found";
  public static final String MEMBER_IN_VSUM_NOT_FOUND_ERROR = "Member in VSUM not found.";
  public static final String FILE_ID_NOT_FOUND_ERROR = "File not found.";
  public static final String METAMODEL_PAIR_COUNT_MISMATCH_ERROR =
      "Number of Ecore files must match number of GenModel files";
  public static final String METAMODEL_PAIR_REQUIRED_ERROR =
      "At least one metamodel (Ecore/GenModel pair) must be provided.";
  public static final String METAMODEL_IDS_NOT_FOUND_IN_THIS_VSUM_NOT_FOUND_ERROR =
      "MetaModel Ids not found in this VSUM";
  public static final String REACTION_FILE_REQUIRED_ERROR =
      "At least one reactions file must be provided";
  public static final String FAT_JAR_NOT_FOUND_ERROR = "Fat jar not found at: ";
  public static final String VITRUV_CLI_EXECUTION_FAILED_ERROR = "Vitruv-CLI execution failed: ";
  public static final String VITRUV_CLI_ERROR = "Vitruv-CLI Error: ";
}
