package org.folio.services.email.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mail.MailConfig;
import io.vertx.ext.mail.MailMessage;
import org.folio.rest.jaxrs.model.Configurations;
import org.folio.rest.jaxrs.model.EmailEntity;
import org.folio.rest.jaxrs.model.EmailEntries;
import org.folio.services.email.AbstractMailService;
import org.folio.services.email.MailService;

import java.util.List;
import java.util.stream.Collectors;

import static org.folio.util.EmailUtils.MESSAGE_RESULT;

public class MailServiceImpl extends AbstractMailService implements MailService {

  public MailServiceImpl(Vertx vertx) {
    super(vertx);
  }

  @Override
  public void sendEmail(JsonObject configJson, JsonObject emailEntityJson,
                        Handler<AsyncResult<JsonObject>> resultHandler) {
    try {
      EmailEntity emailEntity = emailEntityJson.mapTo(EmailEntity.class);
      Configurations configurations = configJson.mapTo(Configurations.class);

      MailConfig mailConfig = createMailConfig(configurations);
      MailMessage mailMessage = buildMailMessage(emailEntity, configurations);

      defineMailClient(mailConfig)
        .sendMail(mailMessage, mailHandler -> {
          if (mailHandler.failed()) {
            logger.error(String.format(ERROR_SENDING_EMAIL, mailHandler.cause().getMessage()));
            resultHandler.handle(Future.failedFuture(mailHandler.cause()));
            return;
          }

          // the logic of sending the result of sending email to `mod-notify`
          JsonObject message = createSuccessMessage(mailHandler);
          resultHandler.handle(Future.succeededFuture(message));
        });
    } catch (Exception ex) {
      logger.error(String.format(ERROR_SENDING_EMAIL, ex.getMessage()));
      resultHandler.handle(Future.failedFuture(ex.getMessage()));
    }
  }

  @Override
  public void createOrUpdateConfiguration(JsonObject configJson) {
    try {
      Configurations configurations = configJson.mapTo(Configurations.class);
      MailConfig mailConfig = createMailConfig(configurations);
      defineMailClient(mailConfig);
    } catch (Exception ex) {
      logger.error(String.format(ERROR_UPDATING_MAIL_CLIENT_CONFIG, ex.getMessage()));
    }
  }

  @Override
  public void sendBatchEmails(String tenantId, JsonObject emailEntityArray,
                              Handler<AsyncResult<JsonObject>> resultHandler) {
    try {
      List<Future> futureList = sendBatchEmailList(emailEntityArray);
      CompositeFuture.join(futureList).setHandler(asyncResult -> {
        if (asyncResult.failed()) {
          resultHandler.handle(Future.failedFuture(asyncResult.cause()));
          return;
        }
        JsonObject successMessage = new JsonObject().put(MESSAGE_RESULT, SUCCESS_SEND_BATCH_EMAILS);
        resultHandler.handle(Future.succeededFuture(successMessage));
      });
    } catch (Exception ex) {
      logger.error(String.format(ERROR_SENDING_EMAIL, ex.getMessage()));
      resultHandler.handle(Future.failedFuture(ex.getMessage()));
    }
  }

  private List<Future> sendBatchEmailList(JsonObject emailEntityArray) {
    EmailEntries emailEntries = emailEntityArray.mapTo(EmailEntries.class);
    List<MailMessage> messageList = emailEntries.getEmailEntity().stream()
      .map(this::buildMailMessage)
      .collect(Collectors.toList());

    return messageList.stream()
      .map(mailMessage -> sendMessage(client, mailMessage))
      .collect(Collectors.toList());
  }
}
