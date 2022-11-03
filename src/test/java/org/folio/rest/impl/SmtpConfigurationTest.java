package org.folio.rest.impl;

import static org.folio.matchers.JsonMatchers.matchesJson;
import static org.folio.util.StubUtils.buildSmtpConfiguration;

import java.util.List;
import java.util.UUID;

import org.apache.http.HttpStatus;
import org.folio.rest.impl.base.AbstractAPITest;
import org.junit.Test;

import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;

public class SmtpConfigurationTest extends AbstractAPITest {

  @Test
  public void postSmtpConfigurationWithId() {
    JsonObject smtpConfiguration = buildSmtpConfiguration()
      .put("id", UUID.randomUUID().toString());

    post(REST_PATH_SMTP_CONFIGURATION, smtpConfiguration.encodePrettily())
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .body(matchesJson(smtpConfiguration, List.of("metadata")));
  }

  @Test
  public void smtpConfigurationCrud() {
    JsonObject smtpConfiguration = buildSmtpConfiguration();

    post(REST_PATH_SMTP_CONFIGURATION, smtpConfiguration.encodePrettily())
      .then()
      .statusCode(HttpStatus.SC_CREATED)
      .body(matchesJson(smtpConfiguration, List.of("id", "metadata")));

    Response getResponse = get(REST_PATH_SMTP_CONFIGURATION)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body(matchesJson(smtpConfiguration, List.of("id", "metadata")))
      .extract()
      .response();

    String id = new JsonObject(getResponse.body().asString()).getString("id");

    // PUT without an ID should succeed
    JsonObject updatedSmtpConfiguration = smtpConfiguration
      .copy()
      .put("username", "updated-username");
    put(REST_PATH_SMTP_CONFIGURATION, updatedSmtpConfiguration.encodePrettily())
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body(matchesJson(updatedSmtpConfiguration, List.of("id", "metadata")));

    // PUT with correct ID should succeed
    JsonObject updatedSmtpConfigurationWithId = smtpConfiguration
      .copy()
      .put("username", "updated-username-2")
      .put("id", id);
    put(REST_PATH_SMTP_CONFIGURATION, updatedSmtpConfigurationWithId.encodePrettily())
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body(matchesJson(updatedSmtpConfigurationWithId, List.of("metadata")));

    // PUT with wrong ID should return 404
    put(REST_PATH_SMTP_CONFIGURATION,
      updatedSmtpConfiguration.put("id", UUID.randomUUID().toString()).encodePrettily())
      .then()
      .statusCode(HttpStatus.SC_NOT_FOUND);

    get(REST_PATH_SMTP_CONFIGURATION)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body(matchesJson(updatedSmtpConfigurationWithId, List.of("metadata")));

    delete(REST_PATH_SMTP_CONFIGURATION)
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT);

    get(REST_PATH_SMTP_CONFIGURATION)
      .then()
      .statusCode(HttpStatus.SC_NOT_FOUND);
  }

}
