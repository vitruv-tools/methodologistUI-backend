package tools.vitruv.methodologist.general;

import static tools.vitruv.methodologist.messages.Error.FILE_HASHING_EXCEPTION;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import tools.vitruv.methodologist.exception.FileHashingException;

/** SHA-256 hashing helper. */
public final class Sha256 {

  private Sha256() {}

  /**
   * Computes the SHA-256 digest of the given bytes and returns its lowercase hexadecimal string.
   *
   * @param data the bytes to hash; must not be {@code null}
   * @return 64 lowercase hex characters
   * @throws FileHashingException if the SHA-256 algorithm is not available
   */
  public static String hex(byte[] data) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(md.digest(data));
    } catch (NoSuchAlgorithmException e) {
      throw new FileHashingException(FILE_HASHING_EXCEPTION, e);
    }
  }
}
