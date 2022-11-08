package org.folio.rest.impl;

import static java.lang.String.format;
import static org.folio.matchers.JsonMatchers.matchesJson;
import static org.folio.util.StubUtils.buildSmtpConfiguration;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.UUID;

import org.apache.http.HttpStatus;
import org.folio.rest.impl.base.AbstractAPITest;
import org.junit.Test;

import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;

public class SmtpConfigurationTest extends AbstractAPITest {
  public static final String ITEM_PATH_TEMPLATE = "%s/%s";

  @Test
  public void postSmtpConfigurationWithIdShouldSucceed() {
    String id = UUID.randomUUID().toString();

    JsonObject smtpConfiguration = buildSmtpConfiguration().put("id", id);

    Response postResponse = post(REST_PATH_SMTP_CONFIGURATION, smtpConfiguration.encodePrettily())
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .extract()
      .response();

    assertEquals(new JsonObject(postResponse.body().asString()).getString("id"), id);
  }

  @Test
  public void smtpConfigurationPost() {
    JsonObject smtpConfiguration = buildSmtpConfiguration();

    post(REST_PATH_SMTP_CONFIGURATION, smtpConfiguration.encodePrettily())
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .body(matchesJson(smtpConfiguration, List.of("id", "metadata")));
  }

  @Test
  public void smtpConfigurationGetAll() {
    JsonObject smtpConfiguration = buildSmtpConfiguration();

    Response postResponse = post(REST_PATH_SMTP_CONFIGURATION, smtpConfiguration.encodePrettily())
      .then()
      .extract()
      .response();

    JsonObject postResponseJson = new JsonObject(postResponse.body().asString());
    JsonObject smtpConfigurations = new JsonObject()
      .put("smtpConfigurations", List.of(postResponseJson))
      .put("totalRecords", 1);

    get(REST_PATH_SMTP_CONFIGURATION)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body(matchesJson(smtpConfigurations, List.of("metadata")));
  }

  @Test
  public void smtpConfigurationGetById() {
    JsonObject smtpConfiguration = buildSmtpConfiguration();

    Response postResponse = post(REST_PATH_SMTP_CONFIGURATION, smtpConfiguration.encodePrettily())
      .then()
      .extract()
      .response();

    String postResponseId = new JsonObject(postResponse.body().asString()).getString("id");

    get(pathToSmtpConfigurationById(postResponseId))
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body(matchesJson(smtpConfiguration.put("id", postResponseId), List.of("metadata")));
  }

  @Test
  public void smtpConfigurationGetByIdShouldReturn404WhenIdIsWrong() {
    JsonObject smtpConfiguration = buildSmtpConfiguration();

    post(REST_PATH_SMTP_CONFIGURATION, smtpConfiguration.encodePrettily());

    get(pathToSmtpConfigurationById(UUID.randomUUID().toString()))
      .then()
      .statusCode(HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void smtpConfigurationPut() {
    JsonObject smtpConfiguration = buildSmtpConfiguration();

    Response postResponse = post(REST_PATH_SMTP_CONFIGURATION, smtpConfiguration.encodePrettily())
      .then()
      .extract()
      .response();

    String postResponseId = new JsonObject(postResponse.body().asString()).getString("id");

    JsonObject smtpConfigurationToPut = smtpConfiguration
      .copy()
      .put("id", postResponseId)
      .put("username", "updated-username");

    put(pathToSmtpConfigurationById(postResponseId), smtpConfigurationToPut.encodePrettily())
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT);

    get(pathToSmtpConfigurationById(postResponseId))
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body(matchesJson(smtpConfigurationToPut, List.of("metadata")));
  }

  @Test
  public void smtpConfigurationPutShouldReturn404WhenIdPathParameterIsWrong() {
    JsonObject smtpConfiguration = buildSmtpConfiguration();

    Response postResponse = post(REST_PATH_SMTP_CONFIGURATION, smtpConfiguration.encodePrettily())
      .then()
      .extract()
      .response();

    String postResponseId = new JsonObject(postResponse.body().asString()).getString("id");

    JsonObject smtpConfigurationToPut = smtpConfiguration
      .copy()
      .put("id", postResponseId);

    String idPathParameter = UUID.randomUUID().toString();

    put(pathToSmtpConfigurationById(idPathParameter),
      smtpConfigurationToPut.encodePrettily())
      .then()
      .statusCode(HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void smtpConfigurationDelete() {
    JsonObject smtpConfiguration = buildSmtpConfiguration();

    Response postResponse = post(REST_PATH_SMTP_CONFIGURATION, smtpConfiguration.encodePrettily())
      .then()
      .extract()
      .response();

    String postResponseId = new JsonObject(postResponse.body().asString()).getString("id");

    delete(pathToSmtpConfigurationById(postResponseId))
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT);

    get(pathToSmtpConfigurationById(postResponseId))
      .then()
      .statusCode(HttpStatus.SC_NOT_FOUND);
  }

  @Test
  public void smtpConfigurationDeleteShouldReturn404WhenIdPathParameterIsWrong() {
    JsonObject smtpConfiguration = buildSmtpConfiguration();

    Response postResponse = post(REST_PATH_SMTP_CONFIGURATION, smtpConfiguration.encodePrettily())
      .then()
      .extract()
      .response();

    String postResponseId = new JsonObject(postResponse.body().asString()).getString("id");

    String idPathParameter = UUID.randomUUID().toString();

    delete(pathToSmtpConfigurationById(idPathParameter))
      .then()
      .statusCode(HttpStatus.SC_NOT_FOUND);

    get(pathToSmtpConfigurationById(postResponseId))
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body(matchesJson(smtpConfiguration.put("id", postResponseId), List.of("metadata")));;
  }

  @Test
  public void attemptToPostMoreThanOneSmtpConfigurationShouldFail() {
    post(REST_PATH_SMTP_CONFIGURATION, buildSmtpConfiguration().encodePrettily())
      .then()
      .statusCode(HttpStatus.SC_CREATED);

    Response response = post(REST_PATH_SMTP_CONFIGURATION, buildSmtpConfiguration().encodePrettily())
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .extract()
      .response();

    String errorMessage = new JsonObject(response.body().asString())
      .getJsonArray("errors")
      .getJsonObject(0)
      .getString("message");

    assertTrue(errorMessage.contains("value already exists in table smtp_configuration"));
  }

  private String pathToSmtpConfigurationById(String id) {
    return format(ITEM_PATH_TEMPLATE, REST_PATH_SMTP_CONFIGURATION, id);
  }
}
