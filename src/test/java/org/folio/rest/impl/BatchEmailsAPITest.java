package org.folio.rest.impl;

import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.restassured.RestAssured;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import junit.framework.AssertionFailedError;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.http.HttpStatus;
import org.folio.enums.SendingStatus;
import org.folio.rest.RestVerticle;
import org.folio.rest.client.TenantClient;
import org.folio.rest.jaxrs.model.EmailEntity;
import org.folio.rest.jaxrs.model.EmailEntries;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.subethamail.wiser.Wiser;

import javax.mail.Address;
import javax.mail.MessagingException;
import javax.ws.rs.core.MediaType;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TOKEN;
import static org.folio.util.StubUtils.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(VertxUnitRunner.class)
public class BatchEmailsAPITest {

  private static final Logger logger = LoggerFactory.getLogger(BatchEmailsAPITest.class);

  private static final String HTTP_PORT = "http.port";
  private static final String OKAPI_TENANT = "test_tenant";
  private static final String OKAPI_TOKEN = "test_token";
  private static final String OKAPI_URL_HEADER = "x-okapi-url";
  private static final String OKAPI_HOST = "localhost";
  private static final String OKAPI_URL_TEMPLATE = "http://localhost:%s";
  private static final String REST_BATCH_EMAILS = "/batchEmails";
  private static final String TENANT_CLIENT_HOST = " http://%s:%s";
  private static final String REST_PATH_WITH_QUERY = "%s?query=status=%s&limit=%s";
  private static final String ADDRESS_TEMPLATE = "%s@localhost";
  private static final String EMAIL_MESSAGES_TABLE_NAME = "email_messages";

  private Function<EmailEntity, String> getRecipients = EmailEntity::getTo;
  private Function<EmailEntity, String> getSenders = EmailEntity::getFrom;

  private static Wiser wiser;
  private static Vertx vertx;
  private static int port;

  @Rule
  public Timeout rule = Timeout.seconds(100);

  @Rule
  public WireMockRule userMockServer = new WireMockRule(
    WireMockConfiguration.wireMockConfig()
      .dynamicPort()
      .notifier(new ConsoleNotifier(true)));

  @BeforeClass
  public static void setUpClass(final TestContext context) throws Exception {
    Async async = context.async();
    vertx = Vertx.vertx();
    port = NetworkUtils.nextFreePort();

    wiser = new Wiser();
    wiser.setPort(2500);

    PostgresClient.setIsEmbedded(true);
    PostgresClient.getInstance(vertx).startEmbeddedPostgres();

    TenantClient tenantClient = new TenantClient(String.format(TENANT_CLIENT_HOST, OKAPI_HOST, port), OKAPI_TENANT, OKAPI_TOKEN);
    DeploymentOptions restDeploymentOptions = new DeploymentOptions()
      .setConfig(new JsonObject().put(HTTP_PORT, port));

    vertx.deployVerticle(RestVerticle.class.getName(), restDeploymentOptions,
      res -> {
        try {
          tenantClient.postTenant(null, res2 -> {
              wiser.start();
              async.complete();
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
      PostgresClient.stopEmbeddedPostgres();
      wiser.stop();
      async.complete();
    }));
  }

  @Before
  public void setUp(TestContext context) {
    Async async = context.async();
    PostgresClient.getInstance(vertx, OKAPI_TENANT).delete(EMAIL_MESSAGES_TABLE_NAME, new Criterion(),
      event -> {
        if (event.failed()) {
          logger.error(event.cause());
          context.fail(event.cause());
        } else {
          async.complete();
        }
      });
  }

  @Test
  public void saveBatchAndGetAllEmailsMessagesFullConfig() {
    int batchSize = 500;
    String messageStatus = SendingStatus.getStatus(SendingStatus.NEW);
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
    String messageStatus = SendingStatus.getStatus(SendingStatus.NEW);
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
    String messageStatus = SendingStatus.getStatus(SendingStatus.FAILURE);
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
    String messageStatus = SendingStatus.getStatus(SendingStatus.NEW);
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
    String failureStatus = SendingStatus.getStatus(SendingStatus.FAILURE);
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

  private void checkDeliveredMessages(int batchSize, int mockServerPort, List<EmailEntity> expectedEntries) {
    // save batch emails
    postEmailsMessages(String.format(OKAPI_URL_TEMPLATE, mockServerPort), expectedEntries)
      .then()
      .statusCode(HttpStatus.SC_NO_CONTENT);

    // run trigger send emails
    runSendEmailTrigger(String.format(OKAPI_URL_TEMPLATE, mockServerPort))
      .then()
      .statusCode(HttpStatus.SC_OK);

    // find all emails with DELIVERED status
    String deliveredStatus = SendingStatus.getStatus(SendingStatus.DELIVERED);
    Response response = getEmailsMessages(String.format(OKAPI_URL_TEMPLATE, mockServerPort), deliveredStatus, batchSize)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract()
      .response();

    // the trigger processes only 50 per call
    List<EmailEntity> actualEntries = convertEntriesToJson(response).getEmailEntity();
    assertEquals(expectedEntries.size(), actualEntries.size());

    assertEquals(getEntryList(expectedEntries, getSenders), getEntryList(actualEntries, getSenders));
    assertEquals(getEntryList(expectedEntries, getRecipients), getEntryList(actualEntries, getRecipients));

    // check all emails on SMTP server
    checkMessageOnWiserServer(getEntryList(actualEntries, getSenders));
  }

  private void checkMessageOnWiserServer(List<String> senders) {
    List<String> addressList = wiser.getMessages().stream()
      .map(msg -> {
        try {
          return msg.getMimeMessage().getFrom();
        } catch (MessagingException ex) {
          throw new AssertionFailedError(ex.getMessage());
        }
      })
      .flatMap(Arrays::stream)
      .map(Address::toString)
      .collect(Collectors.toList());

    assertTrue(addressList.containsAll(senders));
  }

  private List<String> getEntryList(List<EmailEntity> emailEntity, Function<EmailEntity, String> fn) {
    return emailEntity.stream()
      .map(fn)
      .collect(Collectors.toList());
  }

  private List<EmailEntity> generateEntityList(int batchSize) {
    return IntStream.range(0, batchSize)
      .mapToObj(value ->
        new EmailEntity()
          .withNotificationId(Integer.toString(value))
          .withTo(String.format(ADDRESS_TEMPLATE, RandomStringUtils.randomAlphabetic(10)))
          .withFrom(String.format(ADDRESS_TEMPLATE, RandomStringUtils.randomAlphabetic(10)))
          .withHeader("Reset password")
          .withBody("Test text for the message. Random text: " + RandomStringUtils.randomAlphabetic(200))
          .withOutputFormat(MediaType.TEXT_PLAIN)
      )
      .collect(Collectors.toList());
  }

  private List<EmailEntity> generateEntityListWithoutSender(int batchSize) {
    return IntStream.range(0, batchSize)
      .mapToObj(value ->
        new EmailEntity()
          .withNotificationId(Integer.toString(value))
          .withTo(String.format(ADDRESS_TEMPLATE, RandomStringUtils.randomAlphabetic(10)))
          .withHeader("Reset password")
          .withBody("Test text for the message. Random text: " + RandomStringUtils.randomAlphabetic(200))
          .withOutputFormat(MediaType.TEXT_PLAIN)
      )
      .collect(Collectors.toList());
  }

  private Response postEmailsMessages(String okapiUrl, List<EmailEntity> emailEntityList) {
    return RestAssured.given()
      .port(port)
      .contentType(MediaType.APPLICATION_JSON)
      .header(new Header(OKAPI_HEADER_TENANT, OKAPI_TENANT))
      .header(new Header(OKAPI_URL_HEADER, okapiUrl))
      .header(new Header(OKAPI_HEADER_TOKEN, OKAPI_TOKEN))
      .body(convertListEntityToJson(emailEntityList).toString())
      .when()
      .post(REST_BATCH_EMAILS);
  }

  private Response getEmailsMessages(String okapiUrl, String status, int limit) {
    return RestAssured.given()
      .port(port)
      .contentType(MediaType.APPLICATION_JSON)
      .header(new Header(OKAPI_HEADER_TENANT, OKAPI_TENANT))
      .header(new Header(OKAPI_URL_HEADER, okapiUrl))
      .header(new Header(OKAPI_HEADER_TOKEN, OKAPI_TOKEN))
      .when()
      .get(String.format(REST_PATH_WITH_QUERY, REST_BATCH_EMAILS, status, limit));
  }

  private Response runSendEmailTrigger(String okapiUrl) {
    return RestAssured.given()
      .port(port)
      .contentType(MediaType.APPLICATION_JSON)
      .header(new Header(OKAPI_HEADER_TENANT, OKAPI_TENANT))
      .header(new Header(OKAPI_URL_HEADER, okapiUrl))
      .header(new Header(OKAPI_HEADER_TOKEN, OKAPI_TOKEN))
      .when()
      .get("/delayedTask/sendBatchEmails");
  }

  private EmailEntries convertEntriesToJson(Response response) {
    return new JsonObject(response.getBody().print()).mapTo(EmailEntries.class);
  }

  private JsonObject convertListEntityToJson(List<EmailEntity> emailEntityList) {
    return JsonObject.mapFrom(new EmailEntries()
      .withEmailEntity(emailEntityList)
      .withTotalRecords(emailEntityList.size()));
  }
}
