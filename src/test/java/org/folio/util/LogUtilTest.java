package org.folio.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.folio.rest.jaxrs.model.EmailEntity;
import org.folio.rest.jaxrs.model.EmailEntries;
import org.junit.Test;

import io.vertx.core.json.JsonObject;

public class LogUtilTest {
  @Test
  public void asJsonShouldReturnNullIfCallWithNull() {
    assertNull(LogUtil.asJson(null));
  }

  @Test
  public void asJsonShouldReturnStringValueOfJson() {
    JsonObject json = new JsonObject()
      .put("key", "value");
    assertEquals("{\"key\":\"value\"}", LogUtil.asJson(json));
  }

  @Test
  public void asJsonShouldReturnStringValueOfNumbers() {
    assertEquals("1", LogUtil.asJson(1));
    assertEquals("1.123", LogUtil.asJson(1.123));
  }

  @Test
  public void asJsonShouldReturnStringValueOfString() {
    assertEquals("string", LogUtil.asJson("string"));
  }

  @Test
  public void asJsonShouldReturnNullIfCallWithNullAndSizeValue() {
    assertNull(LogUtil.asJson(null, 10));
  }

  @Test
  public void asJsonShouldReturnNullWhenObjectIsNotSerializable() {
    assertNull(LogUtil.asJson(new HashSet<>()));
  }

  @Test
  public void asJsonShouldReturnStringRepresentationOfListOfIntegers() {
    assertEquals("list(size: 3, elements: [1, 2, 3])", LogUtil.asJson(List.of(1, 2, 3)));
  }

  @Test
  public void asJsonShouldReturnStringRepresentationOfListOfObjects() {
    assertEquals("list(size: 3, elements: [1, 2.2, string])", LogUtil.asJson(List.of(1, 2.2, "string")));
  }

  @Test
  public void headersAsStringShouldRemoveOkapiTokenAndReturnRepresentation() {
    Map<String, String> okapiHeaders = new HashMap<>();
    okapiHeaders.put("X-Okapi-Tenant", "testTenant");
    okapiHeaders.put("X-Okapi-Token", "token");
    okapiHeaders.put("X-Okapi-Url", "url");
    assertEquals("{x-okapi-tenant=testTenant, x-okapi-url=url}", LogUtil.headersAsString(okapiHeaders));
  }

  @Test
  public void emailIdsAsStringShouldReturnEmailsRepresentationOfEmailEntries() {
    String emailId = UUID.randomUUID().toString();
    EmailEntity emailEntity = new EmailEntity()
      .withId(emailId)
      .withFrom("test sender")
      .withTo("test recipient")
      .withHeader("header")
      .withBody("test message");

    EmailEntries emailEntries = new EmailEntries().withEmailEntity(List.of(emailEntity));
    assertEquals(emailId, LogUtil.emailIdsAsString(emailEntries));
  }

  @Test
  public void emailIdsAsStringShouldReturnNullIfEmailsNull() {
    assertNull(LogUtil.emailIdsAsString((Collection<EmailEntity>) null));
  }

  @Test
  public void emailIdsAsStringShouldReturnNullIfEmailEntriesNull() {
    assertNull(LogUtil.emailIdsAsString((EmailEntries) null));
  }

  @Test
  public void smtpConfigAsJsonShouldReturnNullIfConfigNull() {
    assertNull(LogUtil.smtpConfigAsJson(null));
  }

  @Test
  public void emailAsJsonShouldReturnNullIfEmailIsNull() {
    assertNull(LogUtil.emailAsJson(null));
  }

}
