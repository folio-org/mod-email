package org.folio.rest.impl;

import io.restassured.response.Response;
import org.apache.http.HttpStatus;
import org.folio.enums.SendingStatus;
import org.folio.rest.impl.base.AbstractBatchAPITest;
import org.folio.rest.jaxrs.model.EmailEntity;
import org.junit.Test;

import java.text.SimpleDateFormat;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import static org.folio.util.StubUtils.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DelayedTasksAPITest extends AbstractBatchAPITest {

  @Test
  public void deleteDelayedTaskExpiredMessagesWithDate() {
    int batchSize = 200;
    String messageStatus = SendingStatus.getStatus(SendingStatus.NEW);
    int mockServerPort = userMockServer.port();
    initModConfigStub(mockServerPort, getFullWiserMockConfigurations());

    // save batch emails
    List<EmailEntity> expectedEntries = generateEntityListWithoutSender(batchSize);
    postEmailsMessages(String.format(OKAPI_URL_TEMPLATE, mockServerPort), expectedEntries)
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT);

    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
    df.setTimeZone(TimeZone.getTimeZone(ZoneOffset.UTC));
    String expirationDate = df.format(new Date());

    // delete messages by date
    deleteMessagesByDate(String.format(OKAPI_URL_TEMPLATE, mockServerPort), expirationDate)
      .then()
      .statusCode(HttpStatus.SC_OK);

    // find all emails
    Response response = getEmailsMessages(String.format(OKAPI_URL_TEMPLATE, mockServerPort), messageStatus, batchSize)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract()
      .response();

    List<EmailEntity> actualEntries = convertEntriesToJson(response).getEmailEntity();
    assertTrue(actualEntries.isEmpty());
  }

  @Test
  public void deleteDelayedTaskExpiredMessagesWithoutDate() {
    int batchSize = 200;
    String messageStatus = SendingStatus.getStatus(SendingStatus.NEW);
    int mockServerPort = userMockServer.port();
    initModConfigStub(mockServerPort, getFullWiserMockConfigurations());

    // save batch emails
    List<EmailEntity> expectedEntries = generateEntityListWithoutSender(batchSize);
    postEmailsMessages(String.format(OKAPI_URL_TEMPLATE, mockServerPort), expectedEntries)
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT);

    // delete messages
    deleteMessages(String.format(OKAPI_URL_TEMPLATE, mockServerPort))
      .then()
      .statusCode(HttpStatus.SC_OK);

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
  public void deleteDelayedTasks() {
    int batchSize = 200;
    int mockServerPort = userMockServer.port();
    initModConfigStub(mockServerPort, getFullWiserMockConfigurations());

    // save batch emails
    List<EmailEntity> expectedEntries = generateEntityListWithoutSender(batchSize);
    postEmailsMessages(String.format(OKAPI_URL_TEMPLATE, mockServerPort), expectedEntries)
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT);

    // reset config
    initModConfigStub(mockServerPort, initIncorrectConfigurations());

    // delete messages by date
    deleteMessagesByDate(String.format(OKAPI_URL_TEMPLATE, mockServerPort), "2019-01-31")
      .then()
      .statusCode(HttpStatus.SC_OK);

    // delete messages
    deleteMessages(String.format(OKAPI_URL_TEMPLATE, mockServerPort))
      .then()
      .statusCode(HttpStatus.SC_OK);

    // run trigger send emails
    runSendEmailTrigger(String.format(OKAPI_URL_TEMPLATE, mockServerPort))
      .then()
      .statusCode(HttpStatus.SC_OK);
  }

  @Test
  public void sendEmailDelayedTasks(){
    int mockServerPort = userMockServer.port();

    // run trigger send emails
    runSendEmailTrigger(String.format(OKAPI_URL_TEMPLATE, mockServerPort))
      .then()
      .statusCode(HttpStatus.SC_OK);

    // run trigger send emails
    runSendEmailTrigger(String.format(OKAPI_URL_TEMPLATE, mockServerPort))
      .then()
      .statusCode(HttpStatus.SC_OK);

    // delete messages
    deleteMessages(String.format(OKAPI_URL_TEMPLATE, mockServerPort))
      .then()
      .statusCode(HttpStatus.SC_OK);
  }

  @Test
  public void deleteDelayedTasksWithoutConfig() {
    int mockServerPort = userMockServer.port();

    // delete messages by date
    deleteMessagesByDate(String.format(OKAPI_URL_TEMPLATE, mockServerPort), "2019-01-31")
      .then()
      .statusCode(HttpStatus.SC_OK);

    // delete messages
    deleteMessages(String.format(OKAPI_URL_TEMPLATE, mockServerPort))
      .then()
      .statusCode(HttpStatus.SC_OK);
  }

  @Test
  public void deleteDelayedTaskWithIncorrectPathParam() {
    int mockServerPort = userMockServer.port();

    Response response = deleteMessagesByDate(String.format(OKAPI_URL_TEMPLATE, mockServerPort), "incorrect")
      .then()
      .statusCode(HttpStatus.SC_BAD_REQUEST)
      .extract()
      .response();

    String responseMessage = response.getBody().asString();
    assertEquals("Invalid date value, the parameter must be in the format: yyyy-MM-dd", responseMessage);
  }
}
