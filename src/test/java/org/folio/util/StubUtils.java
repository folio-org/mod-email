package org.folio.util;

import com.google.common.collect.Lists;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.enums.SmtpEmail;
import org.folio.rest.jaxrs.model.Config;
import org.folio.rest.jaxrs.model.Configurations;

import java.util.Random;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

public class StubUtils {

  public static String getEmailEntity(int notificationId, String to, String from, String header, String body, String message, String outputFormat) {
    JsonObject entries = new JsonObject();
    if (StringUtils.isNoneBlank(outputFormat)) {
      entries.put("outputFormat", outputFormat);
    }
    return entries
      .put("notificationId", notificationId)
      .put("to", to)
      .put("from", from)
      .put("header", header)
      .put("body", body)
      .put("message", message)
      .toString();
  }

  public static void initModConfigStub(int port, Configurations configurations) {
    stubFor(get(urlEqualTo("/configurations/entries?query=module==SMPT_SERVER"))
      .willReturn(aResponse()
        .withHeader("Content-Type", "application/json")
        .withHeader("x-okapi-token", "x-okapi-token-TEST")
        .withHeader("x-okapi-url", "http://localhost:" + port)
        .withBody(JsonObject.mapFrom(configurations).toString())));
  }

  public static Configurations initIncorrectConfigurations() {
    Configurations configurations = new Configurations();
    configurations.setTotalRecords(new Random().nextInt(5));
    return configurations;
  }

  public static Configurations getConfigurations() {
    Configurations configurations = new Configurations();
    configurations.setConfigs(Lists.newArrayList(
      createConfig(SmtpEmail.EMAIL_USERNAME, "user"),
      createConfig(SmtpEmail.EMAIL_PASSWORD, "password"),
      createConfig(SmtpEmail.EMAIL_SMTP_HOST, "smtp_host"),
      createConfig(SmtpEmail.EMAIL_SMTP_PORT, "500")
    ));
    configurations.setTotalRecords(6);
    return configurations;
  }

  private static Config createConfig(SmtpEmail smtpEmail, String value) {
    Config config = new Config();
    config.setCode(smtpEmail.name());
    config.setValue(value);
    return config;
  }
}
