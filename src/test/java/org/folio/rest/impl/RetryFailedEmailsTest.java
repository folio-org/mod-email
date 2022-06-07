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

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Date;
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
      runRetryJobAndWait(emailsCount, FAILURE, i, shouldRetry);
      verifyStoredEmails(emailsCount, FAILURE, i, shouldRetry, expectedErrorMessage);
    }

    // another run to make sure that emails are no longer retried
    runRetryJobAndWait(emailsCount, FAILURE, 3, false);
    // wait unconditionally since there are no detectable changes to tell us that the job is done
    Awaitility.await().during(3, SECONDS);
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
    runRetryJobAndWait(RETRY_BATCH_SIZE, DELIVERED, 2, false);
    runRetryJobAndWait(twoBatches, DELIVERED, 2, false);
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
    runRetryJobAndWait(1, DELIVERED, 2, false);
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
    runRetryJobAndWait(1, DELIVERED, 2, false);
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
    runRetryJobAndWait(1, DELIVERED, 2, false);
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
    runRetryJobAndWait(1, DELIVERED, 2, false);
    verifyThatEmailsWereSent(1, 2);
  }

  @Test
  public void shouldNotRetryEmailsOlderThanConfiguredAge() {
    initModConfigStub(userMockServer.port(), getWiserMockConfigurations());
    throwSmtpError(true);

    String expectedErrorMessage = "Error in the 'mod-email' module, the module " +
      "didn't send email | message: recipient address not accepted: 452 Error: too many recipients";

    sendEmails(2)
      .forEach(response -> response.then()
        .statusCode(HttpStatus.SC_OK)
        .body(is(expectedErrorMessage)));

    List<EmailEntity> emailsBeforeRetry = verifyStoredEmails(2, FAILURE, 1, true, expectedErrorMessage);
    EmailEntity firstEmail = emailsBeforeRetry.get(0);
    EmailEntity secondEmail = emailsBeforeRetry.get(1);

    Instant rightBelowThreshold = ZonedDateTime.now(ZoneOffset.UTC)
      .minusMinutes(RETRY_AGE_THRESHOLD_MINUTES + 1)
      .toInstant();

    updateEmail(firstEmail.withDate(Date.from(rightBelowThreshold)));
    runRetryJobAndWait(1, FAILURE, 2, true);

    List<EmailEntity> ignoredEmails = verifyStoredEmails(1, FAILURE, 1, true, expectedErrorMessage);
    assertThat(ignoredEmails, hasSize(1));
    assertThat(ignoredEmails.get(0).getId(), is(firstEmail.getId()));

    List<EmailEntity> retriedEmails = verifyStoredEmails(1, FAILURE, 2, true, expectedErrorMessage);
    assertThat(retriedEmails, hasSize(1));
    assertThat(retriedEmails.get(0).getId(), is(secondEmail.getId()));
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

  private void runRetryJobAndWait(int expectedEmailsCount, Status expectedStatus,
    int expectedAttemptsCount, boolean expectedShouldRetry) {

    runRetryJob()
      .then()
      .statusCode(HttpStatus.SC_ACCEPTED);

    Awaitility.await()
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
