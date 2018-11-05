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
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.http.HttpStatus;
import org.folio.rest.RestVerticle;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.ws.rs.core.MediaType;

import static junit.framework.TestCase.assertTrue;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TOKEN;
import static org.folio.util.StubUtils.*;

@RunWith(VertxUnitRunner.class)
public class EmailAPITest {

  private static final String OKAPI_URL = "x-okapi-url";
  private static final String HTTP_PORT = "http.port";
  private static final String REST_PATH = "/email";

  private static Vertx vertx;
  private static int port;

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

    DeploymentOptions restDeploymentOptions = new DeploymentOptions().setConfig(new JsonObject().put(HTTP_PORT, port));
    vertx.deployVerticle(RestVerticle.class.getName(), restDeploymentOptions, res -> async.complete());
  }

  @AfterClass
  public static void tearDownClass(final TestContext context) {
    Async async = context.async();
    vertx.close(context.asyncAssertSuccess(res -> async.complete()));
  }

  @Test
  public void checkHeadersInRequestToConfigModule() {
    int mockServerPort = userMockServer.port();
    initModConfigStub(mockServerPort, initIncorrectConfigurations());
    String okapiTenant = "test_tenant";
    String okapiUrl = "http://localhost:" + mockServerPort;
    String okapiToken = "test_token";
    String okapiEmailEntity = getEmailEntity(1, "user@user.com", "admin@admin.com", "Reset password", "Text body", "Text message", null);

    RestAssured.given()
      .port(port)
      .contentType(MediaType.APPLICATION_JSON)
      .header(new Header(OKAPI_HEADER_TENANT, okapiTenant))
      .header(new Header(OKAPI_URL, okapiUrl))
      .header(new Header(OKAPI_HEADER_TOKEN, okapiToken))
      .body(okapiEmailEntity)
      .when()
      .post(REST_PATH)
      .then()
      .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR)
      .header(OKAPI_HEADER_TENANT, okapiTenant)
      .header(OKAPI_URL, okapiUrl)
      .header(OKAPI_HEADER_TOKEN, okapiToken);
  }

  @Test
  public void shouldReturnFailedResultWithMessageWhenConfigNotFound() {
    int mockServerPort = userMockServer.port();
    initModConfigStub(mockServerPort, initIncorrectConfigurations());
    String okapiUrl = "http://localhost:" + mockServerPort;
    String expectedResponse = "The 'mod-config' module doesn't have a minimum config for SNTP server";

    Response response = RestAssured.given()
      .port(EmailAPITest.port)
      .contentType(MediaType.APPLICATION_JSON)
      .header(new Header(OKAPI_HEADER_TENANT, "tenant"))
      .header(new Header(OKAPI_URL, okapiUrl))
      .header(new Header(OKAPI_HEADER_TOKEN, "token"))
      .body(getEmailEntity(1, "user@user.com", "admin@admin.com", "Reset password", "body", "message", "text"))
      .when()
      .post(REST_PATH)
      .then()
      .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR)
      .extract().response();

    assertTrue(response.asString().contains(expectedResponse));
  }

  @Test
  public void shouldReturnFailedResultIncorrectSmtpConfig() {
    int mockServerPort = userMockServer.port();
    initModConfigStub(mockServerPort, getConfigurations());
    String okapiUrl = "http://localhost:" + mockServerPort;
    String expectedResponse = "failed to resolve";

    Response response = RestAssured.given()
      .port(EmailAPITest.port)
      .contentType(MediaType.APPLICATION_JSON)
      .header(new Header(OKAPI_HEADER_TENANT, "tenant"))
      .header(new Header(OKAPI_URL, okapiUrl))
      .header(new Header(OKAPI_HEADER_TOKEN, "token"))
      .body(getEmailEntity(1, "user@user.com", "admin@admin.com", "Reset password", "body", "message", "text/html"))
      .when()
      .post(REST_PATH)
      .then()
      .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR)
      .extract().response();

    assertTrue(response.asString().contains(expectedResponse));
  }

  @Test
  public void shouldReturnFailedResultWhenRequestWithoutJson() {
    int mockServerPort = userMockServer.port();
    String okapiUrl = "http://localhost:" + mockServerPort;
    String expectedResponse = "The object to be validated must not be null";

    Response response = RestAssured.given()
      .port(EmailAPITest.port)
      .contentType(MediaType.APPLICATION_JSON)
      .header(new Header(OKAPI_HEADER_TENANT, "tenant"))
      .header(new Header(OKAPI_URL, okapiUrl))
      .header(new Header(OKAPI_HEADER_TOKEN, "token"))
      .when()
      .post(REST_PATH)
      .then()
      .statusCode(HttpStatus.SC_BAD_REQUEST)
      .extract().response();

    assertTrue(response.asString().contains(expectedResponse));
  }

  @Test
  public void shouldReturnFailedResultWhenRequestWithIncorrectEmailEntity() {
    int mockServerPort = userMockServer.port();
    String okapiUrl = "http://localhost:" + mockServerPort;
    String expectedResponse = "\"message\":\"may not be null\"";

    Response response = RestAssured.given()
      .port(EmailAPITest.port)
      .contentType(MediaType.APPLICATION_JSON)
      .header(new Header(OKAPI_HEADER_TENANT, "tenant"))
      .header(new Header(OKAPI_URL, okapiUrl))
      .header(new Header(OKAPI_HEADER_TOKEN, "token"))
      .body(getEmailEntity(1, null, "admin@admin.com", "Reset password", "body", null, null))
      .when()
      .post(REST_PATH)
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .extract().response();

    assertTrue(response.asString().contains(expectedResponse));
  }
}
