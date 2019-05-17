package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.enums.SendingStatus;
import org.folio.exceptions.EmptyListOfEntriesException;
import org.folio.rest.jaxrs.model.EmailEntries;
import org.folio.rest.jaxrs.resource.DelayedTask;
import org.folio.services.email.MailService;
import org.folio.services.storage.StorageService;

import javax.ws.rs.core.Response;
import java.util.Map;
import java.util.Objects;

import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.folio.util.EmailUtils.MAIL_SERVICE_ADDRESS;
import static org.folio.util.EmailUtils.STORAGE_SERVICE_ADDRESS;

public class DelayedTasksAPI implements DelayedTask {

  private static final Logger logger = LoggerFactory.getLogger(DelayedTasksAPI.class);
  private static final String SUCCESS_MESSAGE = "Success";
  private static final String STATUS_TYPE = "status=%S";
  private static final int BATCH_SIZE = 50;
  private static final int OFFSET_VAL = 0;
  private static final String STATUS_VAL = "status";
  private static final String INTERNAL_SERVER_ERROR = "Internal server error";

  private final String tenantId;
  private final MailService mailService;
  private final StorageService storageService;

  public DelayedTasksAPI(Vertx vertx, String tenantId) {
    this.tenantId = tenantId;
    this.mailService = MailService.createProxy(vertx, MAIL_SERVICE_ADDRESS);
    this.storageService = StorageService.createProxy(vertx, STORAGE_SERVICE_ADDRESS);
  }

  @Override
  public void getDelayedTaskSendBatchEmails(Map<String, String> okapiHeaders,
                                            Handler<AsyncResult<Response>> asyncResultHandler, Context context) {
    try {
      findBatchEmailEntries()
        .compose(jsonObj -> changeStatusEntries(jsonObj, SendingStatus.IN_PROCESS))
        .compose(this::sendBatchEmails)
        .compose(jsonObj -> changeStatusEntries(jsonObj, SendingStatus.findStatusByName(jsonObj.getString(STATUS_VAL))))
        .map(jsonObj -> GetDelayedTaskSendBatchEmailsResponse.respond200WithTextPlain(SUCCESS_MESSAGE))
        .map(Response.class::cast)
        .otherwise(this::mapExceptionToResponse)
        .setHandler(asyncResultHandler);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
      asyncResultHandler.handle(Future.succeededFuture(
        GetDelayedTaskSendBatchEmailsResponse.respond500WithTextPlain(ex)));
    }
  }

  @Override
  public void deleteDelayedTaskExpiredMessages(Map<String, String> okapiHeaders,
                                               Handler<AsyncResult<Response>> asyncResultHandler, Context context) {
    try {
      // not implemented
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
      asyncResultHandler.handle(Future.succeededFuture(
        DeleteDelayedTaskExpiredMessagesResponse.respond500WithTextPlain(ex)));
    }
  }

  private Future<JsonObject> findBatchEmailEntries() {
    String query = String.format(STATUS_TYPE, SendingStatus.NEW);
    Future<JsonObject> future = Future.future();
    storageService.findAllEmailEntries(tenantId, BATCH_SIZE, OFFSET_VAL, query, result -> {
      if (result.failed()) {
        future.fail(result.cause());
        return;
      }
      JsonObject emailEntriesJson = result.result();
      EmailEntries emailEntries = emailEntriesJson.mapTo(EmailEntries.class);
      if (Objects.isNull(emailEntries) || emailEntries.getEmailEntity().isEmpty()) {
        future.fail(new EmptyListOfEntriesException("List of entries is empty!"));
      } else {
        future.complete(emailEntriesJson);
      }
    });
    return future;
  }

  private Future<JsonObject> changeStatusEntries(JsonObject emailEntriesJson, SendingStatus status) {
    Future<JsonObject> future = Future.future();
    storageService.updateStatusEmailEntries(tenantId, emailEntriesJson, status.name(), result -> {
      if (result.failed()) {
        future.fail(result.cause());
        return;
      }
      future.complete(emailEntriesJson);
    });
    return future;
  }

  private Future<JsonObject> sendBatchEmails(JsonObject emailEntriesJson) {
    Future<JsonObject> future = Future.future();
    mailService.sendBatchEmails(tenantId, emailEntriesJson, result -> {
      if (result.failed()) {
        emailEntriesJson.put(STATUS_VAL, SendingStatus.FAILURE);
      } else {
        emailEntriesJson.put(STATUS_VAL, SendingStatus.DELIVERED);
      }
      future.complete(emailEntriesJson);
    });
    return future;
  }

  private Response mapExceptionToResponse(Throwable t) {
    if (t.getClass() == EmptyListOfEntriesException.class) {
      return Response.status(200)
        .header(CONTENT_TYPE, TEXT_PLAIN)
        .entity(t.getMessage())
        .build();
    }
    logger.error(t.getMessage(), t);
    return Response.status(500)
      .header(CONTENT_TYPE, TEXT_PLAIN)
      .entity(INTERNAL_SERVER_ERROR)
      .build();
  }
}
