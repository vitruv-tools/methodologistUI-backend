package com.vitruv.methodologist.exception;

/**
 * Exception thrown when a response from a third-party API cannot be parsed correctly.
 *
 * <p>This typically indicates that the received payload does not match the expected format (e.g.,
 * malformed JSON, missing fields, or invalid data types).
 */
public class ParseThirdPartyApiResponseException extends RuntimeException {

  /**
   * Constructs a new {@code ParseThirdPartyApiResponseException} with the specified detail message.
   *
   * @param message a description of why parsing the third-party API response failed
   */
  public ParseThirdPartyApiResponseException(String message) {
    super(message);
  }
}
