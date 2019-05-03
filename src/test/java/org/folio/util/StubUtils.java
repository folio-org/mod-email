package org.folio.util;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.google.common.collect.Lists;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.enums.SmtpEmail;
import org.folio.rest.jaxrs.model.Config;
import org.folio.rest.jaxrs.model.Configurations;

import java.util.Random;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

public class StubUtils {

  private static final String URL_CONFIGURATIONS_TO_SMPT_SERVER = "/configurations/entries?query=module==SMPT_SERVER";

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
        .withBody(JsonObject.mapFrom(configurations).toString())));
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

  public static Configurations getConfigurations() {
    return createConfigurations("user", "password", "smtp_host", "500");
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
    return get(urlEqualTo(URL_CONFIGURATIONS_TO_SMPT_SERVER));
  }

  private static Configurations createConfigurations(String user, String password, String host, String port) {
    return new Configurations().withConfigs(Lists.newArrayList(
      createConfig(SmtpEmail.EMAIL_USERNAME, user),
      createConfig(SmtpEmail.EMAIL_PASSWORD, password),
      createConfig(SmtpEmail.EMAIL_SMTP_HOST, host),
      createConfig(SmtpEmail.EMAIL_SMTP_PORT, port)
    )).withTotalRecords(6);
  }

  private static Config createConfig(SmtpEmail smtpEmail, String value) {
    Config config = new Config();
    config.setCode(smtpEmail.name());
    config.setValue(value);
    return config;
  }
}
