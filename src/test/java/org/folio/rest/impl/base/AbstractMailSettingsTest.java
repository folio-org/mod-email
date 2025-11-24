package org.folio.rest.impl.base;

import static org.junit.Assert.assertEquals;

import org.folio.exceptions.EmailSettingsException;
import org.folio.rest.jaxrs.model.Error;
import org.junit.Test;

public class AbstractMailSettingsTest {

  @Test
  public void handleServiceException_positive_emailSettingsException() {
    var mailSettingsException = new EmailSettingsException(new Error(), 400);
    var service = new AbstractMailSettings() {};
    var response = service.handleServiceError(mailSettingsException);
    assertEquals(400, response.getStatus());
  }

  @Test
  public void handleServiceException_positive_genericException() {
    var runtimeException = new RuntimeException("error");
    var service = new AbstractMailSettings() {};
    var response = service.handleServiceError(runtimeException);
    assertEquals(500, response.getStatus());

    var error = new Error()
      .withCode("service_error")
      .withType("RuntimeException")
      .withMessage("Unexpected error occurred: error");

    assertEquals(error, response.getEntity());
  }
}
