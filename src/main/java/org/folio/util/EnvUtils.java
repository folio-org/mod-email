package org.folio.util;

import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;

public final class EnvUtils {

  private EnvUtils() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * Retrieves a value from system properties or environment variables, or returns a default.
   *
   * @param propertyName   - the name of the property variable
   * @param envName        - the name of the environment variable
   * @param defaultValue   - the value to return if neither is set or conversion fails
   * @param valueConverter - a function to convert the string value to the desired type
   * @param <T>            - the type of the value
   * @return the converted value, or defaultValue if not found or conversion fails
   */
  public static <T> T getEnvOrDefault(String propertyName, String envName, T defaultValue,
      Function<String, T> valueConverter) {
    var propertyValue = System.getProperty(propertyName, System.getenv(envName));
    if (StringUtils.isBlank(propertyValue)) {
      return defaultValue;
    }

    try {
      return valueConverter.apply(propertyValue);
    } catch (Exception e) {
      return defaultValue;
    }
  }
}
