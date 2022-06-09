package org.folio.rest.impl.base;

import static io.vertx.core.json.JsonObject.mapFrom;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.jaxrs.model.EmailEntity.Status;
import static org.folio.util.EmailUtils.EMAIL_STATISTICS_TABLE_NAME;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.text.SimpleDateFormat;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;

import javax.mail.Address;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.ws.rs.core.MediaType;

import io.restassured.specification.RequestSpecification;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.postgres.testing.PostgresTesterContainer;
import org.folio.rest.RestVerticle;
import org.folio.rest.client.TenantClient;
import org.folio.rest.jaxrs.model.EmailEntity;
import org.folio.rest.jaxrs.model.EmailEntries;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.jaxrs.model.TenantJob;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.util.ClockUtil;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.subethamail.wiser.Wiser;
import org.subethamail.wiser.WiserMessage;

import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;

import io.restassured.RestAssured;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import junit.framework.AssertionFailedError;

@RunWith(VertxUnitRunner.class)
public abstract class AbstractAPITest {

  private static final Logger logger = LogManager.getLogger(AbstractAPITest.class);

  private static final int DEFAULT_LIMIT = 100;
  private static final int POST_TENANT_TIMEOUT = 10000;
  private static final String HTTP_PORT = "http.port";
  private static final String TENANT_CLIENT_HOST = " http://%s:%s";

  private static final String OKAPI_TENANT = "test_tenant";
  private static final String OKAPI_URL_HEADER = "x-okapi-url";
  private static final String OKAPI_HOST = "localhost";
  private static final String OKAPI_URL_TEMPLATE = "http://localhost:%s";

  protected static final String REST_PATH_EMAIL = "/email";
  private static final String PATH_WITH_QUERY_TEMPLATE = "%s?query=%s&limit=%s";
  protected static final String ADDRESS_TEMPLATE = "%s@localhost";
  private static final String SUCCESS_SEND_EMAIL = "The message has been delivered to %s";
  private static final String MESSAGE_NOT_FOUND = "The message for the sender: `%s` was not found on the SMTP server";
  protected static final String FAIL_SENDING_EMAIL = "Error in the 'mod-email' module, the module didn't send email | message:";

  private static final String REST_DELETE_BATCH_EMAILS = "/delayedTask/expiredMessages";
  private static final String REST_PATH_DELETE_BATCH_EMAILS = "%s?expirationDate=%s&emailStatus=%s";

  private static Wiser wiser;
  private static Vertx vertx;
  private static int port;
  protected static PostgresClient postgresClient;

  @Rule
  public Timeout rule = Timeout.seconds(100);

  @Rule
  public WireMockRule userMockServer = new WireMockRule(
    WireMockConfiguration.wireMockConfig()
      .dynamicPort()
      .notifier(new ConsoleNotifier(true)));

  @BeforeClass
  public static void setUpClass(final TestContext context) {
    PostgresClient.setPostgresTester(new PostgresTesterContainer());
    Async async = context.async();
    vertx = Vertx.vertx();
    port = NetworkUtils.nextFreePort();
    wiser = new Wiser(2500);

    TenantClient tenantClient = new TenantClient(String.format(TENANT_CLIENT_HOST, OKAPI_HOST, port), OKAPI_TENANT, null);
    DeploymentOptions restDeploymentOptions = new DeploymentOptions()
      .setConfig(new JsonObject().put(HTTP_PORT, port));

    vertx.deployVerticle(RestVerticle.class.getName(), restDeploymentOptions,
      res -> {
        try {
          TenantAttributes t = new TenantAttributes().withModuleTo("mod-email-1.0.0");
          tenantClient.postTenant(t, res2 -> {
            if (res2.failed()) {
              Throwable cause = res2.cause();
              logger.error(cause);
              context.fail(cause);
              return;
            }

            final HttpResponse<Buffer> postResponse = res2.result();
            assertThat(postResponse.statusCode(), is(HttpStatus.SC_CREATED));

            String jobId = postResponse.bodyAsJson(TenantJob.class).getId();

            tenantClient.getTenantByOperationId(jobId, POST_TENANT_TIMEOUT, getResult -> {
              if (getResult.failed()) {
                Throwable cause = getResult.cause();
                logger.error(cause.getMessage());
                context.fail(cause);
                return;
              }

              final HttpResponse<Buffer> getResponse = getResult.result();
              assertThat(getResponse.statusCode(), is(HttpStatus.SC_OK));
              assertThat(getResponse.bodyAsJson(TenantJob.class).getComplete(), is(true));
              wiser.start();
              postgresClient = PostgresClient.getInstance(vertx, OKAPI_TENANT);
              async.complete();
            });
            }
          );
        } catch (Exception e) {
          logger.error(e.getMessage());
        }
      });
  }

  @AfterClass
  public static void tearDownClass(final TestContext context) {
    Async async = context.async();
    vertx.close(context.asyncAssertSuccess(res -> {
      PostgresClient.stopPostgresTester();
      wiser.stop();
      async.complete();
    }));
  }

  @Before
  public void setUp(TestContext context) {
    throwSmtpError(false);
    wiser.getMessages().clear();
    ClockUtil.setDefaultClock();

    Async async = context.async();
    postgresClient.delete(EMAIL_STATISTICS_TABLE_NAME, new Criterion(),
      event -> {
        if (event.failed()) {
          logger.error(event.cause());
          context.fail(event.cause());
        } else {
          async.complete();
        }
      });
  }

  @After
  public void afterEach(TestContext context) {
    wiser.getMessages().clear();
    ClockUtil.setDefaultClock();
  }

  protected Response getEmails(Status status) {
    return getEmails("status==" + status.value());
  }

  protected Response getEmails(String query) {
    return getRequestSpecification()
      .get(String.format(PATH_WITH_QUERY_TEMPLATE, REST_PATH_EMAIL, query, DEFAULT_LIMIT));
  }

  protected Response post(String path) {
    return getRequestSpecification()
      .post(path);
  }

  protected Response post(String path, String body) {
    return getRequestSpecification()
      .body(body)
      .post(path);
  }

  /**
   * Send email notifications
   */
  protected Response sendEmail(EmailEntity emailEntity) {
    return post(REST_PATH_EMAIL, mapFrom(emailEntity).encodePrettily());
  }

  /**
   * Delete email by expirationDate and status
   */
  protected Response deleteEmailByDateAndStatus(String expirationDate, String status) {
    return getRequestSpecification()
      .delete(String.format(REST_PATH_DELETE_BATCH_EMAILS, REST_DELETE_BATCH_EMAILS, expirationDate, status));
  }

  /**
   * Check that after sending the email successfully, it contains the email address of the recipient
   */
  protected void checkResponseMessage(Response response, String recipient) {
    String responseMessage = response.getBody().asString();
    assertEquals(String.format(SUCCESS_SEND_EMAIL, recipient), responseMessage);
  }

  /**
   * Find the sent email message on the SMTP server by the sender
   */
  protected WiserMessage findMessageOnWiserServer(String sender) {
    return wiser.getMessages().stream()
      .filter(msg -> {
        try {
          return isContainsSenderAddress(sender, msg.getMimeMessage().getFrom());
        } catch (MessagingException ex) {
          logger.debug(ex);
          return false;
        }
      })
      .findFirst()
      .orElseThrow(() -> throwAssertionFailedError(sender));
  }

  private AssertionFailedError throwAssertionFailedError(String sender) {
    throw new AssertionFailedError(String.format(MESSAGE_NOT_FOUND, sender));
  }

  /**
   * Check on the SMTP server that the email was sent successfully.
   */
  protected void checkMessagesOnWiserServer(WiserMessage wiserMessage,
    EmailEntity emailEntity) throws MessagingException {

    assertTrue(wiserMessage.toString().contains(emailEntity.getBody()));

    MimeMessage message = wiserMessage.getMimeMessage();
    assertEquals(emailEntity.getHeader(), message.getSubject());

    Address[] from = message.getFrom();
    checkAddress(emailEntity.getFrom(), from);

    Address[] recipients = message.getAllRecipients();
    checkAddress(emailEntity.getTo(), recipients);
  }

  /**
   * Check stored emails in the database
   */
  protected void checkStoredEmailsInDb(EmailEntity emailEntity, Status status) {
    Response responseDb = getEmails(status)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract()
      .response();

    List<EmailEntity> emailEntities = convertEntriesToJson(responseDb).getEmailEntity();
    Optional<EmailEntity> emailEntityOpt = emailEntities.stream().filter(entity -> emailEntity.getTo().equals(entity.getTo())).findFirst();
    if (!emailEntityOpt.isPresent()) {
      fail();
    }
    EmailEntity entity = emailEntityOpt.get();

    assertEquals(emailEntity.getNotificationId(), entity.getNotificationId());
    assertEquals(emailEntity.getTo(), entity.getTo());
    assertEquals(emailEntity.getFrom(), entity.getFrom());
    assertEquals(emailEntity.getHeader(), entity.getHeader());
    assertEquals(emailEntity.getOutputFormat(), entity.getOutputFormat());
    assertEquals(emailEntity.getBody(), entity.getBody());

    assertTrue(StringUtils.isNoneBlank(entity.getMessage()));
    assertTrue(StringUtils.isNoneBlank(entity.getStatus().value()));
  }

  /**
   * Send random email
   */
  protected EmailEntity sendEmail(int expectedStatusCode) {
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

    sendEmail(emailEntity)
      .then()
      .statusCode(expectedStatusCode)
      .extract()
      .response();

    return emailEntity;
  }

  protected String generateExpirationDate() {
    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
    df.setTimeZone(TimeZone.getTimeZone(ZoneOffset.UTC));
    return df.format(new Date());
  }

  protected EmailEntries convertEntriesToJson(Response response) {
    return new JsonObject(response.getBody().print()).mapTo(EmailEntries.class);
  }

  private void checkAddress(String expectedAddress, Address[] address) {
    assertTrue(isContainsSenderAddress(expectedAddress, address));
  }

  private boolean isContainsSenderAddress(String sender, Address[] address) {
    return Arrays.stream(address)
      .map(Address::toString)
      .anyMatch(ad -> ad.equals(sender));
  }

  private RequestSpecification getRequestSpecification() {
    return RestAssured.given()
      .port(port)
      .contentType(MediaType.APPLICATION_JSON)
      .header(new Header(OKAPI_HEADER_TENANT, OKAPI_TENANT))
      .header(new Header(OKAPI_URL_HEADER, String.format(OKAPI_URL_TEMPLATE, userMockServer.port())))
      .when();
  }

  protected Future<RowSet<Row>> updateEmail(EmailEntity email) {
    return postgresClient.update(EMAIL_STATISTICS_TABLE_NAME, mapFrom(email), email.getId());
  }

  protected void throwSmtpError(boolean throwError) {
    wiser.getServer().setMaxRecipients(throwError ? 0 : 1000);
  }
}
