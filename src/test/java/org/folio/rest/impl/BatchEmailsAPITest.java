package org.folio.rest.impl;

import io.restassured.response.Response;
import org.apache.http.HttpStatus;
import org.folio.rest.impl.base.AbstractBatchAPITest;
import org.folio.rest.jaxrs.model.EmailEntity;
import org.folio.rest.jaxrs.model.EmailEntity.Status;
import org.folio.rest.jaxrs.model.EmailEntries;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

import static org.folio.util.StubUtils.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BatchEmailsAPITest extends AbstractBatchAPITest {

  @Test
  public void saveBatchAndGetAllEmailsMessagesFullConfig() {
    int batchSize = 500;
    String messageStatus = getStatus(Status.NEW);
    int mockServerPort = userMockServer.port();
    initModConfigStub(mockServerPort, getFullWiserMockConfigurations());

    // save batch emails
    List<EmailEntity> expectedEntries = generateEntityListWithoutSender(batchSize);
    postEmailsMessages(String.format(OKAPI_URL_TEMPLATE, mockServerPort), expectedEntries)
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT);

    // find all emails
    Response response = getEmailsMessages(String.format(OKAPI_URL_TEMPLATE, mockServerPort), messageStatus, batchSize)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract()
      .response();

    List<EmailEntity> actualEntries = convertEntriesToJson(response).getEmailEntity();
    assertEquals(expectedEntries.size(), actualEntries.size());

    assertTrue(getEntryList(actualEntries, getSenders).contains(DEFAULT_SENDER));
    assertEquals(getEntryList(expectedEntries, getRecipients), getEntryList(actualEntries, getRecipients));
  }

  @Test
  public void saveBatchAndGetAllEmailsMessages() {
    int batchSize = 500;
    String messageStatus = getStatus(Status.NEW);
    int mockServerPort = userMockServer.port();
    initModConfigStub(mockServerPort, getWiserMockConfigurations());

    // save batch emails
    List<EmailEntity> expectedEntries = generateEntityList(batchSize);
    postEmailsMessages(String.format(OKAPI_URL_TEMPLATE, mockServerPort), expectedEntries)
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT);

    // find all emails
    Response response = getEmailsMessages(String.format(OKAPI_URL_TEMPLATE, mockServerPort), messageStatus, batchSize)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract()
      .response();

    List<EmailEntity> actualEntries = convertEntriesToJson(response).getEmailEntity();
    assertEquals(expectedEntries.size(), actualEntries.size());

    assertEquals(getEntryList(expectedEntries, getSenders), getEntryList(actualEntries, getSenders));
    assertEquals(getEntryList(expectedEntries, getRecipients), getEntryList(actualEntries, getRecipients));
  }

  @Test
  public void testFailureStatusEmailsMessages() {
    int batchSize = 50;
    String messageStatus = getStatus(Status.FAILURE);
    int mockServerPort = userMockServer.port();
    initModConfigStub(mockServerPort, getIncorrectWiserMockConfigurations());

    // save batch emails
    postEmailsMessages(String.format(OKAPI_URL_TEMPLATE, mockServerPort), generateEntityList(batchSize))
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT);

    // run trigger send emails
    runSendEmailTrigger(String.format(OKAPI_URL_TEMPLATE, mockServerPort))
      .then()
      .statusCode(HttpStatus.SC_OK);

    // find all emails with FAILURE status
    Response response = getEmailsMessages(String.format(OKAPI_URL_TEMPLATE, mockServerPort), messageStatus, batchSize)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract()
      .response();

    // the trigger processes only 50 per call
    EmailEntries entries = convertEntriesToJson(response);
    assertEquals(entries.getTotalRecords(), Integer.valueOf(batchSize));
  }

  @Test
  public void getAllEmailsMessages() {
    int mockServerPort = userMockServer.port();
    String messageStatus = getStatus(Status.NEW);
    Response response = getEmailsMessages(String.format(OKAPI_URL_TEMPLATE, mockServerPort), messageStatus, 10)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract()
      .response();

    EmailEntries entries = convertEntriesToJson(response);
    assertEquals(entries.getTotalRecords(), Integer.valueOf(0));
    assertTrue(entries.getEmailEntity().isEmpty());
  }

  @Test
  public void checkSendingEmailWithDifferentConfigs() {
    int mockServerPort = userMockServer.port();
    int batchSize = 50;

    // init incorrect SMTP mock configuration
    initModConfigStub(mockServerPort, getIncorrectWiserMockConfigurations());

    // save batch emails
    postEmailsMessages(String.format(OKAPI_URL_TEMPLATE, mockServerPort), generateEntityList(batchSize))
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT);

    // run trigger send emails
    runSendEmailTrigger(String.format(OKAPI_URL_TEMPLATE, mockServerPort))
      .then()
      .statusCode(HttpStatus.SC_OK);

    // find all emails with FAILURE status
    String failureStatus = getStatus(Status.FAILURE);
    Response response = getEmailsMessages(String.format(OKAPI_URL_TEMPLATE, mockServerPort), failureStatus, batchSize)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract()
      .response();

    // the trigger processes only 50 per call
    EmailEntries entries = convertEntriesToJson(response);
    assertEquals(entries.getTotalRecords(), Integer.valueOf(batchSize));

    // init correct SMTP mock configuration and check it again
    initModConfigStub(mockServerPort, getWiserMockConfigurations());
    List<EmailEntity> expectedEntries = generateEntityList(batchSize);
    checkDeliveredMessages(batchSize, mockServerPort, expectedEntries);
  }

  @Test
  public void testDeliveredStatusEmailsMessages() {
    int batchSize = ThreadLocalRandom.current().nextInt(0, 50);
    int mockServerPort = userMockServer.port();
    initModConfigStub(mockServerPort, getWiserMockConfigurations());
    List<EmailEntity> expectedEntries = generateEntityList(batchSize);

    checkDeliveredMessages(batchSize, mockServerPort, expectedEntries);
  }

  @Test
  public void testEmptyEmailsMessages() {
    int batchSize = 0;
    int mockServerPort = userMockServer.port();
    initModConfigStub(mockServerPort, getWiserMockConfigurations());
    List<EmailEntity> expectedEntries = generateEntityList(batchSize);

    checkDeliveredMessages(batchSize, mockServerPort, expectedEntries);
  }

  @Test
  public void testRunTriggerSeveralTimes() {
    int batchSize = 0;
    int mockServerPort = userMockServer.port();
    initModConfigStub(mockServerPort, getWiserMockConfigurations());
    List<EmailEntity> expectedEntries = generateEntityList(batchSize);

    // save batch emails
    postEmailsMessages(String.format(OKAPI_URL_TEMPLATE, mockServerPort), expectedEntries)
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT);

    IntStream.range(0, 50).forEach(r ->
      runSendEmailTrigger(String.format(OKAPI_URL_TEMPLATE, mockServerPort))
        .then()
        .statusCode(HttpStatus.SC_OK));
  }
}
