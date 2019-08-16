package org.folio.util;

import static junit.framework.TestCase.fail;
import static org.folio.util.EmailUtils.getEmailConfig;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import javax.ws.rs.core.Response.Status;

import org.folio.enums.SmtpEmail;
import org.folio.rest.jaxrs.model.Config;
import org.folio.rest.jaxrs.model.Configurations;
import org.folio.rest.jaxrs.resource.Email;
import org.folio.services.email.MailService;
import org.junit.Test;

public class EmailUtilsTest {

  @Test
  public void testMethodCreateResponse() {
    String message = "test message";
    verifyResponse(EmailUtils.createResponse(Status.OK, message), 200, message);
    verifyResponse(EmailUtils.createResponse(Status.BAD_REQUEST, message), 400, message);
    verifyResponse(EmailUtils.createResponse(Status.INTERNAL_SERVER_ERROR, message), 500, Status.INTERNAL_SERVER_ERROR.getReasonPhrase());
  }

  @Test
  public void testMethodCheckMinConfigSmtpServer() {
    Configurations conf = new Configurations();

    boolean isIncorrectOrEmpty = EmailUtils.isIncorrectSmtpServerConfig(conf);
    assertTrue(isIncorrectOrEmpty);
  }

  private void verifyResponse(Email.PostEmailResponse response, int expectedStatusCode, String expectedMsg) {
    assertEquals(expectedStatusCode, response.getStatus());
    assertEquals(expectedMsg, response.getEntity());
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
