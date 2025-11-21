package org.folio.rest.impl;

import static org.folio.rest.jaxrs.model.EmailEntity.Status.DELIVERED;
import static org.folio.rest.jaxrs.model.EmailEntity.Status.FAILURE;
import static org.folio.util.StubUtils.buildSmtpConfiguration;
import static org.folio.util.StubUtils.buildWiserEmailSettings;
import static org.folio.util.StubUtils.buildWiserSmtpConfiguration;
import static org.folio.util.StubUtils.getIncorrectConfigurations;
import static org.folio.util.StubUtils.getIncorrectWiserMockConfigurations;
import static org.folio.util.StubUtils.getWiserMockConfigurations;
import static org.folio.util.StubUtils.initModConfigStub;
import static org.junit.Assert.assertEquals;

import java.util.List;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.folio.rest.impl.base.AbstractAPITest;
import org.folio.rest.jaxrs.model.EmailEntity;
import org.junit.Test;

import io.restassured.response.Response;

public class GettingMetricsTest extends AbstractAPITest {

  @Test
  public void testDelayedTaskExpiredEmailWithDateAndDeliveredStatus() {
    Response response = getEmails(DELIVERED)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract()
      .response();

    List<EmailEntity> actualEntries = convertEntriesToJson(response).getEmailEntity();
    assertEquals(0, actualEntries.size());

    deleteEmailByDateAndStatus(generateExpirationDate(), DELIVERED.value())
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT)
      .extract()
      .response();
  }

  @Test
  public void testDelayedTaskExpiredEmailWithDateAndFailureStatus() {
    Response response = getEmails(FAILURE)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract()
      .response();

    List<EmailEntity> actualEntries = convertEntriesToJson(response).getEmailEntity();
    assertEquals(0, actualEntries.size());

    deleteEmailByDateAndStatus(generateExpirationDate(), FAILURE.value())
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT)
      .extract()
      .response();
  }

  @Test
  public void testDelayedTasks() {
    String emptyVal = StringUtils.EMPTY;
    String incorrectDate = "INCORRECT_DATE";

    deleteEmailByDateAndStatus(generateExpirationDate(), emptyVal)
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT)
      .extract()
      .response();

    deleteEmailByDateAndStatus(incorrectDate, emptyVal)
      .then()
      .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR)
      .extract()
      .response();

    deleteEmailByDateAndStatus(emptyVal, FAILURE.value())
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT)
      .extract()
      .response();

    deleteEmailByDateAndStatus(emptyVal, incorrectDate)
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT)
      .extract()
      .response();
  }

  @Test
  public void testSendEmailsWithDeliveredStatus() {
    int mockServerPort = userMockServer.port();
    initModConfigStub(mockServerPort, getWiserMockConfigurations());

    int statusCode = 200;
    EmailEntity emailOne = sendEmail(statusCode);
    EmailEntity emailTwo = sendEmail(statusCode);
    EmailEntity emailThree = sendEmail(statusCode);

    // check email on DB
    checkStoredEmailsInDb(emailOne, DELIVERED);
    checkStoredEmailsInDb(emailTwo, DELIVERED);
    checkStoredEmailsInDb(emailThree, DELIVERED);

    // delete all delivered email
    deleteEmailByDateAndStatus(generateExpirationDate(), DELIVERED.value())
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT)
      .extract()
      .response();

    // find all delivered email
    Response response = getEmails(DELIVERED)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract()
      .response();

    List<EmailEntity> actualEntries = convertEntriesToJson(response).getEmailEntity();
    assertEquals(0, actualEntries.size());
  }

  @Test
  public void testSendEmailsWithFailureStatus() {
    int mockServerPort = userMockServer.port();
    initModConfigStub(mockServerPort, getIncorrectWiserMockConfigurations());

    int statusCode = 200;
    EmailEntity emailOne = sendEmail(statusCode);
    EmailEntity emailTwo = sendEmail(statusCode);
    EmailEntity emailThree = sendEmail(statusCode);

    // check email on DB
    checkStoredEmailsInDb(emailOne, FAILURE);
    checkStoredEmailsInDb(emailTwo, FAILURE);
    checkStoredEmailsInDb(emailThree, FAILURE);

    // delete all failure email
    deleteEmailByDateAndStatus(generateExpirationDate(), FAILURE.value())
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT)
      .extract()
      .response();

    // find all failure email
    Response response = getEmails(FAILURE)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract()
      .response();

    List<EmailEntity> actualEntries = convertEntriesToJson(response).getEmailEntity();
    assertEquals(0, actualEntries.size());
  }

  @Test
  public void testSendEmailWithIncorrectConfigurations() {
    int mockServerPort = userMockServer.port();
    initModConfigStub(mockServerPort, getIncorrectConfigurations());

    // send email
    EmailEntity email = sendEmail(200);

    // check email on DB
    checkStoredEmailsInDb(email, FAILURE);

    // delete all failure email
    deleteEmailByDateAndStatus(generateExpirationDate(), FAILURE.value())
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT)
      .extract()
      .response();

    // find all failure email
    Response response = getEmails(FAILURE)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract()
      .response();

    List<EmailEntity> actualEntries = convertEntriesToJson(response).getEmailEntity();
    assertEquals(0, actualEntries.size());
  }

  @Test
  public void testEmailExpiryWithExpirationTimeAndCorrectConfig() {
    JsonObject smtpConfiguration = buildWiserEmailSettings();
    // For testing purpose, expiration hours set to 0. So that the email gets deleted immediately
    smtpConfiguration.getJsonObject("value").put("expirationHours", 0);

    Response postResponse = post(REST_PATH_MAIL_SETTINGS, smtpConfiguration.encodePrettily())
      .then()
      .extract()
      .response();

    String postResponseId = new JsonObject(postResponse.body().asString()).getString("id");

    int mockServerPort = userMockServer.port();
    initModConfigStub(mockServerPort, getWiserMockConfigurations());

    int statusCode = 200;
    EmailEntity emailOne = sendEmail(statusCode);

    // check email on DB
    checkStoredEmailsInDb(emailOne, DELIVERED);

    // This will delete the delivered email as the expiration hours set to 0
    deleteEmailByDateAndStatus(StringUtils.EMPTY, StringUtils.EMPTY)
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT)
      .extract()
      .response();

    // find all delivered email
    Response response = getEmails(DELIVERED)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract()
      .response();

    List<EmailEntity> actualEntries = convertEntriesToJson(response).getEmailEntity();
    assertEquals(0, actualEntries.size());

    // For testing purpose, expiration hours set to 1. So that the email gets deleted only after 1 hour
    smtpConfiguration.getJsonObject("value").put("expirationHours", 1);
    put(REST_PATH_MAIL_SETTINGS + "/" + postResponseId, smtpConfiguration.encodePrettily())
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT);

    EmailEntity emailTwo = sendEmail(statusCode);
    checkStoredEmailsInDb(emailTwo, DELIVERED);

    // This will delete the delivered email only after 1 hour
    deleteEmailByDateAndStatus(StringUtils.EMPTY, StringUtils.EMPTY)
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT)
      .extract()
      .response();

    // find all delivered email
    response = getEmails(DELIVERED)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract()
      .response();

    actualEntries = convertEntriesToJson(response).getEmailEntity();
    assertEquals(1, actualEntries.size());
  }

  @Test
  public void testEmailExpiryWithExpirationTimeAndInCorrectConfig() {
    // For testing purpose, expiration hours set to 0. So that the email gets deleted immediately
    JsonObject smtpConfiguration = buildSmtpConfiguration();
    post(REST_PATH_SMTP_CONFIGURATION, smtpConfiguration.encodePrettily())
      .then()
      .statusCode(HttpStatus.SC_CREATED);

    int mockServerPort = userMockServer.port();
    int statusCode = 200;
    initModConfigStub(mockServerPort, getIncorrectWiserMockConfigurations());

    EmailEntity emailTwo0 = sendEmail(statusCode);
    checkStoredEmailsInDb(emailTwo0, FAILURE);

    // This will delete the delivered email
    deleteEmailByDateAndStatus(StringUtils.EMPTY, StringUtils.EMPTY)
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT)
      .extract()
      .response();

    // find all failure email
    Response response = getEmails(FAILURE)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract()
      .response();

    List<EmailEntity> actualEntries = convertEntriesToJson(response).getEmailEntity();
    assertEquals(1, actualEntries.size());
  }
}
