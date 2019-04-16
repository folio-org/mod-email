package org.folio.util;

import org.folio.rest.jaxrs.model.Configurations;
import org.folio.rest.jaxrs.resource.Email;
import org.junit.Test;

import javax.ws.rs.core.Response;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EmailUtilsTest {

  @Test
  public void testMethodCreateResponse() {
    String message = "test message";
    verifyResponse(EmailUtils.createResponse(Response.Status.OK, message), 200, message);
    verifyResponse(EmailUtils.createResponse(Response.Status.BAD_REQUEST, message), 400, message);
    verifyResponse(EmailUtils.createResponse(Response.Status.INTERNAL_SERVER_ERROR, message), 500, message);
  }

  @Test
  public void testMethodCheckMinConfigSntpServer(){
    Configurations conf = new Configurations();

    boolean isFullMinConfigForServer = EmailUtils.checkMinConfigSmtpServer(conf);
    assertTrue(isFullMinConfigForServer);
  }

  private void verifyResponse(Email.PostEmailResponse response, int expectedStatusCode, String expectedMsg) {
    assertEquals(expectedStatusCode, response.getStatus());
    assertEquals(expectedMsg, response.getEntity());
  }

}
