package tools.vitruv.methodologist.user.service;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Utility for generating secure random passwords.
 *
 * <p>Uses {@link SecureRandom} as the randomness source and composes a password from: numbers,
 * special characters, uppercase letters and lowercase letters. Helper methods return streams of
 * randomly chosen {@link Character} values from ASCII ranges.
 */
public class RandomPasswordGenerator {

  Random random = new SecureRandom();

  /**
   * Generate a stream of random alphabetic characters.
   *
   * @param count number of characters to produce
   * @param upperCase if true, produce uppercase letters (ASCII A..Z), otherwise lowercase letters
   *     (ASCII a..z)
   * @return a {@link Stream} of random {@link Character} values
   */
  private Stream<Character> getRandomAlphabets(int count, boolean upperCase) {
    IntStream characters = null;
    if (upperCase) {
      characters = random.ints(count, 65, 90);
    } else {
      characters = random.ints(count, 97, 122);
    }
    return characters.mapToObj(data -> (char) data);
  }

  /**
   * Generate a stream of random numeric characters (ASCII '0'..'9').
   *
   * @param count number of numeric characters to produce
   * @return a {@link Stream} of random numeric {@link Character} values
   */
  private Stream<Character> getRandomNumbers(int count) {
    IntStream numbers = random.ints(count, 48, 57);
    return numbers.mapToObj(data -> (char) data);
  }

  /**
   * Generate a stream of random special characters from a chosen ASCII sub-range.
   *
   * @param count number of special characters to produce
   * @return a {@link Stream} of random special {@link Character} values
   */
  private Stream<Character> getRandomSpecialChars(int count) {
    IntStream specialChars = random.ints(count, 33, 45);
    return specialChars.mapToObj(data -> (char) data);
  }

  /**
   * Build a secure random password composed of:
   *
   * <p>The characters are collected, shuffled and concatenated into a {@link String}.
   *
   * @return generated password as {@link String}
   */
  public String generateSecureRandomPassword() {
    Stream<Character> passwordStream =
        Stream.concat(
            getRandomNumbers(2),
            Stream.concat(
                getRandomSpecialChars(2),
                Stream.concat(getRandomAlphabets(2, true), getRandomAlphabets(4, false))));
    List<Character> passwordChars = passwordStream.collect(Collectors.toList());

    /* shuffle password characters */
    Collections.shuffle(passwordChars);
    return passwordChars.stream()
        .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append)
        .toString();
  }
}
