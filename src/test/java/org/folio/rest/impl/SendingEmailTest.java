package org.folio.rest.impl;

import static java.lang.String.format;
import static junit.framework.TestCase.fail;
import static org.folio.matchers.JsonMatchers.matchesJson;
import static org.folio.rest.jaxrs.model.EmailEntity.Status.DELIVERED;
import static org.folio.util.StubUtils.buildIncorrectWiserEmailSettings;
import static org.folio.util.StubUtils.buildIncorrectWiserSmtpConfiguration;
import static org.folio.util.StubUtils.buildInvalidEmailSettings;
import static org.folio.util.StubUtils.buildInvalidSmtpConfiguration;
import static org.folio.util.StubUtils.buildValidEmailSettings;
import static org.folio.util.StubUtils.buildWiserSmtpConfiguration;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.http.HttpStatus;
import org.folio.rest.impl.base.AbstractAPITest;
import org.folio.rest.jaxrs.model.Attachment;
import org.folio.rest.jaxrs.model.EmailEntity;
import org.junit.Test;
import org.subethamail.wiser.WiserMessage;

import io.restassured.response.Response;
import io.vertx.core.json.JsonObject;
import junit.framework.AssertionFailedError;

public class SendingEmailTest extends AbstractAPITest {

  @Test
  public void sendTextEmail() throws Exception {
    createWiserSmtpConfigurationInDb();
    String sender = format(ADDRESS_TEMPLATE, RandomStringUtils.secure().nextAlphabetic(7));
    String recipient = format(ADDRESS_TEMPLATE, RandomStringUtils.secure().nextAlphabetic(5));
    String msg = "Test text for the message. Random text: " + RandomStringUtils.secure().nextAlphabetic(20);

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
    createWiserSmtpConfigurationInDb();
    String sender = format(ADDRESS_TEMPLATE, RandomStringUtils.secure().nextAlphabetic(7));
    String recipient = format(ADDRESS_TEMPLATE, RandomStringUtils.secure().nextAlphabetic(5));

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
    createWiserSmtpConfigurationInDb();
    String sender = format(ADDRESS_TEMPLATE, RandomStringUtils.secure().nextAlphabetic(7));
    String recipient = format(ADDRESS_TEMPLATE, RandomStringUtils.secure().nextAlphabetic(5));

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
    createWiserSmtpConfigurationInDb();
    String sender = format(ADDRESS_TEMPLATE, RandomStringUtils.secure().nextAlphabetic(7));
    String recipient = format(ADDRESS_TEMPLATE, RandomStringUtils.secure().nextAlphabetic(5));

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
    // init incorrect SMTP mock configuration
    createIncorrectWiserSmtpConfigurationInDb();
    String sender = format(ADDRESS_TEMPLATE, RandomStringUtils.secure().nextAlphabetic(7));
    String recipient = format(ADDRESS_TEMPLATE, RandomStringUtils.secure().nextAlphabetic(5));

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

    // delete incorrect configuration from local DB
    deleteLocalConfigurationAndWait();
    // init correct SMTP configuration
    createWiserSmtpConfigurationInDb();

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
  public void shouldSucceedWhenBothSettingAndSmtpConfigsExistAndAreValid() throws Exception {
    createWiserEmailSettingInDb();
    createWiserSmtpConfigurationInDb();
    sendEmailAndAssertDelivered();
  }

  @Test
  public void shouldSucceedWhenOnlyEmailSettingExistsAndIsValid() throws Exception {
    createWiserEmailSettingInDb();
    sendEmailAndAssertDelivered();
  }

  @Test
  public void shouldSucceedWhenEmailSettingExistsAndSmtpConfigIsIncorrectForWiser() throws Exception {
    createWiserEmailSettingInDb();
    createIncorrectWiserSmtpConfigurationInDb();
    sendEmailAndAssertDelivered();
  }

  @Test
  public void shouldSucceedWhenOnlySmtpConfigExistsAndIsValid() throws Exception {
    createWiserSmtpConfigurationInDb();
    sendEmailAndAssertDelivered();
    checkThatCorrectConfigCopiedToLocalDb();
    checkThatConfigAreDeletedFromSmtpConfig();
  }

  @Test
  public void shouldFailWhenNoneOfTheConfigsExist() {
    sendEmailAndAssertFailure(HttpStatus.SC_BAD_REQUEST);
  }

  @Test
  public void shouldFailWhenOnlySmtpConfigExistsAndIsInvalid() throws Exception {
    createInvalidSmtpConfigurationInDb();
    sendEmailAndAssertFailure(HttpStatus.SC_OK);
  }

  @Test
  public void shouldFailWhenOnlySmtpConfigExistsAndIsIncorrectForWiser() throws Exception {
    createIncorrectWiserSmtpConfigurationInDb();
    sendEmailAndAssertFailure(HttpStatus.SC_OK);
    checkThatConfigAreDeletedFromSmtpConfig();
  }

  @Test
  public void shouldSucceedWhenBothConfigsExistAndEmailSettingIsInvalid() throws Exception {
    createInvalidEmailSettingInDb();
    createWiserSmtpConfigurationInDb();
    sendEmailAndAssertDelivered();
    checkThatCorrectConfigCopiedToLocalDb();
    checkThatConfigAreDeletedFromSmtpConfig();
  }

  @Test
  public void shouldFailWhenOnlyEmailSettingExistsAndIsInvalid() {
    createInvalidEmailSettingInDb();
    sendEmailAndAssertFailure(HttpStatus.SC_BAD_REQUEST);
  }

  @Test
  public void shouldFailWhenBothConfigsExistAndBothAreInvalid() {
    createInvalidEmailSettingInDb();
    createInvalidSmtpConfigurationInDb();
    sendEmailAndAssertFailure(HttpStatus.SC_OK);
  }

  @Test
  public void shouldFailWhenBothConfigsExistAndEmailSettingIsInvalidAndSmtpConfigIsIncorrectForWiser() {
    createInvalidEmailSettingInDb();
    createIncorrectWiserSmtpConfigurationInDb();
    sendEmailAndAssertFailure(HttpStatus.SC_OK);
    checkThatConfigAreDeletedFromSmtpConfig();
  }

  @Test
  public void shouldFailWhenBothConfigsExistAndEmailSettingIsIncorrectForWiser() {
    createIncorrectWiserEmailSettingsInDb();
    createWiserSmtpConfigurationInDb();
    sendEmailAndAssertFailure(HttpStatus.SC_OK);
  }

  @Test
  public void shouldFailWhenOnlyEmailSettingExistsAndIsIncorrectForWiser() {
    createIncorrectWiserEmailSettingsInDb();
    sendEmailAndAssertFailure(HttpStatus.SC_OK);
  }

  @Test
  public void shouldFailWhenBothConfigsExistAndEmailSettingIsIncorrectForWiserAndSmtpConfigIsInvalid() {
    createIncorrectWiserEmailSettingsInDb();
    createInvalidSmtpConfigurationInDb();
    sendEmailAndAssertFailure(HttpStatus.SC_OK);
  }

  @Test
  public void shouldFailWhenBothConfigsExistAndBothAreIncorrectForWiser() {
    createIncorrectWiserEmailSettingsInDb();
    createIncorrectWiserSmtpConfigurationInDb();
    sendEmailAndAssertFailure(HttpStatus.SC_OK);
  }

  private void sendEmailAndAssertDelivered() throws Exception {
    String sender = format(ADDRESS_TEMPLATE, RandomStringUtils.secure().nextAlphabetic(7));
    String recipient = format(ADDRESS_TEMPLATE, RandomStringUtils.secure().nextAlphabetic(5));
    String msg = "Test text for the message. Random text: " + RandomStringUtils.secure().nextAlphabetic(20);

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
    String sender = format(ADDRESS_TEMPLATE, RandomStringUtils.secure().nextAlphabetic(7));
    String recipient = format(ADDRESS_TEMPLATE, RandomStringUtils.secure().nextAlphabetic(5));
    String msg = "Test text for the message. Random text: " + RandomStringUtils.secure().nextAlphabetic(20);

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

  private void createWiserEmailSettingInDb() {
    post(REST_PATH_EMAIL_SETTINGS,
      buildValidEmailSettings(UUID.randomUUID().toString(), buildWiserSmtpConfiguration()).encodePrettily());
  }

  private void createInvalidEmailSettingInDb() {
    post(REST_PATH_EMAIL_SETTINGS, buildInvalidEmailSettings().encodePrettily());
  }

  private void createIncorrectWiserSmtpConfigurationInDb() {
    post(REST_PATH_SMTP_CONFIGURATION, buildIncorrectWiserSmtpConfiguration().encodePrettily());
  }

  private void createIncorrectWiserEmailSettingsInDb() {
    post(REST_PATH_EMAIL_SETTINGS, buildIncorrectWiserEmailSettings().encodePrettily());
  }

  private void createInvalidSmtpConfigurationInDb() {
    post(REST_PATH_SMTP_CONFIGURATION, buildInvalidSmtpConfiguration().encodePrettily());
  }

  private void checkThatCorrectConfigCopiedToLocalDb() {
    Response response = get(REST_PATH_EMAIL_SETTINGS)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract()
      .response();

    String configuration = new JsonObject(response.body().asString())
      .getJsonArray("settings")
      .getJsonObject(0)
      .getJsonObject("value")
      .encodePrettily();
    assertThat(configuration, matchesJson(buildWiserSmtpConfiguration(), List.of("id", "metadata")));
  }

  private void checkThatConfigAreDeletedFromSmtpConfig() {
    Response response = get(REST_PATH_SMTP_CONFIGURATION)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract()
      .response();
    long totalRecords = new JsonObject(response.body().asString())
      .getLong("totalRecords");
    assertEquals(0L, totalRecords);
  }
}
