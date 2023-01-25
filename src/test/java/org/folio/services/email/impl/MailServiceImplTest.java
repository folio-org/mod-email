package org.folio.services.email.impl;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class MailServiceImplTest {

  @Test
  public void sendEmailShouldFail() {
    Promise<JsonObject> promise = Promise.promise();
    new MailServiceImpl(Vertx.vertx()).sendEmail(new JsonObject(), null, promise);
    assertTrue(promise.future().failed());
  }
}
