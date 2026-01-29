package org.folio.services.email;

import org.folio.services.email.impl.MailServiceImpl;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.Future;
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
    return new MailServiceVertxEBProxy(vertx, address);
  }

  /**
   * send a single mail via MailClient
   *
   * @param tenantId        the tenant identifier (from request headers)
   * @param configJson      represents the configuration of a mail service with mail server hostname,
   *                        port, security options, login options and login/password
   * @param emailEntityJson MailMessage object containing the mail text, from/to, attachments etc
   */
  Future<JsonObject> sendEmail(String tenantId, JsonObject configJson, JsonObject emailEntityJson);
}
