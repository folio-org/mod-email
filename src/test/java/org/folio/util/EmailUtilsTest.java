package org.folio.util;

import static junit.framework.TestCase.fail;
import static org.folio.util.EmailUtils.getEmailConfig;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.folio.enums.SmtpEmail;
import org.folio.rest.jaxrs.model.Config;
import org.folio.rest.jaxrs.model.Configurations;
import org.folio.services.email.MailService;
import org.junit.Test;

public class EmailUtilsTest {

  @Test
  public void testMethodCheckMinConfigSmtpServer() {
    Configurations conf = new Configurations();

    boolean isIncorrectOrEmpty = EmailUtils.isIncorrectSmtpServerConfig(conf);
    assertTrue(isIncorrectOrEmpty);
  }

  @Test
  public void testMethodGetEmailConfig() {
    String errMsg = "Incorrect SMTP server configuration value: %s";
    String value = "test";

    Configurations configurations = new Configurations()
      .withConfigs(Collections.singletonList(
        new Config()
          .withCode(SmtpEmail.EMAIL_FROM.name())
          .withValue(value)));

    try {
      getEmailConfig(configurations, SmtpEmail.EMAIL_FROM, MailService.class);
      fail();
    } catch (IllegalArgumentException ex) {
      assertEquals(String.format(errMsg, value), ex.getMessage());
    } catch (Exception ex) {
      fail();
    }
  }

}
