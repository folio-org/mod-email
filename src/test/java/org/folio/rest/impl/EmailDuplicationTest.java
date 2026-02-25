package org.folio.rest.impl;

import static java.lang.String.format;
import static org.apache.commons.lang3.RandomStringUtils.secure;
import static org.folio.util.StubUtils.createConfigurations;
import static org.folio.util.StubUtils.initModConfigStub;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import javax.ws.rs.core.MediaType;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import eu.rekawek.toxiproxy.model.ToxicDirection;
import io.vertx.core.json.JsonObject;
import org.apache.http.HttpStatus;
import org.folio.rest.impl.base.AbstractAPITest;
import org.folio.rest.jaxrs.model.EmailEntity;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.ToxiproxyContainer;

public class EmailDuplicationTest extends AbstractAPITest {

  private static final String AUTH_METHODS = "CRAM-MD5 LOGIN PLAIN";
  private static final ObjectMapper objectMapper = new ObjectMapper();
  private static final HttpClient httpClient = HttpClient.newHttpClient();

  private static Network network;

  @ClassRule
  public static GenericContainer<?> mailhog = new GenericContainer<>("mailhog/mailhog:latest")
    .withExposedPorts(1025, 8025)  // 1025=SMTP, 8025=HTTP API
    .withNetwork(Network.SHARED)
    .withNetworkAliases("mailhog");

  @ClassRule
  public static ToxiproxyContainer toxiproxy = new ToxiproxyContainer(
    "ghcr.io/shopify/toxiproxy:2.11.0")
    .withNetwork(Network.SHARED)
    .withNetworkAliases("toxiproxy");

  private Proxy smtpProxy;
  private int mockServerPort;

  @BeforeClass
  public static void setUpSmtp() {
    network = Network.SHARED;
  }

  @AfterClass
  public static void tearDownSmtp() {
    if (network != null) {
      network.close();
    }
  }

  @Before
  public void setUp() throws IOException {
    mockServerPort = userMockServer.port();
    var toxiproxyClient = new ToxiproxyClient(toxiproxy.getHost(), toxiproxy.getControlPort());

    try {
      var oldProxy = toxiproxyClient.getProxy("smtp");
      if (oldProxy != null) {
        oldProxy.delete();
      }
    } catch (Exception e) {
      // ignore
    }

    smtpProxy = toxiproxyClient.createProxy("smtp", "0.0.0.0:8666", "mailhog:1025");

    var proxyPort = toxiproxy.getMappedPort(8666);

    var configurations = createConfigurations("user", "password",
      toxiproxy.getHost(), String.valueOf(proxyPort), AUTH_METHODS);
    initModConfigStub(mockServerPort, configurations);

    clearMailhogMessages();
  }

  @After
  public void tearDown() throws IOException {
    clearMailhogMessages();

    if (smtpProxy != null) {
      try {
        smtpProxy.delete();
      } catch (Exception e) {
        // ignore
      }
      smtpProxy = null;
    }
  }

  @Test
  public void shouldNotDuplicateWithIncreasedTimeout() throws Exception {
    smtpProxy.toxics()
      .latency("slow_smtp", ToxicDirection.DOWNSTREAM, 500);

    var sender = format(ADDRESS_TEMPLATE, secure().nextAlphabetic(7));
    var recipient = format(ADDRESS_TEMPLATE, secure().nextAlphabetic(5));
    var msg = "Timeout test body: " + secure().nextAlphabetic(25);

    var emailEntity = new EmailEntity()
      .withId(UUID.randomUUID().toString())
      .withNotificationId("timeout-test-1")
      .withTo(recipient)
      .withFrom(sender)
      .withHeader("Timeout Test")
      .withBody(msg)
      .withOutputFormat(MediaType.TEXT_PLAIN);

    var response = sendEmail(emailEntity)
      .then()
      .extract()
      .response();

    var statusCode = response.getStatusCode();
    assertEquals("Expected OK status", HttpStatus.SC_OK, statusCode);

    var messageCount = getMailhogMessageCount();
    assertEquals("Expected exactly one message (no duplicate)", 1, messageCount);
  }

  @Test
  public void shouldTimeoutWithVerySlowSmtp() throws Exception {
    smtpProxy.toxics()
      .latency("very_slow_smtp", ToxicDirection.DOWNSTREAM, 26_000);

    var sender = format(ADDRESS_TEMPLATE, secure().nextAlphabetic(7));
    var recipient = format(ADDRESS_TEMPLATE, secure().nextAlphabetic(5));
    var msg = "Timeout test body: " + secure().nextAlphabetic(25);

    var emailEntity = new EmailEntity()
      .withId(UUID.randomUUID().toString())
      .withNotificationId("timeout-test-1")
      .withTo(recipient)
      .withFrom(sender)
      .withHeader("Timeout Test")
      .withBody(msg)
      .withOutputFormat(MediaType.TEXT_PLAIN);

    var response = sendEmail(emailEntity)
      .then()
      .extract()
      .response();

    var responseBody = response.getBody().asString();
    assertTrue("Expected timeout error",
      responseBody.contains("the module didn't send email") || responseBody.contains("socket was closed"));

    var messageCount = getMailhogMessageCount();
    assertEquals("Expected no messages (timeout prevented sending)", 0, messageCount);
  }

  @Test
  public void shouldTimeoutOnSlowLargeEmail() throws Exception {
    var messageSizeMB = 5;
    var targetDurationSeconds = 15;
    var bandwidthKBps = (messageSizeMB * 1024) / targetDurationSeconds;

    smtpProxy.toxics()
      .bandwidth("slow_upload", ToxicDirection.UPSTREAM, bandwidthKBps);

    var proxyPort = toxiproxy.getMappedPort(8666);
    var settings = getSettings(proxyPort);
    post(REST_PATH_MAIL_SETTINGS, settings.encodePrettily());

    var sender = format(ADDRESS_TEMPLATE, secure().nextAlphabetic(7));
    var recipient = format(ADDRESS_TEMPLATE, secure().nextAlphabetic(5));
    var msg = generateLargeMessage(messageSizeMB);

    var emailEntity = new EmailEntity()
      .withId(UUID.randomUUID().toString())
      .withNotificationId("large-email-test-" + System.currentTimeMillis())
      .withTo(recipient)
      .withFrom(sender)
      .withHeader("Large Email Test - " + messageSizeMB + "MB")
      .withBody(msg)
      .withOutputFormat(MediaType.TEXT_PLAIN);

    var response = sendEmail(emailEntity)
      .then()
      .extract()
      .response();

    var responseBody = response.getBody().asString();
    assertTrue("Expected timeout error with slow large email transfer",
      responseBody.contains("the module didn't send email") || responseBody.contains("socket was closed"));

    var messageCount = getMailhogMessageCount();
    assertEquals("Expected no messages (timeout prevented sending)", 0, messageCount);
  }

  private static JsonObject getSettings(Integer proxyPort) {
    return new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("key", "smtp-configuration")
      .put("scope", "mod-email")
      .put("_version", 1)
      .put("value", new JsonObject()
        .put("host", toxiproxy.getHost())
        .put("port", proxyPort)
        .put("username", "user")
        .put("password", "password")
        .put("ssl", false)
        .put("loginOption", "NONE")
        .put("startTlsOptions", "OPTIONAL")
        .put("authMethods", AUTH_METHODS)
        .put("from", "")
        .put("idleTimeout", 10));
  }

  private String generateLargeMessage(int sizeMB) {
    var sizeBytes = sizeMB * 1024 * 1024;
    var sb = new StringBuilder(sizeBytes);

    sb.append("Large email test message\n");
    sb.append("Expected size: ").append(sizeMB).append("MB\n");
    sb.append("Generated at: ").append(System.currentTimeMillis()).append("\n");
    sb.append("=".repeat(100)).append("\n\n");

    var chunk = secure().nextAlphanumeric(1000) + "\n";
    int chunksNeeded = (sizeBytes - sb.length()) / chunk.length();

    sb.append(chunk.repeat(Math.max(0, chunksNeeded)));
    return sb.toString();
  }

  private void clearMailhogMessages() {
    try {
      var url = "http://" + mailhog.getHost() + ":" + mailhog.getMappedPort(8025) + "/api/v1/messages";
      var request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .DELETE()
        .build();

      httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (Exception e) {
      // ignore
    }
  }

  private int getMailhogMessageCount() {
    try {
      var url = "http://" + mailhog.getHost() + ":" + mailhog.getMappedPort(8025) + "/api/v2/messages";
      var request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .GET()
        .build();

      var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      var root = objectMapper.readTree(response.body());

      return root.get("count").asInt();
    } catch (Exception e) {
      return -1;
    }
  }
}
