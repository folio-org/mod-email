package org.folio.rest.impl;

import static org.folio.rest.jaxrs.model.EmailEntity.Status.DELIVERED;
import static org.folio.rest.jaxrs.model.EmailEntity.Status.FAILURE;
import static org.folio.util.StubUtils.getIncorrectConfigurations;
import static org.folio.util.StubUtils.getIncorrectWiserMockConfigurations;
import static org.folio.util.StubUtils.getWiserMockConfigurations;
import static org.folio.util.StubUtils.initModConfigStub;
import static org.junit.Assert.assertEquals;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.folio.rest.impl.base.AbstractAPITest;
import org.folio.rest.jaxrs.model.EmailEntity;
import org.junit.Test;

import io.restassured.response.Response;

public class GettingMetricsTest extends AbstractAPITest {

  @Test
  public void testDelayedTaskExpiredEmailWithDateAndDeliveredStatus() {
    Response response = getEmails(DELIVERED.value())
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
    Response response = getEmails(FAILURE.value())
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
    EmailEntity emailOne = sendEmails(statusCode);
    EmailEntity emailTwo = sendEmails(statusCode);
    EmailEntity emailThree = sendEmails(statusCode);

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
    Response response = getEmails(DELIVERED.value())
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
    EmailEntity emailOne = sendEmails(statusCode);
    EmailEntity emailTwo = sendEmails(statusCode);
    EmailEntity emailThree = sendEmails(statusCode);

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
    Response response = getEmails(FAILURE.value())
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
    EmailEntity email = sendEmails(500);

    // check email on DB
    checkStoredEmailsInDb(email, FAILURE);

    // delete all failure email
    deleteEmailByDateAndStatus(generateExpirationDate(), FAILURE.value())
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT)
      .extract()
      .response();

    // find all failure email
    Response response = getEmails(FAILURE.value())
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract()
      .response();

    List<EmailEntity> actualEntries = convertEntriesToJson(response).getEmailEntity();
    assertEquals(0, actualEntries.size());
  }
}
