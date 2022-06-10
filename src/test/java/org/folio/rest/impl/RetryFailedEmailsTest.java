package org.folio.rest.impl;

import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static org.folio.rest.jaxrs.model.EmailEntity.Status.DELIVERED;
import static org.folio.rest.jaxrs.model.EmailEntity.Status.FAILURE;
import static org.folio.util.StubUtils.getIncorrectConfigurations;
import static org.folio.util.StubUtils.getIncorrectWiserMockConfigurations;
import static org.folio.util.StubUtils.getWiserMockConfigurations;
import static org.folio.util.StubUtils.initFailModConfigStub;
import static org.folio.util.StubUtils.initModConfigStub;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.http.HttpStatus;
import org.folio.rest.impl.base.AbstractAPITest;
import org.folio.rest.jaxrs.model.EmailEntity;
import org.folio.rest.jaxrs.model.EmailEntity.Status;
import org.folio.rest.jaxrs.model.EmailEntries;
import org.folio.util.ClockUtil;
import org.junit.Test;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import io.restassured.response.Response;

public class RetryFailedEmailsTest extends AbstractAPITest {
  private static final int RETRY_MAX_ATTEMPTS = 3;
  private static final int RETRY_BATCH_SIZE = 50;
  private static final int RETRY_AGE_THRESHOLD_MINUTES = 10;
  private static final String PATH_RETRY_FAILED_EMAILS = "/delayedTask/retryFailedEmails";
  private static final String MESSAGE_WAS_DELIVERED = "The message has been delivered";

  @Test
  public void shouldRetryFailedEmailsUntilAllAttemptsAreExhausted() {
    initModConfigStub(userMockServer.port(), getWiserMockConfigurations());
    throwSmtpError(true);
    int emailsCount = 5;

    String expectedErrorMessage = "Error in the 'mod-email' module, the module " +
      "didn't send email | message: recipient address not accepted: 452 Error: too many recipients";

    sendEmails(emailsCount)
      .forEach(response -> response.then()
        .statusCode(HttpStatus.SC_OK)
        .body(is(expectedErrorMessage)));

    assertThat(getEmails(FAILURE, 1, true, expectedErrorMessage), hasSize(emailsCount));

    for (int i = 2; i < RETRY_MAX_ATTEMPTS + 1; i++) {
      boolean shouldRetry = i < RETRY_MAX_ATTEMPTS;
      runRetryJobAndWaitForResult(emailsCount, FAILURE, i, shouldRetry, expectedErrorMessage);
    }

    // another run to make sure that emails are no longer retried
    runRetryJobAndWaitForResult(emailsCount, FAILURE, RETRY_MAX_ATTEMPTS, false,
      expectedErrorMessage, ofSeconds(3));
  }

  @Test
  public void shouldRetryFailedEmailsInBatches() {
    initModConfigStub(userMockServer.port(), getWiserMockConfigurations());
    throwSmtpError(true);
    int twoBatches = RETRY_BATCH_SIZE * 2;

    String expectedErrorMessage = "Error in the 'mod-email' module, the module " +
      "didn't send email | message: recipient address not accepted: 452 Error: too many recipients";

    sendEmails(twoBatches)
      .forEach(response -> response.then()
        .statusCode(HttpStatus.SC_OK)
        .body(is(expectedErrorMessage)));

    assertThat(getEmails(FAILURE, 1, true, expectedErrorMessage), hasSize(twoBatches));
    throwSmtpError(false);
    runRetryJobAndWaitForResult(RETRY_BATCH_SIZE, DELIVERED, 2, false, MESSAGE_WAS_DELIVERED);
    runRetryJobAndWaitForResult(twoBatches, DELIVERED, 2, false, MESSAGE_WAS_DELIVERED);
  }

  @Test
  public void shouldRetryEmailFailedDueToSmtpError() {
    initModConfigStub(userMockServer.port(), getWiserMockConfigurations());
    throwSmtpError(true);

    String expectedErrorMessage = "Error in the 'mod-email' module, the module " +
      "didn't send email | message: recipient address not accepted: 452 Error: too many recipients";

    sendEmail(buildEmail())
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body(is(expectedErrorMessage));

    verifyStoredEmails(1, FAILURE, 1, true, expectedErrorMessage);
    throwSmtpError(false);
    runRetryJobAndWaitForResult(1, DELIVERED, 2, false, MESSAGE_WAS_DELIVERED);
  }

  @Test
  public void shouldRetryEmailFailedDueToInvalidConfiguration() {
    initModConfigStub(userMockServer.port(), getIncorrectWiserMockConfigurations());

    String expectedErrorMessage = "Error in the 'mod-email' module, the module didn't send email | " +
      "message: Connection refused";

    sendEmail(buildEmail())
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body(containsString(expectedErrorMessage));

    verifyStoredEmails(1, FAILURE, 1, true, expectedErrorMessage);
    initModConfigStub(userMockServer.port(), getWiserMockConfigurations());
    runRetryJobAndWaitForResult(1, DELIVERED, 2, false, MESSAGE_WAS_DELIVERED);
  }

  @Test
  public void shouldRetryEmailFailedDueToInsufficientConfiguration() {
    initModConfigStub(userMockServer.port(), getIncorrectConfigurations());

    String expectedErrorMessage = "The 'mod-config' module doesn't have a minimum config for SMTP " +
      "server, the min config is: [EMAIL_SMTP_PORT, EMAIL_PASSWORD, EMAIL_SMTP_HOST, EMAIL_USERNAME]";

    sendEmail(buildEmail())
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body(is(expectedErrorMessage));

    verifyStoredEmails(1, FAILURE, 1, true, expectedErrorMessage);
    initModConfigStub(userMockServer.port(), getWiserMockConfigurations());
    runRetryJobAndWaitForResult(1, DELIVERED, 2, false, MESSAGE_WAS_DELIVERED);
  }

  @Test
  public void shouldRetryEmailFailedDueToErrorInModConfiguration() {
    initFailModConfigStub(userMockServer.port());

    String expectedErrorMessage = "Error looking up config";

    sendEmail(buildEmail())
      .then()
      .statusCode(HttpStatus.SC_BAD_REQUEST)
      .body(containsString(expectedErrorMessage));

    verifyStoredEmails(1, FAILURE, 1, true, expectedErrorMessage);
    initModConfigStub(userMockServer.port(), getWiserMockConfigurations());
    runRetryJobAndWaitForResult(1, DELIVERED, 2, false, MESSAGE_WAS_DELIVERED);
  }

  @Test
  public void shouldNotRetryEmailOlderThanConfiguredAge() {
    initModConfigStub(userMockServer.port(), getWiserMockConfigurations());
    throwSmtpError(true);

    String expectedErrorMessage = "Error in the 'mod-email' module, the module " +
      "didn't send email | message: recipient address not accepted: 452 Error: too many recipients";

    sendEmail(buildEmail())
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body(is(expectedErrorMessage));

    verifyStoredEmails(1, FAILURE, 1, true, expectedErrorMessage);

    // run retry job and verify that email was retried
    runRetryJobAndWaitForResult(1, FAILURE, 2, true, expectedErrorMessage);

    // jump into the future to make email too old to retry
    ClockUtil.setClock(Clock.offset(ClockUtil.getClock(),
      Duration.ofMinutes(RETRY_AGE_THRESHOLD_MINUTES + 1)));

    runRetryJobAndWaitForResult(1, FAILURE, 2, true, expectedErrorMessage, ofSeconds(3));
  }

  private static EmailEntity buildEmail() {
    return buildEmail("1");
  }

  private static EmailEntity buildEmail(String notificationId) {
    return new EmailEntity()
      .withId(UUID.randomUUID().toString())
      .withNotificationId(notificationId)
      .withTo(String.format(ADDRESS_TEMPLATE, RandomStringUtils.randomAlphabetic(5)))
      .withFrom(String.format(ADDRESS_TEMPLATE, RandomStringUtils.randomAlphabetic(7)))
      .withHeader("Reset password")
      .withBody("Test text for the message.")
      .withOutputFormat(MediaType.TEXT_PLAIN);
  }

  private static Collection<EmailEntity> buildEmails(int count) {
    return IntStream.range(0, count)
      .boxed()
      .map(String::valueOf)
      .map(RetryFailedEmailsTest::buildEmail)
      .collect(toList());
  }

  private Collection<Response> sendEmails(int count) {
    return buildEmails(count)
      .stream()
      .map(this::sendEmail)
      .collect(toList());
  }

  private List<EmailEntity> getEmails(Status status, int attemptsCount, boolean shouldRetry,
    String message) {

    String query = String.format(
      "status==%s and attemptsCount==%d and shouldRetry==%b and message=\"%s\"",
      status.value(), attemptsCount, shouldRetry, message);

    return getEmails(query)
      .then()
      .statusCode(200)
      .extract()
      .body()
      .as(EmailEntries.class)
      .getEmailEntity();
  }

  private Response runRetryJob() {
    return post(PATH_RETRY_FAILED_EMAILS);
  }

  private List<EmailEntity> runRetryJobAndWaitForResult(int expectedEmailsCount, Status expectedStatus,
    int expectedAttemptsCount, boolean expectedShouldRetry, String expectedMessage) {

    return runRetryJobAndWaitForResult(expectedEmailsCount, expectedStatus,
      expectedAttemptsCount, expectedShouldRetry, expectedMessage, Duration.ZERO);
  }

  private List<EmailEntity> runRetryJobAndWaitForResult(int expectedEmailsCount,
    Status expectedStatus, int expectedAttemptsCount, boolean expectedShouldRetry,
    String expectedMessage, Duration pollDelayDuration) {

    runRetryJob()
      .then()
      .statusCode(HttpStatus.SC_ACCEPTED);

    return Awaitility.await()
      .pollDelay(pollDelayDuration)
      .atMost(15, SECONDS)
      .until(() -> getEmails(expectedStatus, expectedAttemptsCount, expectedShouldRetry, expectedMessage),
        list -> list.size() == expectedEmailsCount);
  }

  private List<EmailEntity> verifyStoredEmails(int expectedEmailsCount, Status expectedStatus,
    int expectedAttemptsCount, boolean expectedShouldRetry, String expectedMessage) {

    List<EmailEntity> emails = getEmails(expectedStatus, expectedAttemptsCount, expectedShouldRetry,
      expectedMessage);
    assertThat(emails, hasSize(expectedEmailsCount));

    return emails;
  }

}
