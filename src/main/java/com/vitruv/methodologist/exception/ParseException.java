package com.vitruv.methodologist.exception;

/**
 * Custom runtime exception for handling parsing-related errors. Thrown when parsing operations
 * fail, such as during JSON/XML processing or data conversion.
 *
 * @extends RuntimeException
 */
public class ParseException extends RuntimeException {
  /**
   * Constructs a new ParseException with the specified error message.
   *
   * @param message the detail message describing the parsing error
   */
  public ParseException(String message) {
    super(message);
  }
}
