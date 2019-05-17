package org.folio.services.email;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.folio.services.email.impl.MailServiceImpl;

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
   * @param configJson      represents the configuration of a mail service with mail server hostname,
   *                        port, security options, login options and login/password
   * @param emailEntityJson MailMessage object containing the mail text, from/to, attachments etc
   */
  void sendEmail(JsonObject configJson, JsonObject emailEntityJson, Handler<AsyncResult<JsonObject>> resultHandler);

  /**
   * Send a batch emails via MailClient
   *
   * @param emailEntityArray object containing the list of emailEntity
   */
  void sendBatchEmails(String tenantId, JsonObject emailEntityArray, Handler<AsyncResult<JsonObject>> resultHandler);

  /**
   * Create or update MailClient configurations
   *
   * @param configJson represents the configuration of a mail service with mail server hostname,
   *                   port, security options, login options and login/password
   */
  void createOrUpdateConfiguration(JsonObject configJson);
}
