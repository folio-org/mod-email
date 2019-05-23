package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.folio.exceptions.EmptyListOfEntriesException;
import org.folio.rest.jaxrs.model.EmailEntity;
import org.folio.rest.jaxrs.model.EmailEntries;
import org.folio.rest.jaxrs.resource.DelayedTask;
import org.folio.services.email.MailService;
import org.folio.services.storage.StorageService;
import org.folio.util.EmailUtils;

import javax.ws.rs.core.Response;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.folio.util.EmailUtils.MAIL_SERVICE_ADDRESS;
import static org.folio.util.EmailUtils.STORAGE_SERVICE_ADDRESS;

public class DelayedTasksAPI implements DelayedTask {

  private static final Logger logger = LoggerFactory.getLogger(DelayedTasksAPI.class);
  private static final String SUCCESS_MESSAGE = "Success";
  private static final String ERROR_MESSAGE = "Invalid date value, the parameter must be in the format: yyyy-MM-dd";
  private static final String STATUS_TYPE = "status=%S";
  private static final int BATCH_SIZE = 50;
  private static final int OFFSET_VAL = 0;
  private static final String STATUS_VAL = "status";
  private static final String INTERNAL_SERVER_ERROR = "Internal server error";
  private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

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
        .compose(jsonObj -> changeStatusEntries(jsonObj, EmailEntity.Status.PROCESSING))
        .compose(this::sendBatchEmails)
        .compose(jsonObj -> changeStatusEntries(jsonObj, EmailUtils.findStatusByName(jsonObj.getString(STATUS_VAL))))
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
  public void deleteDelayedTaskExpiredMessages(String expirationDate, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    try {
      checkExpirationDate(expirationDate)
        .compose(v -> deleteMessagesByExpirationDate(expirationDate))
        .map(v -> DeleteDelayedTaskExpiredMessagesResponse.respond200WithTextPlain(SUCCESS_MESSAGE))
        .map(Response.class::cast)
        .otherwise(this::mapExceptionToResponse)
        .setHandler(asyncResultHandler);
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
      asyncResultHandler.handle(Future.succeededFuture(
        DeleteDelayedTaskExpiredMessagesResponse.respond500WithTextPlain(ex)));
    }
  }

  private Future<Void> checkExpirationDate(String expirationDate) {
    Future<Void> future = Future.future();
    if (StringUtils.isBlank(expirationDate) || isCorrectDateFormat(expirationDate)) {
      future.complete();
    } else {
      future.fail(new IllegalArgumentException(ERROR_MESSAGE));
    }
    return future;
  }

  private boolean isCorrectDateFormat(String expirationDate) {
    return DATE_PATTERN.matcher(expirationDate).matches();
  }

  private Future<Void> deleteMessagesByExpirationDate(String expirationDate) {
    Future<Void> future = Future.future();
    storageService.deleteEmailEntriesByExpirationDate(tenantId, expirationDate, result -> {
      if (result.failed()) {
        future.fail(result.cause());
        return;
      }
      future.complete();
    });
    return future;
  }

  private Future<JsonObject> findBatchEmailEntries() {
    String query = String.format(STATUS_TYPE, EmailEntity.Status.NEW);
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

  private Future<JsonObject> changeStatusEntries(JsonObject emailEntriesJson, EmailEntity.Status status) {
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
        emailEntriesJson.put(STATUS_VAL, EmailEntity.Status.FAILURE);
      } else {
        emailEntriesJson.put(STATUS_VAL, EmailEntity.Status.DELIVERED);
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

    if (t.getClass() == IllegalArgumentException.class) {
      return Response.status(400)
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
