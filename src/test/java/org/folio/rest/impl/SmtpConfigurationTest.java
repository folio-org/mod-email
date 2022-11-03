package org.folio.rest.impl;

import static org.folio.matchers.JsonMatchers.matchesJson;
import static org.folio.util.StubUtils.buildSmtpConfiguration;

import java.util.List;
import java.util.UUID;

import org.apache.http.HttpStatus;
import org.folio.rest.impl.base.AbstractAPITest;
import org.junit.Test;

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

    get(REST_PATH_SMTP_CONFIGURATION)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body(matchesJson(smtpConfiguration, List.of("id", "metadata")));

    JsonObject updatedSmtpConfiguration = smtpConfiguration.put("username", "updated-username");
    put(REST_PATH_SMTP_CONFIGURATION, updatedSmtpConfiguration.encodePrettily())
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body(matchesJson(updatedSmtpConfiguration, List.of("id", "metadata")));

    get(REST_PATH_SMTP_CONFIGURATION)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body(matchesJson(updatedSmtpConfiguration, List.of("id", "metadata")));

    delete(REST_PATH_SMTP_CONFIGURATION)
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT);

    get(REST_PATH_SMTP_CONFIGURATION)
      .then()
      .statusCode(HttpStatus.SC_NOT_FOUND);
  }

}
