package org.folio.rest.impl;

import static java.lang.String.format;
import static org.apache.commons.lang3.RandomStringUtils.secure;
import static org.folio.util.StubUtils.createConfigurations;
import static org.folio.util.StubUtils.initModConfigStub;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import eu.rekawek.toxiproxy.model.ToxicDirection;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.ws.rs.core.MediaType;
import org.apache.http.HttpStatus;
import org.folio.rest.impl.base.AbstractAPITest;
import org.folio.rest.jaxrs.model.EmailEntity;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.utility.DockerImageName;

@Ignore
public class EmailDuplicationTest extends AbstractAPITest {

  private static final DockerImageName name = DockerImageName.parse("ghcr.io/shopify/toxiproxy:2.5.0");

  @ClassRule
  public static final ToxiproxyContainer toxiproxy =
    new ToxiproxyContainer(name).withExposedPorts(8474, 6666);

  private int mockServerPort;
  private int toxiproxyPort;
  private ToxiproxyClient toxiproxyClient;
  private Proxy smtpProxy;

  @Before
  public void setUp() {
    mockServerPort = userMockServer.port();
  }

  @Before
  public void setUpToxiproxy() throws Exception {
    toxiproxyPort = toxiproxy.getMappedPort(8474);
    toxiproxyClient = new ToxiproxyClient(toxiproxy.getHost(), toxiproxyPort);

    // Ensure stale proxy with same name is removed (e.g., in IDE reruns)
    try {
      var existing = toxiproxyClient.getProxy("smtp");
      existing.delete();
    } catch (Exception ignored) {
      // no existing proxy
    }

    var wiserPort = 2500;
    String upstreamHost = getDockerHostAddress();
    smtpProxy = toxiproxyClient.createProxy("smtp", "0.0.0.0:6666", upstreamHost + ":" + wiserPort);
  }

  @Test
  @Ignore
  public void reproduceDuplicationWithLatencyProxy() throws Exception {
    int targetTotalLatencyMs = 10000;
    int estimatedDownstreamResponses = 6;
    int perPacketLatency = targetTotalLatencyMs / estimatedDownstreamResponses;
    smtpProxy.toxics().latency("latency", ToxicDirection.DOWNSTREAM, perPacketLatency);

    int proxyPort = toxiproxy.getMappedPort(6666);
    var configurations = createConfigurations("user", "password", "127.0.0.1", String.valueOf(proxyPort));
    initModConfigStub(mockServerPort, configurations);

    var sender = format(ADDRESS_TEMPLATE, secure().nextAlphabetic(7));
    var recipient = format(ADDRESS_TEMPLATE, secure().nextAlphabetic(5));
    var msg = "Duplication latency test body: " + secure().nextAlphabetic(25);

    var emailEntity = new EmailEntity()
      .withId(UUID.randomUUID().toString())
      .withNotificationId("dup-latency-1")
      .withTo(recipient)
      .withFrom(sender)
      .withHeader("Latency Duplication Test")
      .withBody(msg)
      .withOutputFormat(MediaType.TEXT_PLAIN);

    var response = sendEmail(emailEntity)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .extract()
      .response();

    var responseBody = response.getBody().asString();
    if (responseBody.contains("the module didn't send email")) {
      post("delayedTask/retryFailedEmails")
        .then()
        .statusCode(HttpStatus.SC_ACCEPTED);
    }

    TimeUnit.SECONDS.sleep(30);

    var messages = getAllMessages();
    assertFalse("Expected messages are not empty", messages.isEmpty());
    assertTrue("Expected message higher than 1", messages.size() > 1);
  }

  private String getDockerHostAddress() {
    // Prefer Testcontainers special DNS name (works on Linux, macOS, Windows)
    String[] candidates = { "host.testcontainers.internal", "host.docker.internal", "localhost" };
    for (String candidate : candidates) {
      try {
        java.net.InetAddress.getByName(candidate); // will throw if unresolved
        return candidate;
      } catch (Exception ignored) {
        // try next
      }
    }
    // Fallback to gateway heuristic
    return "127.0.0.1";
  }
}
