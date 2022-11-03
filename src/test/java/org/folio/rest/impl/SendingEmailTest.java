package org.folio.rest.impl;

import static java.lang.String.format;
import static java.util.stream.Collectors.toMap;
import static junit.framework.TestCase.fail;
import static org.folio.matchers.JsonMatchers.matchesJson;
import static org.folio.rest.jaxrs.model.EmailEntity.Status.DELIVERED;
import static org.folio.util.StubUtils.buildIncorrectWiserSmtpConfiguration;
import static org.folio.util.StubUtils.buildInvalidSmtpConfiguration;
import static org.folio.util.StubUtils.buildWiserSmtpConfiguration;
import static org.folio.util.StubUtils.createConfigurationsWithCustomHeaders;
import static org.folio.util.StubUtils.getIncorrectConfigurations;
import static org.folio.util.StubUtils.getIncorrectWiserMockConfigurations;
import static org.folio.util.StubUtils.getWiserMockConfigurations;
import static org.folio.util.StubUtils.initFailModConfigStub;
import static org.folio.util.StubUtils.initModConfigStub;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
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
    String sender = format(ADDRESS_TEMPLATE, RandomStringUtils.randomAlphabetic(7));
    String recipient = format(ADDRESS_TEMPLATE, RandomStringUtils.randomAlphabetic(5));
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
    String sender = format(ADDRESS_TEMPLATE, RandomStringUtils.randomAlphabetic(7));
    String recipient = format(ADDRESS_TEMPLATE, RandomStringUtils.randomAlphabetic(5));

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
    String sender = format(ADDRESS_TEMPLATE, RandomStringUtils.randomAlphabetic(7));
    String recipient = format(ADDRESS_TEMPLATE, RandomStringUtils.randomAlphabetic(5));

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
    String sender = format(ADDRESS_TEMPLATE, RandomStringUtils.randomAlphabetic(7));
    String recipient = format(ADDRESS_TEMPLATE, RandomStringUtils.randomAlphabetic(5));

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
    String sender = format(ADDRESS_TEMPLATE, RandomStringUtils.randomAlphabetic(7));
    String recipient = format(ADDRESS_TEMPLATE, RandomStringUtils.randomAlphabetic(5));

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
    String sender = format(ADDRESS_TEMPLATE, RandomStringUtils.randomAlphabetic(7));
    String recipient = format(ADDRESS_TEMPLATE, RandomStringUtils.randomAlphabetic(5));
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

  @Test
  public void shouldSucceedWhenBothLocalAndRemoteConfigsExistAndAreValid() throws Exception {
    int mockServerPort = userMockServer.port();
    initModConfigStub(mockServerPort, getWiserMockConfigurations());
    createWiserSmtpConfigurationInDb();

    sendEmailAndAssertDelivered();
  }

  @Test
  public void shouldSucceedWhenOnlyLocalConfigExistsAndIsValid() throws Exception {
    createWiserSmtpConfigurationInDb();

    sendEmailAndAssertDelivered();
  }

  @Test
  public void shouldSucceedWhenBothLocalAndRemoteConfigsExistAndRemoteIsInvalid() throws Exception {
    int mockServerPort = userMockServer.port();
    initModConfigStub(mockServerPort, getIncorrectConfigurations());
    createWiserSmtpConfigurationInDb();

    sendEmailAndAssertDelivered();
  }

  @Test
  public void shouldSucceedWhenBothLocalAndRemoteConfigsExistAndRemoteIsIncorrectForWiser() throws Exception {
    int mockServerPort = userMockServer.port();
    initModConfigStub(mockServerPort, getIncorrectWiserMockConfigurations());
    createWiserSmtpConfigurationInDb();

    sendEmailAndAssertDelivered();
  }

  @Test
  public void shouldSucceedWhenOnlyRemoteConfigExistsAndIsValid() throws Exception {
    int mockServerPort = userMockServer.port();
    initModConfigStub(mockServerPort, getWiserMockConfigurations());

    sendEmailAndAssertDelivered();
    checkThatCorrectConfigCopiedToLocalDb();
  }

  @Test
  public void shouldFailWhenNoneOfTheConfigsExist() {
    int mockServerPort = userMockServer.port();
    initFailModConfigStub(mockServerPort);

    sendEmailAndAssertFailure(HttpStatus.SC_BAD_REQUEST);
  }

  @Test
  public void shouldFailWhenOnlyRemoteConfigExistsAndIsInvalid() throws Exception {
    int mockServerPort = userMockServer.port();
    initModConfigStub(mockServerPort, getIncorrectConfigurations());

    sendEmailAndAssertFailure(HttpStatus.SC_OK);
  }

  @Test
  public void shouldFailWhenOnlyRemoteConfigExistsAndIsIncorrectForWiser() throws Exception {
    int mockServerPort = userMockServer.port();
    initModConfigStub(mockServerPort, getIncorrectWiserMockConfigurations());

    sendEmailAndAssertFailure(HttpStatus.SC_OK);
  }

  @Test
  public void shouldSucceedWhenBothConfigsExistAndLocalIsInvalid() throws Exception {
    int mockServerPort = userMockServer.port();
    initModConfigStub(mockServerPort, getWiserMockConfigurations());

    createInvalidSmtpConfigurationInDb();

    sendEmailAndAssertDelivered();
    checkThatCorrectConfigCopiedToLocalDb();
  }

  @Test
  public void shouldFailWhenOnlyLocalConfigExistsAndIsInvalid() {
    int mockServerPort = userMockServer.port();
    initFailModConfigStub(mockServerPort);

    createInvalidSmtpConfigurationInDb();

    sendEmailAndAssertFailure(HttpStatus.SC_BAD_REQUEST);
  }

  @Test
  public void shouldFailWhenBothConfigsExistAndBothAreInvalid() {
    int mockServerPort = userMockServer.port();
    initModConfigStub(mockServerPort, getIncorrectConfigurations());

    createInvalidSmtpConfigurationInDb();

    sendEmailAndAssertFailure(HttpStatus.SC_OK);
  }

  @Test
  public void shouldFailWhenBothConfigsExistAndLocalIsInvalidAndRemoteIsIncorrectForWiser() {
    int mockServerPort = userMockServer.port();
    initModConfigStub(mockServerPort, getIncorrectWiserMockConfigurations());

    createInvalidSmtpConfigurationInDb();

    sendEmailAndAssertFailure(HttpStatus.SC_OK);
  }

  @Test
  public void shouldFailWhenBothConfigsExistAndLocalIsIncorrectForWiser() throws Exception {
    int mockServerPort = userMockServer.port();
    initModConfigStub(mockServerPort, getWiserMockConfigurations());

    createIncorrectWiserSmtpConfigurationInDb();

    sendEmailAndAssertFailure(HttpStatus.SC_OK);
  }

  @Test
  public void shouldFailWhenOnlyLocalConfigExistsAndIsIncorrectForWiser() {
    int mockServerPort = userMockServer.port();
    initFailModConfigStub(mockServerPort);

    createIncorrectWiserSmtpConfigurationInDb();

    sendEmailAndAssertFailure(HttpStatus.SC_OK);
  }

  @Test
  public void shouldFailWhenBothConfigsExistAndLocalIsIncorrectForWiserAndRemoteIsInvalid() {
    int mockServerPort = userMockServer.port();
    initModConfigStub(mockServerPort, getIncorrectConfigurations());

    createIncorrectWiserSmtpConfigurationInDb();

    sendEmailAndAssertFailure(HttpStatus.SC_OK);
  }

  @Test
  public void shouldFailWhenBothConfigsExistAndBothAreIncorrectForWiser() {
    int mockServerPort = userMockServer.port();
    initModConfigStub(mockServerPort, getIncorrectWiserMockConfigurations());

    createIncorrectWiserSmtpConfigurationInDb();

    sendEmailAndAssertFailure(HttpStatus.SC_OK);
  }

  private void sendEmailAndAssertDelivered() throws Exception {
    String sender = format(ADDRESS_TEMPLATE, RandomStringUtils.randomAlphabetic(7));
    String recipient = format(ADDRESS_TEMPLATE, RandomStringUtils.randomAlphabetic(5));
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

  private void sendEmailAndAssertFailure(int expectedEmailSendingStatus) {
    String sender = format(ADDRESS_TEMPLATE, RandomStringUtils.randomAlphabetic(7));
    String recipient = format(ADDRESS_TEMPLATE, RandomStringUtils.randomAlphabetic(5));
    String msg = "Test text for the message. Random text: " + RandomStringUtils.randomAlphabetic(20);

    EmailEntity emailEntity = new EmailEntity()
      .withNotificationId("1")
      .withTo(recipient)
      .withFrom(sender)
      .withHeader("Reset password")
      .withBody(msg)
      .withOutputFormat(MediaType.TEXT_PLAIN);

    sendEmail(emailEntity)
      .then()
      .statusCode(expectedEmailSendingStatus);

    // check email on SMTP server
    try {
      findMessageOnWiserServer(sender);
    } catch (AssertionFailedError e) {
      assertEquals(e.getMessage(), format(MESSAGE_NOT_FOUND, sender));
    }

    // check email on DB
    checkAbsenceOfStoredEmailsInDb(emailEntity, DELIVERED);
  }

  private void createWiserSmtpConfigurationInDb() {
    post(REST_PATH_SMTP_CONFIGURATION, buildWiserSmtpConfiguration().encodePrettily());
  }

  private void createIncorrectWiserSmtpConfigurationInDb() {
    post(REST_PATH_SMTP_CONFIGURATION, buildIncorrectWiserSmtpConfiguration().encodePrettily());
  }

  private void createInvalidSmtpConfigurationInDb() {
    post(REST_PATH_SMTP_CONFIGURATION, buildInvalidSmtpConfiguration().encodePrettily());
  }

  private void checkThatCorrectConfigCopiedToLocalDb() {
    get(REST_PATH_SMTP_CONFIGURATION)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body(matchesJson(buildWiserSmtpConfiguration(), List.of("id", "ssl", "trustAll",
        "loginOption", "startTlsOptions", "authMethods", "from", "emailHeaders")));
  }
}
