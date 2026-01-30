package org.folio.util;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Test;

public class EnvUtilsTest {

  private static final String PROPERTY_NAME = "test.property";
  private static final String ENV_NAME = "TEST_PROPERTY";

  @After
  public void tearDown() {
    System.clearProperty(PROPERTY_NAME);
  }

  @Test
  public void getEnvOrDefault_positive_integerValueFromProperty() {
    System.setProperty(PROPERTY_NAME, "30");

    var result = EnvUtils.getEnvOrDefault(PROPERTY_NAME, ENV_NAME, 10, Integer::parseInt);

    assertEquals(Integer.valueOf(30), result);
  }

  @Test
  public void getEnvOrDefault_positive_invalidIntegerValue() {
    System.setProperty(PROPERTY_NAME, "unknown");

    var result = EnvUtils.getEnvOrDefault(PROPERTY_NAME, ENV_NAME, 10, Integer::parseInt);

    assertEquals(Integer.valueOf(10), result);
  }

  @Test
  public void getEnvOrDefault_positive_propertyNotFound() {
    var result = EnvUtils.getEnvOrDefault(PROPERTY_NAME, ENV_NAME, 10, Integer::parseInt);

    assertEquals(Integer.valueOf(10), result);
  }

  @Test
  public void getEnvOrDefault_positive_stringValue() {
    System.setProperty(PROPERTY_NAME, "test-value");

    var result = EnvUtils.getEnvOrDefault(PROPERTY_NAME, ENV_NAME, "default", String::valueOf);

    assertEquals("test-value", result);
  }
}
