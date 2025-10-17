package org.folio.rest.impl;

import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;
import org.apache.http.HttpStatus;
import org.folio.rest.impl.base.AbstractAPITest;
import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static java.lang.String.format;
import static org.folio.matchers.JsonMatchers.matchesJson;
import static org.folio.util.StubUtils.buildEmailSetting;

public class EmailSettingsAPITest extends AbstractAPITest {
  public static final String ITEM_PATH_TEMPLATE = "%s/%s";

  @Test
  public void postEmailSettings_positive() {
    JsonObject emailSetting = buildEmailSetting();

    post(REST_PATH_EMAIL_SETTINGS, emailSetting.encodePrettily())
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .body(matchesJson(emailSetting, List.of("metadata")));
  }

  @Test
  public void getEmailSettings_positive() {
    JsonObject emailSetting = buildEmailSetting();

    Response postResponse = post(REST_PATH_EMAIL_SETTINGS, emailSetting.encodePrettily())
      .then()
      .extract()
      .response();

    JsonObject postResponseJson = new JsonObject(postResponse.body().asString());
    JsonObject emailSettings = new JsonObject()
      .put("settings", List.of(postResponseJson))
      .put("totalRecords", 1);

    get(REST_PATH_EMAIL_SETTINGS)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body(matchesJson(emailSettings, List.of("metadata")));
  }

  @Test
  public void getById_positive() {
    JsonObject emailSetting = buildEmailSetting();

    Response postResponse = post(REST_PATH_EMAIL_SETTINGS, emailSetting.encodePrettily())
      .then()
      .extract()
      .response();

    String postResponseId = new JsonObject(postResponse.body().asString()).getString("id");

    get(pathToEmailSettingById(postResponseId))
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body(matchesJson(emailSetting.put("id", postResponseId), List.of("metadata")));
  }

  @Test
  public void getById_negative_idPathParameterIsWrong() {
    JsonObject emailSetting = buildEmailSetting();

    post(REST_PATH_EMAIL_SETTINGS, emailSetting.encodePrettily());

    get(pathToEmailSettingById(UUID.randomUUID().toString()))
      .then()
      .statusCode(HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void putById_positive() {
    JsonObject emailSetting = buildEmailSetting();

    Response postResponse = post(REST_PATH_EMAIL_SETTINGS, emailSetting.encodePrettily())
      .then()
      .extract()
      .response();

    String postResponseId = new JsonObject(postResponse.body().asString()).getString("id");

    JsonObject emailSettingToPut = emailSetting
      .copy()
      .put("id", postResponseId)
      .put("value", "updated-reply-to@folio.org");

    put(pathToEmailSettingById(postResponseId), emailSettingToPut.encodePrettily())
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT);

    get(pathToEmailSettingById(postResponseId))
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body(matchesJson(emailSettingToPut, List.of("metadata")));
  }

  @Test
  public void putById_negative_idPathParameterIsWrong() {
    JsonObject emailSetting = buildEmailSetting();

    Response postResponse = post(REST_PATH_EMAIL_SETTINGS, emailSetting.encodePrettily())
      .then()
      .extract()
      .response();

    String postResponseId = new JsonObject(postResponse.body().asString()).getString("id");

    JsonObject emailSettingToPut = emailSetting
      .copy()
      .put("id", postResponseId);

    String idPathParameter = UUID.randomUUID().toString();

    put(pathToEmailSettingById(idPathParameter),
      emailSettingToPut.encodePrettily())
      .then()
      .statusCode(HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void deleteById_positive() {
    JsonObject emailSetting = buildEmailSetting();

    Response postResponse = post(REST_PATH_EMAIL_SETTINGS, emailSetting.encodePrettily())
      .then()
      .extract()
      .response();

    String postResponseId = new JsonObject(postResponse.body().asString()).getString("id");

    delete(pathToEmailSettingById(postResponseId))
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT);

    get(pathToEmailSettingById(postResponseId))
      .then()
      .statusCode(HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void deleteById_negative_idPathParameterIsWrong() {
    JsonObject emailSetting = buildEmailSetting();

    Response postResponse = post(REST_PATH_EMAIL_SETTINGS, emailSetting.encodePrettily())
      .then()
      .extract()
      .response();

    String postResponseId = new JsonObject(postResponse.body().asString()).getString("id");

    String idPathParameter = UUID.randomUUID().toString();

    delete(pathToEmailSettingById(idPathParameter))
      .then()
      .statusCode(HttpStatus.SC_NOT_FOUND);

    get(pathToEmailSettingById(postResponseId))
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body(matchesJson(emailSetting.put("id", postResponseId), List.of("metadata")));
  }

  @Test
  public void getEmailSettings_positive_queryByScope() {
    JsonObject emailSetting1 = buildEmailSetting( "reply-to", "noreply@folio.org", "Reply-To address");
    JsonObject emailSetting2 = buildEmailSetting("sender", "admin@folio.org", "Sender address");

    post(REST_PATH_EMAIL_SETTINGS, emailSetting1.encodePrettily());
    post(REST_PATH_EMAIL_SETTINGS, emailSetting2.encodePrettily());

    get(REST_PATH_EMAIL_SETTINGS + "?query=scope==email")
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract()
      .response();
  }

  @Test
  public void getEmailSettings_positive_queryByKey() {
    JsonObject emailSetting1 = buildEmailSetting("reply-to", "noreply@folio.org", "Reply-To address");
    JsonObject emailSetting2 = buildEmailSetting("sender", "admin@folio.org", "Sender address");

    post(REST_PATH_EMAIL_SETTINGS, emailSetting1.encodePrettily());
    post(REST_PATH_EMAIL_SETTINGS, emailSetting2.encodePrettily());

    get(REST_PATH_EMAIL_SETTINGS + "?query=key==reply-to")
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract()
      .response();
  }

  @Test
  public void postEmailSettings_negative_withInvalidData() {
    JsonObject invalidEmailSetting = new JsonObject()
      .put("scope", "")  // Empty scope should be invalid
      .put("key", "test-key")
      .put("value", "test-value");

    post(REST_PATH_EMAIL_SETTINGS, invalidEmailSetting.encodePrettily())
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY);
  }

  private String pathToEmailSettingById(String id) {
    return format(ITEM_PATH_TEMPLATE, REST_PATH_EMAIL_SETTINGS, id);
  }
}
