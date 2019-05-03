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
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.Attachment;
import org.folio.rest.jaxrs.model.EmailEntity;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.subethamail.wiser.Wiser;
import org.subethamail.wiser.WiserMessage;

import javax.mail.Address;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.ws.rs.core.MediaType;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static junit.framework.TestCase.fail;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TOKEN;
import static org.folio.util.StubUtils.getIncorrectWiserMockConfigurations;
import static org.folio.util.StubUtils.getWiserMockConfigurations;
import static org.folio.util.StubUtils.initModConfigStub;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(VertxUnitRunner.class)
public class SendingEmailTest {

  private final Logger logger = LoggerFactory.getLogger(SendingEmailTest.class);

  private static final String OKAPI_URL = "x-okapi-url";
  private static final String HTTP_PORT = "http.port";
  private static final String REST_PATH = "/email";
  private static final String OKAPI_TENANT = "test_tenant";
  private static final String OKAPI_TOKEN = "test_token";
  private static final String OKAPI_URL_TEMPLATE = "http://localhost:%s";

  private static final String ADDRESS_TEMPLATE = "%s@localhost";
  private static final String SUCCESS_SEND_EMAIL = "The message has been delivered to %s";
  private static final String MESSAGE_NOT_FOUND = "The message for the sender: `%s` was not found on the SMTP server";

  private static Vertx vertx;
  private static int port;
  private static Wiser wiser;

  @Rule
  public WireMockRule userMockServer = new WireMockRule(
    WireMockConfiguration.wireMockConfig()
      .dynamicPort()
      .notifier(new ConsoleNotifier(true)));

  @BeforeClass
  public static void setUpClass(final TestContext context) {
    Async async = context.async();
    vertx = Vertx.vertx();
    port = NetworkUtils.nextFreePort();

    wiser = new Wiser();
    wiser.setPort(2500);

    DeploymentOptions restDeploymentOptions = new DeploymentOptions().setConfig(new JsonObject().put(HTTP_PORT, port));
    vertx.deployVerticle(RestVerticle.class.getName(), restDeploymentOptions, res ->
      {
        wiser.start();
        async.complete();
      }
    );
  }

  @AfterClass
  public static void tearDownClass(final TestContext context) {
    Async async = context.async();
    vertx.close(context.asyncAssertSuccess(res -> {
      wiser.stop();
      async.complete();
    }));
  }

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

    Response response = getResponse(String.format(OKAPI_URL_TEMPLATE, mockServerPort), emailEntity)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract()
      .response();
    checkResponse(response, recipient);

    WiserMessage wiserMessage = findMessageOnWiserServer(sender);
    checkMessagesOnWiserServer(wiserMessage, emailEntity);
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

    Response response = getResponse(String.format(OKAPI_URL_TEMPLATE, mockServerPort), emailEntity)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract()
      .response();
    checkResponse(response, recipient);

    WiserMessage wiserMessage = findMessageOnWiserServer(sender);
    checkMessagesOnWiserServer(wiserMessage, emailEntity);
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

    Response response = getResponse(String.format(OKAPI_URL_TEMPLATE, mockServerPort), emailEntity)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract()
      .response();
    checkResponse(response, recipient);

    WiserMessage wiserMessage = findMessageOnWiserServer(sender);
    checkMessagesOnWiserServer(wiserMessage, emailEntity);

    String fullMessageInfo = wiserMessage.toString();
    Attachment attachment = emailEntity.getAttachments().get(0);

    assertTrue(fullMessageInfo.contains(attachment.getContentId()));
    assertTrue(fullMessageInfo.contains(attachment.getContentType()));
    assertTrue(fullMessageInfo.contains(attachment.getDescription()));
    assertTrue(fullMessageInfo.contains(attachment.getName()));
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

    Response response = getResponse(String.format(OKAPI_URL_TEMPLATE, mockServerPort), emailEntity)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract()
      .response();
    checkResponse(response, recipient);

    WiserMessage wiserMessage = findMessageOnWiserServer(sender);
    checkMessagesOnWiserServer(wiserMessage, emailEntity);
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

    Response response = getResponse(String.format(OKAPI_URL_TEMPLATE, mockServerPort), emailEntity)
      .then()
      .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR)
      .extract()
      .response();
    assertEquals("Internal Server Error", response.getBody().asString());

    try {
      findMessageOnWiserServer(sender);
      fail();
    } catch (AssertionFailedError ex) {
      //ignore
    }

    // init correct SMTP mock configuration
    initModConfigStub(mockServerPort, getWiserMockConfigurations());

    response = getResponse(String.format(OKAPI_URL_TEMPLATE, mockServerPort), emailEntity)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract()
      .response();

    checkResponse(response, recipient);

    WiserMessage wiserMessage = findMessageOnWiserServer(sender);
    checkMessagesOnWiserServer(wiserMessage, emailEntity);
  }

  private void checkResponse(Response response, String recipient) {
    String responseMessage = response.getBody().asString();
    assertEquals(String.format(SUCCESS_SEND_EMAIL, recipient), responseMessage);
  }

  private void checkMessagesOnWiserServer(WiserMessage wiserMessage,
                                          EmailEntity emailEntity) throws MessagingException {

    assertTrue(wiserMessage.toString().contains(emailEntity.getBody()));

    MimeMessage message = wiserMessage.getMimeMessage();
    assertEquals(emailEntity.getHeader(), message.getSubject());

    Address[] from = message.getFrom();
    checkAddress(emailEntity.getFrom(), from);

    Address[] recipients = message.getAllRecipients();
    checkAddress(emailEntity.getTo(), recipients);
  }

  private void checkAddress(String expectedAddress, Address[] address) {
    assertTrue(isContainsSenderAddress(expectedAddress, address));
  }

  private WiserMessage findMessageOnWiserServer(String sender) {
    return wiser.getMessages().stream()
      .filter(msg -> {
        try {
          return isContainsSenderAddress(sender, msg.getMimeMessage().getFrom());
        } catch (MessagingException ex) {
          logger.debug(ex);
          throw throwAssertionFailedError(sender);
        }
      })
      .findFirst()
      .orElseThrow(() -> throwAssertionFailedError(sender));
  }

  private AssertionFailedError throwAssertionFailedError(String sender) {
    return new AssertionFailedError(String.format(MESSAGE_NOT_FOUND, sender));
  }

  private boolean isContainsSenderAddress(String sender, Address[] address) {
    return Arrays.stream(address)
      .map(Address::toString).
        anyMatch(ad -> ad.equals(sender));
  }

  private Response getResponse(String okapiUrl, EmailEntity emailEntity) {
    return RestAssured.given()
      .port(port)
      .contentType(MediaType.APPLICATION_JSON)
      .header(new Header(OKAPI_HEADER_TENANT, OKAPI_TENANT))
      .header(new Header(OKAPI_URL, okapiUrl))
      .header(new Header(OKAPI_HEADER_TOKEN, OKAPI_TOKEN))
      .body(JsonObject.mapFrom(emailEntity).toString())
      .when()
      .post(REST_PATH);
  }
}
