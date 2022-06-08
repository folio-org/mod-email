package org.folio.rest.impl;

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

    verifyStoredEmails(emailsCount, FAILURE, 1, true, expectedErrorMessage);

    for (int i = 2; i < RETRY_MAX_ATTEMPTS + 1; i++) {
      boolean shouldRetry = i < RETRY_MAX_ATTEMPTS;
      runRetryJobAndWaitForResult(emailsCount, FAILURE, i, shouldRetry);
      verifyStoredEmails(emailsCount, FAILURE, i, shouldRetry, expectedErrorMessage);
    }

    // another run to make sure that emails are no longer retried
    runRetryJobAndWaitForResultAtLeast(emailsCount, FAILURE, 3, false, Duration.ofSeconds(3));
    verifyStoredEmails(emailsCount, FAILURE, 3, false, expectedErrorMessage);
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

    verifyStoredEmails(twoBatches, FAILURE, 1, true, expectedErrorMessage);
    throwSmtpError(false);
    runRetryJobAndWaitForResult(RETRY_BATCH_SIZE, DELIVERED, 2, false);
    runRetryJobAndWaitForResult(twoBatches, DELIVERED, 2, false);
  }

  @Test
  public void shouldSendFailedEmailOnFirstRetry() {
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
    runRetryJobAndWaitForResult(1, DELIVERED, 2, false);
    verifyThatEmailsWereSent(1, 2);
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
    runRetryJobAndWaitForResult(1, DELIVERED, 2, false);
    verifyThatEmailsWereSent(1, 2);
  }

  @Test
  public void shouldRetryEmailFailedDueToInsufficientConfiguration() {
    initModConfigStub(userMockServer.port(), getIncorrectConfigurations());

    String expectedErrorMessage = "Error in the 'mod-email' module, the module didn't send email | " +
      "message: The 'mod-config' module doesn't have a minimum config for SMTP server, the min " +
      "config is: [EMAIL_SMTP_PORT, EMAIL_PASSWORD, EMAIL_SMTP_HOST, EMAIL_USERNAME]";

    sendEmail(buildEmail())
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body(is(expectedErrorMessage));

    verifyStoredEmails(1, FAILURE, 1, true, expectedErrorMessage);
    initModConfigStub(userMockServer.port(), getWiserMockConfigurations());
    runRetryJobAndWaitForResult(1, DELIVERED, 2, false);
    verifyThatEmailsWereSent(1, 2);
  }

  @Test
  public void shouldRetryEmailFailedDueToErrorInModConfiguration() {
    initFailModConfigStub(userMockServer.port());

    String expectedErrorMessage = String.format("Error looking up config at url=" +
      "http://localhost:%d/configurations/entries?query=module==SMTP_SERVER | " +
      "Expected status code 200, got 404 | error message: null", userMockServer.port());

    sendEmail(buildEmail())
      .then()
      .statusCode(HttpStatus.SC_BAD_REQUEST)
      .body(is(expectedErrorMessage));

    verifyStoredEmails(1, FAILURE, 1, true, expectedErrorMessage);
    initModConfigStub(userMockServer.port(), getWiserMockConfigurations());
    runRetryJobAndWaitForResult(1, DELIVERED, 2, false);
    verifyThatEmailsWereSent(1, 2);
  }

  @Test
  public void shouldNotRetryEmailOlderThanConfiguredAge() throws InterruptedException {
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
    runRetryJobAndWaitForResult(1, FAILURE, 2, true);

    // jump into the future to make email too old to retry
    ClockUtil.setClock(Clock.offset(ClockUtil.getClock(),
      Duration.ofMinutes(RETRY_AGE_THRESHOLD_MINUTES + 1)));
    runRetryJobAndWaitForResultAtLeast(1, FAILURE, 2, true, Duration.ofSeconds(3));
    verifyStoredEmails(1, FAILURE, 2, true, expectedErrorMessage);
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

  private Response getEmails(Status status, int attemptsCount, boolean shouldRetry) {
    return getEmails(String.format("status==%s and attemptsCount==%d and shouldRetry==%b",
      status.value(), attemptsCount, shouldRetry));
  }

  private Response runRetryJob() {
    return post(PATH_RETRY_FAILED_EMAILS);
  }

  private void runRetryJobAndWaitForResult(int expectedEmailsCount, Status expectedStatus,
    int expectedAttemptsCount, boolean expectedShouldRetry) {

    runRetryJobAndWaitForResultAtLeast(expectedEmailsCount, expectedStatus, expectedAttemptsCount,
      expectedShouldRetry, Duration.ZERO);
  }

  private void runRetryJobAndWaitForResultAtLeast(int expectedEmailsCount, Status expectedStatus,
    int expectedAttemptsCount, boolean expectedShouldRetry, Duration pollDelayDuration) {

    runRetryJob()
      .then()
      .statusCode(HttpStatus.SC_ACCEPTED);

    Awaitility.await()
      .pollDelay(pollDelayDuration)
      .atMost(15, SECONDS)
      .untilAsserted(() -> assertThat(
        getEmails(expectedStatus, expectedAttemptsCount, expectedShouldRetry)
          .then()
          .statusCode(200)
          .extract()
          .path("emailEntity"),
        hasSize(expectedEmailsCount)));
  }

  private List<EmailEntity> verifyThatEmailsWereSent(int emailsCount, int attemptsCount) {
    return verifyStoredEmails(emailsCount, DELIVERED, attemptsCount, false,
      "The message has been delivered");
  }

  private List<EmailEntity> verifyStoredEmails(int emailsCount, Status status,
    int attemptsCount, boolean shouldRetry, String message) {

    List<EmailEntity> emails = getEmails(status, attemptsCount, shouldRetry)
      .then()
      .statusCode(200)
      .body("emailEntity", hasSize(emailsCount))
      .extract()
      .body()
      .as(EmailEntries.class)
      .getEmailEntity();

    emails.forEach(email -> {
      assertThat(email.getStatus(), is(status));
      assertThat(email.getAttemptsCount(), is(attemptsCount));
      assertThat(email.getShouldRetry(), is(shouldRetry));
      assertThat(email.getMessage(), containsString(message));
    });

    return emails;
  }

}
