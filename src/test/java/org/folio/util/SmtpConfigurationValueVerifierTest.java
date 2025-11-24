package org.folio.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.vertx.core.json.JsonObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import org.folio.exceptions.EmailSettingsException;
import org.junit.Test;

public class SmtpConfigurationValueVerifierTest {

  @Test
  public void verify_positive_validValue() {
    var value = new LinkedHashMap<String, String>();
    value.put("username", "test-username");
    value.put("password", "test-password");
    value.put("port", "557");
    value.put("host", "test-mail.sample.org");

    try {
      SmtpConfigurationValueVerifier.verify(value);
    } catch (EmailSettingsException e) {
      fail("Expected no EmailSettingsException for a valid map value, but got: " + e.getMessage());
    }
  }

  @Test
  public void verify_negative_missingValue() {
    var value = new LinkedHashMap<String, String>();
    value.put("username", "test-username");
    value.put("port", "557");
    value.put("host", "test-mail.sample.org");

    var exception = assertThrows(EmailSettingsException.class, () ->
      SmtpConfigurationValueVerifier.verify(value));

    assertEquals("Invalid value in setting", exception.getMessage());

    var parameter = exception.getError().getParameters().getFirst();
    assertEquals("value.password", parameter.getKey());
    assertEquals("must not be null", parameter.getValue());
  }

  @Test
  public void verify_negative_stringIsNotAcceptedValue() {
    var value = "test";
    var exception = assertThrows(EmailSettingsException.class, () ->
      SmtpConfigurationValueVerifier.verify(value));

    assertEquals("Invalid value in setting", exception.getMessage());
  }

  @Test
  public void verify_negative_jsonObjectIsNotAccepted() {
    var value = new JsonObject()
      .put("username", "test-username")
      .put("password", "test-password")
      .put("port", "557")
      .put("host", "test-mail.sample.org");
    var exception = assertThrows(EmailSettingsException.class, () ->
      SmtpConfigurationValueVerifier.verify(value));

    assertEquals("Invalid value in setting", exception.getMessage());

    var parameter = exception.getError().getParameters().getFirst();
    assertEquals("value", parameter.getKey());
    assertEquals("must be an object", parameter.getValue());
  }

  @Test
  public void verify_negative_serializationException() {
    var value = new LinkedHashMap<Object, Object>();
    // non-string key causes Vert.x JsonObject.mapFrom to throw IllegalArgumentException
    value.put(1, "invalid-key");

    var exception = assertThrows(EmailSettingsException.class, () ->
      SmtpConfigurationValueVerifier.verify(value));

    assertTrue(exception.getMessage().startsWith("Unrecognized field \"1\""));
    assertTrue(exception.getError().getParameters().isEmpty());
  }


  @Test
  public void constructor_isPrivateAndThrows() throws Exception {
    var ctor = SmtpConfigurationValueVerifier.class.getDeclaredConstructor();
    assertTrue(Modifier.isPrivate(ctor.getModifiers()));

    ctor.setAccessible(true);

    var exception = assertThrows(InvocationTargetException.class, ctor::newInstance);
    assertTrue(exception.getCause() instanceof IllegalStateException);
    assertEquals("Utility class cannot be initialized", exception.getCause().getMessage());
  }
}
