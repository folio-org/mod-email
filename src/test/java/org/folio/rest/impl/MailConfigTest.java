package org.folio.rest.impl;

import static io.vertx.core.json.JsonObject.mapFrom;
import static org.folio.util.StubUtils.createConfigurations;
import static org.folio.util.StubUtils.createSmtpConfiguration;
import static org.folio.util.StubUtils.initModConfigStub;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.verifyNew;
import static org.powermock.api.mockito.PowerMockito.whenNew;

import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.RandomStringUtils;
import org.folio.rest.jaxrs.model.Configurations;
import org.folio.rest.jaxrs.model.EmailEntity;
import org.folio.rest.jaxrs.model.SmtpConfiguration;
import org.folio.services.email.impl.MailServiceImpl;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mail.MailConfig;

@RunWith(PowerMockRunner.class)
@PrepareForTest(MailServiceImpl.class)
@PowerMockIgnore({"javax.net.ssl.*", "javax.management.*"})
public class MailConfigTest {

  private static final String ADDRESS_TEMPLATE = "%s@localhost";
  private static final String AUTH_METHODS = "CRAM-MD5 LOGIN PLAIN";

  @Rule
  public WireMockRule mockServer = new WireMockRule(
    WireMockConfiguration.wireMockConfig()
      .dynamicPort()
      .notifier(new ConsoleNotifier(true)));

  @Test
  public void messageShouldIncludeAuthMethodsFromConfiguration() throws Exception {
    Configurations configurations = createConfigurations("user", "pws", "localhost",
      "2500", AUTH_METHODS);
    initModConfigStub(mockServer.port(), configurations);

    SmtpConfiguration smtpConfiguration = createSmtpConfiguration("user", "pws", "localhost", 2500,
      AUTH_METHODS);

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

    MailConfig mailConfigMock = PowerMockito.mock(MailConfig.class);
    whenNew(MailConfig.class).withNoArguments().thenReturn(mailConfigMock);

    new MailServiceImpl(Vertx.vertx()).sendEmail(mapFrom(smtpConfiguration),
      mapFrom(emailEntity), asyncResult -> {});

    verifyNew(MailConfig.class, Mockito.times(1))
      .withNoArguments();

    verify(mailConfigMock, Mockito.times(1))
        .setAuthMethods(AUTH_METHODS);
  }
}
