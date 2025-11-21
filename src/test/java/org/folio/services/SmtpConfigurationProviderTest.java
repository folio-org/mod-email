package org.folio.services;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.util.Collections.emptyMap;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static javax.ws.rs.core.Response.Status.OK;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.quality.Strictness.STRICT_STUBS;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.folio.exceptions.ConfigurationException;
import org.folio.exceptions.SmtpConfigurationException;
import org.folio.exceptions.SmtpConfigurationNotFoundException;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.client.OkapiClient;
import org.folio.rest.jaxrs.model.SmtpConfiguration;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.PostgresClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(VertxUnitRunner.class)
public class SmtpConfigurationProviderTest {

  private static final String TEST_HOST = "smtp.test.com";
  private static final Integer TEST_PORT = 587;
  private static final String TEST_USERNAME = "test@test.com";
  private static final String TEST_PASSWORD = "password123";
  public static final String CONFIG_ID = UUID.randomUUID().toString();

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule().strictness(STRICT_STUBS);

  private SmtpConfigurationProvider provider;
  @Mock private Conn conn;
  @Mock private OkapiClient okapiClient;
  @Mock private PostgresClient postgresClient;
  @Mock private MailSettingsService settingsService;
  @Mock private SmtpConfigurationService smtpConfigurationService;

  @Before
  public void setUp() {
    provider = new SmtpConfigurationProvider(settingsService, postgresClient,
      (unused) -> okapiClient, () -> smtpConfigurationService);
  }

  @After
  public void tearDown() {
    verifyNoMoreInteractions(conn, settingsService, okapiClient, smtpConfigurationService);
  }

  @Test
  public void lookup_positive_foundInSettings(TestContext context) {
    when(postgresClient.withTrans(any())).then(this::withTransHandler);
    when(settingsService.getSmtpConfigSetting(conn)).thenReturn(smtpConfigFuture());

    provider.lookup(requestHeaders()).onComplete(context.asyncAssertSuccess(config ->
      context.assertEquals(config, smtpConfig())));

    verify(settingsService).getSmtpConfigSetting(any());
  }

  @Test
  public void lookup_positive_foundInSmtpRepository(TestContext context) {
    when(postgresClient.withTrans(any())).then(this::withTransHandler);
    when(settingsService.getSmtpConfigSetting(conn)).thenReturn(notFoundConfigFuture());
    when(settingsService.createSmtpConfigSetting(conn, smtpConfig())).thenReturn(smtpConfigFuture());

    when(smtpConfigurationService.getSmtpConfiguration(conn)).thenReturn(smtpConfigFuture());
    when(smtpConfigurationService.deleteSmtpConfiguration(conn, CONFIG_ID)).thenReturn(succeededFuture(true));

    provider.lookup(requestHeaders()).onComplete(context.asyncAssertSuccess(config ->
      context.assertEquals(config, smtpConfig())));
  }

  @Test
  public void lookup_positive_foundInModConfiguration(TestContext context) {
    when(postgresClient.withTrans(any())).then(this::withTransHandler);
    when(settingsService.getSmtpConfigSetting(conn)).thenReturn(notFoundConfigFuture());
    when(smtpConfigurationService.getSmtpConfiguration(conn)).thenReturn(notFoundConfigFuture());
    when(settingsService.createSmtpConfigSetting(eq(conn), any())).thenReturn(smtpConfigFuture());

    var getHttpRequest = Mockito.<HttpRequest<Buffer>>mock();
    var getHttpResponse = Mockito.<HttpResponse<Buffer>>mock();

    when(okapiClient.getAbs(expectedModConfigQuery())).thenReturn(getHttpRequest);
    when(getHttpRequest.send()).thenReturn(succeededFuture(getHttpResponse));
    when(getHttpResponse.statusCode()).thenReturn(OK.getStatusCode());
    when(getHttpResponse.bodyAsJsonObject()).thenReturn(modConfigurationEntries());

    var deleteHttpRequest = Mockito.<HttpRequest<Buffer>>mock();
    var deleteHttpResponse = Mockito.<HttpResponse<Buffer>>mock();
    when(okapiClient.deleteAbs(startsWith("/configurations/entries"))).thenReturn(deleteHttpRequest);
    when(deleteHttpRequest.send()).thenReturn(succeededFuture(deleteHttpResponse));
    when(deleteHttpResponse.statusCode()).thenReturn(NO_CONTENT.getStatusCode());

    provider.lookup(requestHeaders()).onComplete(context.asyncAssertSuccess(config ->
      context.assertEquals(config, smtpConfig())));

    verify(okapiClient, times(4)).deleteAbs(any());
  }

  @Test
  public void lookup_positive_foundInModConfigurationFailedRemoval(TestContext context) {
    when(postgresClient.withTrans(any())).then(this::withTransHandler);
    when(settingsService.getSmtpConfigSetting(conn)).thenReturn(notFoundConfigFuture());
    when(smtpConfigurationService.getSmtpConfiguration(conn)).thenReturn(notFoundConfigFuture());
    when(settingsService.createSmtpConfigSetting(eq(conn), any())).thenReturn(smtpConfigFuture());

    var getHttpRequest = Mockito.<HttpRequest<Buffer>>mock();
    var getHttpResponse = Mockito.<HttpResponse<Buffer>>mock();

    when(okapiClient.getAbs(expectedModConfigQuery())).thenReturn(getHttpRequest);
    when(getHttpRequest.send()).thenReturn(succeededFuture(getHttpResponse));
    when(getHttpResponse.statusCode()).thenReturn(OK.getStatusCode());
    when(getHttpResponse.bodyAsJsonObject()).thenReturn(modConfigurationEntries());

    var deleteHttpRequest = Mockito.<HttpRequest<Buffer>>mock();
    var deleteHttpResponse = Mockito.<HttpResponse<Buffer>>mock();
    when(okapiClient.deleteAbs(startsWith("/configurations/entries"))).thenReturn(deleteHttpRequest);
    when(deleteHttpRequest.send()).thenReturn(succeededFuture(deleteHttpResponse));
    when(deleteHttpResponse.statusCode()).thenReturn(INTERNAL_SERVER_ERROR.getStatusCode());

    provider.lookup(requestHeaders()).onComplete(context.asyncAssertSuccess(config ->
      context.assertEquals(config, smtpConfig())));

    verify(okapiClient, times(4)).deleteAbs(any());
  }

  @Test
  public void lookup_positive_emptyDataInModConfiguration(TestContext context) {
    when(postgresClient.withTrans(any())).then(this::withTransHandler);
    when(settingsService.getSmtpConfigSetting(conn)).thenReturn(notFoundConfigFuture());
    when(smtpConfigurationService.getSmtpConfiguration(conn)).thenReturn(notFoundConfigFuture());

    var getHttpRequest = Mockito.<HttpRequest<Buffer>>mock();
    var getHttpResponse = Mockito.<HttpResponse<Buffer>>mock();

    when(okapiClient.getAbs(expectedModConfigQuery())).thenReturn(getHttpRequest);
    when(getHttpRequest.send()).thenReturn(succeededFuture(getHttpResponse));
    when(getHttpResponse.statusCode()).thenReturn(OK.getStatusCode());
    when(getHttpResponse.bodyAsJsonObject()).thenReturn(emptyModConfigurationEntries());

    provider.lookup(requestHeaders()).onComplete(context.asyncAssertFailure(error -> {
      context.assertTrue(error instanceof SmtpConfigurationException);
      var expectedErrorMessage =
        "The 'mod-config' module doesn't have a minimum config for SMTP server, "
          + "the min config is: [EMAIL_SMTP_PORT, EMAIL_PASSWORD, EMAIL_SMTP_HOST, EMAIL_USERNAME]";
      context.assertEquals(expectedErrorMessage, error.getMessage());
    }));
  }

  @Test
  public void lookup_positive_invalidResponseFromModConfiguration(TestContext context) {
    when(postgresClient.withTrans(any())).then(this::withTransHandler);
    when(settingsService.getSmtpConfigSetting(conn)).thenReturn(notFoundConfigFuture());
    when(smtpConfigurationService.getSmtpConfiguration(conn)).thenReturn(notFoundConfigFuture());

    var getHttpRequest = Mockito.<HttpRequest<Buffer>>mock();
    var getHttpResponse = Mockito.<HttpResponse<Buffer>>mock();

    when(okapiClient.getAbs(expectedModConfigQuery())).thenReturn(getHttpRequest);
    when(getHttpRequest.send()).thenReturn(succeededFuture(getHttpResponse));
    when(getHttpResponse.statusCode()).thenReturn(INTERNAL_SERVER_ERROR.getStatusCode());
    when(getHttpResponse.bodyAsString()).thenReturn("500::test error");

    provider.lookup(requestHeaders()).onComplete(context.asyncAssertFailure(error -> {
      context.assertTrue(error instanceof ConfigurationException);
      var expectedErrorMessage = "Error looking up config at /configurations/entries"
        + "?query=module==SMTP_SERVER | Expected status code 200, got 500 "
        + "| error message: 500::test error";
      context.assertEquals(expectedErrorMessage, error.getMessage());
    }));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void defaultConstructor_positive() throws Exception{
    var provider = new SmtpConfigurationProvider(Vertx.vertx(), settingsService, postgresClient);
    assertNotNull(provider);

    var field = SmtpConfigurationProvider.class.getDeclaredField("okapiClientSupplier");
    field.setAccessible(true);

    var okapiClientSupplier = (Function<Map<String, String>, OkapiClient>) field.get(provider);

    assertNotNull(okapiClientSupplier);
    assertNotNull(okapiClientSupplier.apply(requestHeaders()));
  }

  private static SmtpConfiguration smtpConfig() {
    SmtpConfiguration config = new SmtpConfiguration();
    config.setId(CONFIG_ID);
    config.setHost(TEST_HOST);
    config.setPort(TEST_PORT);
    config.setUsername(TEST_USERNAME);
    config.setPassword(TEST_PASSWORD);
    return config;
  }

  private static Future<SmtpConfiguration> smtpConfigFuture() {
    return succeededFuture(smtpConfig());
  }

  private static Future<SmtpConfiguration> notFoundConfigFuture() {
    return failedFuture(new SmtpConfigurationNotFoundException());
  }

  private Future<?> withTransHandler(InvocationOnMock inv) {
    var handler = inv.<Function<Conn, Future<?>>>getArgument(0);
    return handler.apply(conn);
  }

  private static String expectedModConfigQuery() {
    return "/configurations/entries?query=module==SMTP_SERVER";
  }

  private static JsonObject modConfigurationEntries() {
    return new JsonObject()
      .put("configs", new JsonArray()
        .add(smtpConfigValue("EMAIL_SMTP_HOST", TEST_HOST))
        .add(smtpConfigValue("EMAIL_SMTP_PORT", TEST_PORT))
        .add(smtpConfigValue("EMAIL_USERNAME", TEST_USERNAME))
        .add(smtpConfigValue("EMAIL_PASSWORD", TEST_PASSWORD))
      )
      .put("totalRecords", 4);
  }

  private static JsonObject emptyModConfigurationEntries() {
    return new JsonObject()
      .put("configs", new JsonArray())
      .put("totalRecords", 0);
  }

  private static JsonObject smtpConfigValue(String key, Object value) {
    return new JsonObject()
      .put("id", UUID.randomUUID().toString())
      .put("code", key)
      .put("value", value)
      .put("configName", "email")
      .put("module", "SMTP_SERVER")
      .put("description", "SMTP server configuration")
      .put("enabled", true)
      .put("default", true);
  }

  private static Map<String, String> requestHeaders() {
    return Map.of(
      XOkapiHeaders.TENANT, "test_tenant",
      XOkapiHeaders.URL, "http://okapi:9130",
      XOkapiHeaders.TOKEN, "JWT_TEST_TOKEN"
    );
  }
}
