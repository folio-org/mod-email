package org.folio.services.email.impl;

import static io.vertx.core.json.JsonObject.mapFrom;
import static org.folio.util.StubUtils.buildSmtpConfiguration;
import static org.folio.util.StubUtils.createConfigurations;
import static org.folio.util.StubUtils.initModConfigStub;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.RandomStringUtils;
import org.folio.rest.jaxrs.model.Configurations;
import org.folio.rest.jaxrs.model.EmailEntity;
import org.folio.rest.jaxrs.model.SmtpConfiguration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class MailServiceImplTest {

  private static final String ADDRESS_TEMPLATE = "%s@localhost";
  private static final String AUTH_METHODS = "CRAM-MD5 LOGIN PLAIN";

  @Rule
  public WireMockRule mockServer = new WireMockRule(
    WireMockConfiguration.wireMockConfig()
      .dynamicPort()
      .notifier(new ConsoleNotifier(true)));

  @Test
  public void messageShouldIncludeAuthMethodsFromConfiguration(TestContext context) {
    Configurations configurations = createConfigurations("user", "pws", "localhost",
      "2500", AUTH_METHODS);
    initModConfigStub(mockServer.port(), configurations);

    SmtpConfiguration smtpConfiguration = buildSmtpConfiguration("user", "pws", "localhost",
      2500, AUTH_METHODS);

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

    String tenantId = "test_tenant";

    var mailServiceImpl = new MailServiceImpl(Vertx.vertx());
    mailServiceImpl.sendEmail(tenantId, mapFrom(smtpConfiguration), mapFrom(emailEntity),
      context.asyncAssertFailure(x -> {
        assertThat(mailServiceImpl.getMailConfig(tenantId).getAuthMethods(), is(AUTH_METHODS));
      }));
  }
}
