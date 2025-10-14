package org.folio.services.email;

import io.vertx.core.eventbus.DeliveryOptions;
import org.folio.services.email.impl.MailServiceImpl;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * The service provides the ability to send email using the SMTP server
 */
@ProxyGen
public interface MailService {

  static MailService create(Vertx vertx) {
    return new MailServiceImpl(vertx);
  }

  /**
   * Creates proxy instance that helps to push message into the message queue
   *
   * @param vertx   vertx instance
   * @param address host address
   * @return ValidationEngineService instance
   */
  static MailService createProxy(Vertx vertx, String address) {
    var sentTimeout = new DeliveryOptions().setSendTimeout(5000);
    return new MailServiceVertxEBProxy(vertx, address, sentTimeout);
  }

  /**
   * send a single mail via MailClient
   *
   * @param configJson      represents the configuration of a mail service with mail server hostname,
   *                        port, security options, login options and login/password
   * @param emailEntityJson MailMessage object containing the mail text, from/to, attachments etc
   */
  void sendEmail(JsonObject configJson, JsonObject emailEntityJson, Handler<AsyncResult<JsonObject>> resultHandler);
}
