package org.folio.rest.impl;

import static junit.framework.TestCase.assertTrue;
import static org.folio.util.StubUtils.buildInvalidEmailSettings;
import static org.folio.util.StubUtils.buildInvalidSmtpConfiguration;
import static org.folio.util.StubUtils.getEmailEntity;
import static org.hamcrest.core.StringContains.containsString;

import org.apache.http.HttpStatus;
import org.folio.rest.impl.base.AbstractAPITest;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.restassured.response.Response;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class EmailAPITest extends AbstractAPITest {

  @Test
  public void checkIncorrectSmtpConfiguration() {
    post(REST_PATH_SMTP_CONFIGURATION, buildInvalidSmtpConfiguration().encodePrettily());

    String okapiEmailEntity = getEmailEntity("user@user.com", "admin@admin.com", null);
    String expectedErrMsg = "Invalid config for SMTP server";

    post(REST_PATH_EMAIL, okapiEmailEntity)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body(containsString(expectedErrMsg));
  }

  @Test
  public void checkFallbackToSmtpConfigWhenSettingsIncorrect() {
    post(REST_PATH_EMAIL_SETTINGS, buildInvalidEmailSettings().encodePrettily());
    post(REST_PATH_SMTP_CONFIGURATION, buildInvalidSmtpConfiguration().encodePrettily());

    String okapiEmailEntity = getEmailEntity("user@user.com", "admin@admin.com", null);
    String expectedErrMsg = "Invalid config for SMTP server";

    post(REST_PATH_EMAIL, okapiEmailEntity)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body(containsString(expectedErrMsg));
  }

  @Test
  public void shouldReturnFailedResultWhenRequestWithoutJson() {
    String expectedResponse = "The object to be validated must not be null";

    post(REST_PATH_EMAIL)
      .then()
      .statusCode(HttpStatus.SC_BAD_REQUEST)
      .body(containsString(expectedResponse));
  }

  @Test
  public void shouldReturnFailedResultWhenRequestWithIncorrectEmailEntity() {
    String expectedResponse = "\"message\":\"must not be null\"";
    String okapiEmailEntity = getEmailEntity(null, "admin@admin.com", null);

    Response response = post(REST_PATH_EMAIL, okapiEmailEntity)
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .extract()
      .response();

    assertTrue(response.asString().contains(expectedResponse));
  }

}
