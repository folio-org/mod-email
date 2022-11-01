package org.folio.rest.impl;

import static java.util.stream.Collectors.toMap;
import static junit.framework.TestCase.fail;
import static org.folio.rest.jaxrs.model.EmailEntity.Status.DELIVERED;
import static org.folio.util.StubUtils.createConfigurationsWithCustomHeaders;
import static org.folio.util.StubUtils.getIncorrectWiserMockConfigurations;
import static org.folio.util.StubUtils.getWiserMockConfigurations;
import static org.folio.util.StubUtils.initModConfigStub;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.UUID;

import javax.mail.Header;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.http.HttpStatus;
import org.folio.rest.impl.base.AbstractAPITest;
import org.folio.rest.jaxrs.model.Attachment;
import org.folio.rest.jaxrs.model.EmailEntity;
import org.junit.Test;
import org.subethamail.wiser.WiserMessage;

import io.restassured.response.Response;
import junit.framework.AssertionFailedError;

public class SendingEmailTest extends AbstractAPITest {

  @Test
  public void sendTextEmail() throws Exception {
    int mockServerPort = userMockServer.port();
    initModConfigStub(mockServerPort, getWiserMockConfigurations());
    String sender = String.format(ADDRESS_TEMPLATE, RandomStringUtils.randomAlphabetic(7));
    String recipient = String.format(ADDRESS_TEMPLATE, RandomStringUtils.randomAlphabetic(5));
    String msg = "Test text for the message. Random text: " + RandomStringUtils.randomAlphabetic(20);

    EmailEntity emailEntity = new EmailEntity()
      .withNotificationId("1")
      .withTo(recipient)
      .withFrom(sender)
      .withHeader("Reset password")
      .withBody(msg)
      .withOutputFormat(MediaType.TEXT_PLAIN);

    Response response = sendEmail(emailEntity)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract()
      .response();
    checkResponseMessage(response, recipient);

    // check email on SMTP server
    WiserMessage wiserMessage = findMessageOnWiserServer(sender);
    checkMessagesOnWiserServer(wiserMessage, emailEntity);

    // check email on DB
    checkStoredEmailsInDb(emailEntity, DELIVERED);
  }

  @Test
  public void sendHtmlEmail() throws Exception {
    int mockServerPort = userMockServer.port();
    initModConfigStub(mockServerPort, getWiserMockConfigurations());
    String sender = String.format(ADDRESS_TEMPLATE, RandomStringUtils.randomAlphabetic(7));
    String recipient = String.format(ADDRESS_TEMPLATE, RandomStringUtils.randomAlphabetic(5));

    EmailEntity emailEntity = new EmailEntity()
      .withNotificationId("1")
      .withTo(recipient)
      .withFrom(sender)
      .withHeader("Update password")
      .withBody("<b>Test</b> text for <br> the message")
      .withOutputFormat(MediaType.TEXT_HTML);

    Response response = sendEmail(emailEntity)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract()
      .response();
    checkResponseMessage(response, recipient);

    // check email on SMTP server
    WiserMessage wiserMessage = findMessageOnWiserServer(sender);
    checkMessagesOnWiserServer(wiserMessage, emailEntity);

    // check email on DB
    checkStoredEmailsInDb(emailEntity, DELIVERED);
  }

  @Test
  public void sendHtmlEmailAttachments() throws Exception {
    int mockServerPort = userMockServer.port();
    initModConfigStub(mockServerPort, getWiserMockConfigurations());
    String sender = String.format(ADDRESS_TEMPLATE, RandomStringUtils.randomAlphabetic(7));
    String recipient = String.format(ADDRESS_TEMPLATE, RandomStringUtils.randomAlphabetic(5));

    EmailEntity emailEntity = new EmailEntity()
      .withNotificationId("1")
      .withTo(recipient)
      .withFrom(sender)
      .withHeader("Test header")
      .withBody("<p>Test text for the message</p>")
      .withAttachments(Collections.singletonList(
        new Attachment()
          .withContentId(UUID.randomUUID().toString())
          .withContentType("jpg")
          .withDescription("Description")
          .withName("image.jpg")
          .withData("Data")
      ))
      .withOutputFormat(MediaType.TEXT_HTML);

    Response response = sendEmail(emailEntity)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract()
      .response();
    checkResponseMessage(response, recipient);

    // check email on SMTP server
    WiserMessage wiserMessage = findMessageOnWiserServer(sender);
    checkMessagesOnWiserServer(wiserMessage, emailEntity);

    String fullMessageInfo = wiserMessage.toString();
    Attachment attachment = emailEntity.getAttachments().get(0);

    assertTrue(fullMessageInfo.contains(attachment.getContentId()));
    assertTrue(fullMessageInfo.contains(attachment.getContentType()));
    assertTrue(fullMessageInfo.contains(attachment.getDescription()));
    assertTrue(fullMessageInfo.contains(attachment.getName()));

    // check email on DB
    checkStoredEmailsInDb(emailEntity, DELIVERED);
  }

  @Test
  public void sendHtmlEmailAttachmentsWithoutData() throws Exception {
    int mockServerPort = userMockServer.port();
    initModConfigStub(mockServerPort, getWiserMockConfigurations());
    String sender = String.format(ADDRESS_TEMPLATE, RandomStringUtils.randomAlphabetic(7));
    String recipient = String.format(ADDRESS_TEMPLATE, RandomStringUtils.randomAlphabetic(5));

    EmailEntity emailEntity = new EmailEntity()
      .withNotificationId("1")
      .withTo(recipient)
      .withFrom(sender)
      .withHeader("Support issue")
      .withBody("Test text for the message")
      .withAttachments(Collections.singletonList(
        new Attachment()
          .withContentId(UUID.randomUUID().toString())
          .withContentType("jpg")
          .withDescription("Description")
          .withName("image.jpg")
          .withData("")
      ))
      .withOutputFormat(MediaType.TEXT_HTML);

    Response response = sendEmail(emailEntity)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract()
      .response();
    checkResponseMessage(response, recipient);

    // check email on SMTP server
    WiserMessage wiserMessage = findMessageOnWiserServer(sender);
    checkMessagesOnWiserServer(wiserMessage, emailEntity);

    // check email on DB
    checkStoredEmailsInDb(emailEntity, DELIVERED);
  }

  @Test
  public void checkSendingEmailWithDifferentConfigs() throws Exception {
    int mockServerPort = userMockServer.port();

    // init incorrect SMTP mock configuration
    initModConfigStub(mockServerPort, getIncorrectWiserMockConfigurations());
    String sender = String.format(ADDRESS_TEMPLATE, RandomStringUtils.randomAlphabetic(7));
    String recipient = String.format(ADDRESS_TEMPLATE, RandomStringUtils.randomAlphabetic(5));

    EmailEntity emailEntity = new EmailEntity()
      .withNotificationId("1")
      .withTo(recipient)
      .withFrom(sender)
      .withHeader("Support issue")
      .withBody("Test text for the message")
      .withAttachments(Collections.singletonList(
        new Attachment()
          .withContentId(UUID.randomUUID().toString())
          .withContentType("jpg")
          .withDescription("Description")
          .withName("image.jpg")
          .withData("")
      ))
      .withOutputFormat(MediaType.TEXT_HTML);

    Response response = sendEmail(emailEntity)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract()
      .response();
    assertTrue(response.getBody().asString().contains(FAIL_SENDING_EMAIL));

    try {
      findMessageOnWiserServer(sender);
      fail();
    } catch (AssertionFailedError ex) {
      //ignore
    }

    // init correct SMTP mock configuration
    initModConfigStub(mockServerPort, getWiserMockConfigurations());

    // delete incorrect configuration from local DB
    deleteLocalConfigurationAndWait();

    response = sendEmail(emailEntity)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract()
      .response();

    checkResponseMessage(response, recipient);

    // check email on SMTP server
    WiserMessage wiserMessage = findMessageOnWiserServer(sender);
    checkMessagesOnWiserServer(wiserMessage, emailEntity);

    // check email on DB
    checkStoredEmailsInDb(emailEntity, DELIVERED);
  }

  @Test
  public void messageShouldIncludeCustomHeadersFromConfiguration() throws Exception {
    Map<String, String> customHeaders = Map.of(
      "X-SES-CONFIGURATION-SET", "testConfigSet",
      "X-CUSTOM-HEADER", "customHeaderValue"
    );

    int mockServerPort = userMockServer.port();
    initModConfigStub(mockServerPort, createConfigurationsWithCustomHeaders(customHeaders));
    String sender = String.format(ADDRESS_TEMPLATE, RandomStringUtils.randomAlphabetic(7));
    String recipient = String.format(ADDRESS_TEMPLATE, RandomStringUtils.randomAlphabetic(5));
    String msg = "Test text for the message. Random text: " + RandomStringUtils.randomAlphabetic(20);

    EmailEntity emailEntity = new EmailEntity()
      .withNotificationId("1")
      .withTo(recipient)
      .withFrom(sender)
      .withHeader("Reset password")
      .withBody(msg)
      .withOutputFormat(MediaType.TEXT_PLAIN);

    Response response = sendEmail(emailEntity)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract()
      .response();

    checkResponseMessage(response, recipient);

    WiserMessage wiserMessage = findMessageOnWiserServer(sender);
    checkMessagesOnWiserServer(wiserMessage, emailEntity);

    checkStoredEmailsInDb(emailEntity, DELIVERED);

    assertSentMessageContainsHeaders(wiserMessage, customHeaders);
  }

  private static void assertSentMessageContainsHeaders(WiserMessage message,
    Map<String, String> expectedHeaders) throws Exception {

    Enumeration<Header> actualHeadersEnum = message.getMimeMessage().getMatchingHeaders(
      expectedHeaders.keySet().toArray(new String[0]));

    Map<String, String> actualHeaders = Collections.list(actualHeadersEnum)
      .stream()
      .collect(toMap(Header::getName, Header::getValue));

    assertEquals(expectedHeaders, actualHeaders);
  }

}
