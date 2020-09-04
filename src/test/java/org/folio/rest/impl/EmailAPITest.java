package org.folio.rest.impl;

import static junit.framework.TestCase.assertTrue;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TOKEN;
import static org.folio.util.StubUtils.getEmailEntity;
import static org.folio.util.StubUtils.getIncorrectConfigurations;
import static org.folio.util.StubUtils.initFailModConfigStub;
import static org.folio.util.StubUtils.initIncorrectConfigurations;
import static org.folio.util.StubUtils.initModConfigStub;
import static org.hamcrest.core.StringContains.containsString;

import javax.ws.rs.core.MediaType;

import org.apache.http.HttpStatus;
import org.folio.rest.RestVerticle;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;

import io.restassured.RestAssured;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.rest.persist.PostgresClient;

@RunWith(VertxUnitRunner.class)
public class EmailAPITest {

  private static final String OKAPI_URL = "x-okapi-url";
  private static final String HTTP_PORT = "http.port";
  private static final String REST_PATH = "/email";
  private static final String OKAPI_TENANT = "test_tenant";
  private static final String OKAPI_URL_TEMPLATE = "http://localhost:%s";

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
    PostgresClient.stopEmbeddedPostgres();
    vertx.close(context.asyncAssertSuccess(res -> async.complete()));
  }

  @Test
  public void checkHeadersInRequestToConfigModule() {
    int mockServerPort = userMockServer.port();
    initModConfigStub(mockServerPort, initIncorrectConfigurations());

    String okapiUrl = String.format(OKAPI_URL_TEMPLATE, mockServerPort);
    String okapiEmailEntity = getEmailEntity("user@user.com", "admin@admin.com", null);

    getResponse(okapiUrl, okapiEmailEntity)
      .then()
      .statusCode(HttpStatus.SC_OK);
  }

  @Test
  public void checkIncorrectSmtpConfiguration() {
    int mockServerPort = userMockServer.port();
    initModConfigStub(mockServerPort, getIncorrectConfigurations());

    String okapiUrl = String.format(OKAPI_URL_TEMPLATE, mockServerPort);
    String okapiEmailEntity = getEmailEntity("user@user.com", "admin@admin.com", null);
    String expectedErrMsg = "The 'mod-config' module doesn't have a minimum config for SMTP server";

    getResponse(okapiUrl, okapiEmailEntity)
      .then()
      .statusCode(HttpStatus.SC_OK)
      .body(containsString(expectedErrMsg));
  }

  @Test
  public void checkFailResponseFromConfigModule() {
    int mockServerPort = userMockServer.port();
    initFailModConfigStub(mockServerPort);

    String okapiUrl = String.format(OKAPI_URL_TEMPLATE, mockServerPort);
    String okapiEmailEntity = getEmailEntity("user@user.com", "admin@admin.com", null);
    String expectedErrMsg = "Error looking up config at url";

    getResponse(okapiUrl, okapiEmailEntity)
      .then()
      .statusCode(HttpStatus.SC_BAD_REQUEST)
      .body(containsString(expectedErrMsg));
  }

  @Test
  public void shouldReturnFailedResultWhenRequestWithoutJson() {
    String expectedResponse = "The object to be validated must not be null";

    getResponse(String.format(OKAPI_URL_TEMPLATE, userMockServer.port()))
      .then()
      .statusCode(HttpStatus.SC_BAD_REQUEST)
      .body(containsString(expectedResponse));
  }

  @Test
  public void shouldReturnFailedResultWhenRequestWithIncorrectEmailEntity() {
    String expectedResponse = "\"message\":\"must not be null\"";
    String okapiEmailEntity = getEmailEntity(null, "admin@admin.com", null);

    Response response = getResponse(String.format(OKAPI_URL_TEMPLATE, userMockServer.port()), okapiEmailEntity)
      .then()
      .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
      .extract()
      .response();

    assertTrue(response.asString().contains(expectedResponse));
  }

  private Response getResponse(String okapiUrl) {
    return getRequestSpecification(okapiUrl)
      .when()
      .post(REST_PATH);
  }

  private Response getResponse(String okapiUrl, String body) {
    return getRequestSpecification(okapiUrl)
      .body(body)
      .when()
      .post(REST_PATH);
  }

  private RequestSpecification getRequestSpecification(String okapiUrl) {
    return RestAssured.given()
      .port(port)
      .contentType(MediaType.APPLICATION_JSON)
      .header(new Header(OKAPI_HEADER_TENANT, OKAPI_TENANT))
      .header(new Header(OKAPI_URL, okapiUrl));
  }

}
