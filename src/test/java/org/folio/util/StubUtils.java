package org.folio.util;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.google.common.collect.Lists;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.dbschema.ObjectMapperTool;
import org.folio.enums.SmtpEmail;
import org.folio.rest.jaxrs.model.Config;
import org.folio.rest.jaxrs.model.Configurations;
import org.folio.rest.jaxrs.model.SmtpConfiguration;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.folio.HttpStatus.HTTP_NO_CONTENT;
import static org.folio.enums.SmtpEmail.AUTH_METHODS;
import static org.folio.enums.SmtpEmail.EMAIL_PASSWORD;
import static org.folio.enums.SmtpEmail.EMAIL_SMTP_HOST;
import static org.folio.enums.SmtpEmail.EMAIL_SMTP_PORT;
import static org.folio.enums.SmtpEmail.EMAIL_USERNAME;

public class StubUtils {

  private static final String MODULE_SMTP_SERVER = "SMTP_SERVER";
  private static final String URL_CONFIGURATIONS_TO_SMTP_SERVER = "/configurations/entries?query=module==" + MODULE_SMTP_SERVER;
  public static final String URL_SINGLE_CONFIGURATION = "/configurations/entries/.+";
  private static final String CONFIG_NAME_SMTP = "smtp";
  private static final String CONFIG_NAME_EMAIL_HEADERS = "email.headers";

  private StubUtils() {
    //not called
  }

  public static String getEmailEntity(String to, String from, String outputFormat) {
    JsonObject entries = new JsonObject();
    if (StringUtils.isNoneBlank(outputFormat)) {
      entries.put("outputFormat", outputFormat);
    }
    return entries
      .put("notificationId", 1)
      .put("to", to)
      .put("from", from)
      .put("header", "Reset password")
      .put("body", "Test message")
      .toString();
  }

  public static void initModConfigStub(int port, Configurations configurations) {
    stubFor(createMappingBuilder()
      .willReturn(aResponse()
        .withHeader("Content-Type", "application/json")
        .withHeader("x-okapi-token", "x-okapi-token-TEST")
        .withHeader("x-okapi-url", "http://localhost:" + port)
        .withBody(ObjectMapperTool.valueAsString(configurations).toString())));

    stubFor(delete(urlMatching(URL_SINGLE_CONFIGURATION))
      .willReturn(aResponse()
        .withStatus(HTTP_NO_CONTENT.toInt())
        .withHeader("x-okapi-token", "x-okapi-token-TEST")
        .withHeader("x-okapi-url", "http://localhost:" + port)));
  }

  public static void initFailModConfigStub(int port) {
    stubFor(createMappingBuilder()
      .willReturn(aResponse()
        .withStatus(404)
        .withHeader("Content-Type", "application/json")
        .withHeader("x-okapi-token", "x-okapi-token-TEST")
        .withHeader("x-okapi-url", "http://localhost:" + port)));
  }

  public static Configurations initIncorrectConfigurations() {
    return new Configurations().withTotalRecords(new Random().nextInt(5));
  }

  public static Configurations getIncorrectConfigurations() {
    return createConfigurations("", "", "", "");
  }

  public static Configurations getWiserMockConfigurations() {
    return createConfigurations("user", "password", "localhost", "2500");
  }

  public static Configurations getIncorrectWiserMockConfigurations() {
    String incorrectPort = "555";
    return createConfigurations("user", "pws", "localhost", incorrectPort);
  }

  private static MappingBuilder createMappingBuilder() {
    return get(urlEqualTo(URL_CONFIGURATIONS_TO_SMTP_SERVER));
  }

  private static Configurations createConfigurations(String user, String password, String host, String port) {
    return new Configurations().withConfigs(Lists.newArrayList(
      createSmtpConfig(EMAIL_USERNAME, user),
      createSmtpConfig(EMAIL_PASSWORD, password),
      createSmtpConfig(EMAIL_SMTP_HOST, host),
      createSmtpConfig(EMAIL_SMTP_PORT, port)
    )).withTotalRecords(6);
  }

  public static Configurations createConfigurations(String user, String password,
    String host, String port, String authMethods) {

    return new Configurations().withConfigs(Lists.newArrayList(
      createSmtpConfig(EMAIL_USERNAME, user),
      createSmtpConfig(EMAIL_PASSWORD, password),
      createSmtpConfig(EMAIL_SMTP_HOST, host),
      createSmtpConfig(EMAIL_SMTP_PORT, port),
      createSmtpConfig(AUTH_METHODS, authMethods)
    )).withTotalRecords(7);
  }

  private static Config createSmtpConfig(SmtpEmail code, String value) {
    return createConfig(CONFIG_NAME_SMTP, code.name(), value);
  }

  private static Config createConfig(String configName, String code, String value) {
    return new Config()
      .withId(UUID.randomUUID().toString())
      .withModule(MODULE_SMTP_SERVER)
      .withConfigName(configName)
      .withCode(code)
      .withValue(value);
  }

  public static List<Config> createConfigsForCustomHeaders(Map<String, String> headers) {
    return headers.entrySet().stream()
      .map(header -> createConfig(CONFIG_NAME_EMAIL_HEADERS, header.getKey(), header.getValue()))
      .toList();
  }


  public static Configurations createConfigurationsWithCustomHeaders(Map<String, String> headers) {
    Configurations configurations = getWiserMockConfigurations();
    configurations.getConfigs()
      .addAll(createConfigsForCustomHeaders(headers));

    return configurations;
  }

  public static JsonObject buildSmtpConfiguration() {

    return new JsonObject()
      .put("host", "localhost")
      .put("port", 502)
      .put("username", "username")
      .put("password", "password")
      .put("ssl", true)
      .put("trustAll", false)
      .put("loginOption", "REQUIRED")
      .put("startTlsOptions", "DISABLED")
      .put("authMethods", "CRAM-MD5 LOGIN PLAIN")
      .put("from", "noreply@folio.org")
      .put("emailHeaders", List.of(new JsonObject()
        .put("name", "Reply-To")
        .put("value", "noreply@folio.org")
      ));
  }

  public static JsonObject buildWiserSmtpConfiguration() {
    return new JsonObject()
      .put("host", "localhost")
      .put("port", 2500)
      .put("username", "user")
      .put("password", "password")
      .put("ssl", false)
      .put("trustAll", false)
      .put("loginOption", "NONE")
      .put("startTlsOptions", "OPTIONAL")
      .put("authMethods", "")
      .put("from", "")
      .put("emailHeaders", List.of());
  }

  public static JsonObject buildIncorrectWiserSmtpConfiguration() {
    return new JsonObject()
      .put("host", "localhost")
      .put("port", 555)
      .put("username", "user")
      .put("password", "password");
  }

  public static JsonObject buildInvalidSmtpConfiguration() {
    return new JsonObject()
      .put("host", "localhost")
      .put("password", "password");
  }

  public static SmtpConfiguration buildSmtpConfiguration(String user, String password,
    String host, int port, String authMethods) {

    return new SmtpConfiguration()
      .withUsername(user)
      .withPassword(password)
      .withHost(host)
      .withPort(port)
      .withAuthMethods(authMethods);
  }

}
