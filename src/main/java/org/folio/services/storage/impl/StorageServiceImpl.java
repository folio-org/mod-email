package org.folio.services.storage.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.folio.rest.jaxrs.model.EmailEntity;
import org.folio.rest.jaxrs.model.EmailEntries;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.persist.interfaces.Results;
import org.folio.services.storage.StorageService;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;
import org.z3950.zing.cql.cql2pgjson.FieldException;

import java.util.List;
import java.util.stream.Collectors;

public class StorageServiceImpl implements StorageService {

  private static final Logger logger = LoggerFactory.getLogger(StorageServiceImpl.class);
  private static final String EMAIL_MESSAGES_TABLE_NAME = "email_messages";
  private static final String COLUMN_EXTENSION = ".jsonb";
  private static final String UPDATE_QUERY = "UPDATE %1$s SET jsonb = jsonb || '{\"status\":\"%2$s\"}'::jsonb WHERE jsonb->>'id' IN (%3$s)";
  private static final String DELETE_QUERY_BY_DATE = "DELETE FROM %1$s WHERE (jsonb->>'date')::date <= ('%2$s')::date";
  private static final String DELETE_QUERY_INTERVAL_7_DAYS = "DELETE FROM %1$s WHERE (jsonb->>'date')::date < CURRENT_DATE - INTERVAL '7' DAY";
  private static final String ESCAPE_TEMPLATE = "'%s'";
  private static final String DELIMITER_VAL = ",";

  private final Vertx vertx;

  public StorageServiceImpl(Vertx vertx) {
    this.vertx = vertx;
  }

  @Override
  public void saveEmailEntries(String tenantId, JsonObject emailEntriesJson,
                               Handler<AsyncResult<JsonObject>> asyncResultHandler) {
    try {
      EmailEntries emailEntries = emailEntriesJson.mapTo(EmailEntries.class);
      List<Object> objectList = entriesToListObjects(emailEntries);
      PostgresClient.getInstance(vertx, tenantId).saveBatch(EMAIL_MESSAGES_TABLE_NAME, objectList,
        postReply -> {
          if (postReply.failed()) {
            errorHandler(postReply.cause(), asyncResultHandler);
            return;
          }
          asyncResultHandler.handle(Future.succeededFuture());
        });
    } catch (Exception ex) {
      errorHandler(ex, asyncResultHandler);
    }
  }

  @Override
  public void findAllEmailEntries(String tenantId, int limit, int offset, String query,
                                  Handler<AsyncResult<JsonObject>> asyncResultHandler) {
    try {
      String[] fieldList = {"*"};
      CQLWrapper cql = getCQL(query, limit, offset);
      PostgresClient pgClient = PostgresClient.getInstance(vertx, tenantId);
      pgClient.get(EMAIL_MESSAGES_TABLE_NAME, EmailEntity.class, fieldList, cql, true, false,
        getReply -> {
          if (getReply.failed()) {
            errorHandler(getReply.cause(), asyncResultHandler);
            return;
          }

          Results<EmailEntity> result = getReply.result();
          Integer totalRecords = result.getResultInfo().getTotalRecords();
          EmailEntries emailEntries = new EmailEntries()
            .withEmailEntity(result.getResults())
            .withTotalRecords(totalRecords);

          JsonObject entries = JsonObject.mapFrom(emailEntries);
          asyncResultHandler.handle(Future.succeededFuture(entries));
        });
    } catch (Exception ex) {
      errorHandler(ex, asyncResultHandler);
    }
  }

  @Override
  public void updateStatusEmailEntries(String tenantId, JsonObject emailEntityArray, String status,
                                       Handler<AsyncResult<JsonObject>> asyncResultHandler) {
    try {
      EmailEntries emailEntries = emailEntityArray.mapTo(EmailEntries.class);
      String fullTableName = String.format("%s.%s", PostgresClient.convertToPsqlStandard(tenantId), EMAIL_MESSAGES_TABLE_NAME);
      String ids = getEntriesIds(emailEntries);
      String query = String.format(UPDATE_QUERY, fullTableName, status, ids);
      PostgresClient.getInstance(vertx, tenantId).execute(query, result -> {
        if (result.failed()) {
          errorHandler(result.cause(), asyncResultHandler);
          return;
        }
        asyncResultHandler.handle(Future.succeededFuture());
      });
    } catch (Exception ex) {
      errorHandler(ex, asyncResultHandler);
    }
  }

  @Override
  public void deleteEmailEntriesByExpirationDate(String tenantId, String expirationDate,
                                                 Handler<AsyncResult<JsonObject>> asyncResultHandler) {
    try {
      String fullTableName = String.format("%s.%s", PostgresClient.convertToPsqlStandard(tenantId), EMAIL_MESSAGES_TABLE_NAME);
      String query = StringUtils.isBlank(expirationDate)
        ? String.format(DELETE_QUERY_INTERVAL_7_DAYS, fullTableName)
        : String.format(DELETE_QUERY_BY_DATE, fullTableName, expirationDate);

      PostgresClient.getInstance(vertx, tenantId).execute(query, result -> {
        if (result.failed()) {
          errorHandler(result.cause(), asyncResultHandler);
          return;
        }
        asyncResultHandler.handle(Future.succeededFuture());
      });
    } catch (Exception ex) {
      errorHandler(ex, asyncResultHandler);
    }
  }

  private void errorHandler(Throwable ex, Handler<AsyncResult<JsonObject>> asyncResultHandler) {
    logger.error(ex.getMessage(), ex);
    asyncResultHandler.handle(Future.failedFuture(ex));
  }

  private String getEntriesIds(EmailEntries emailEntries) {
    return emailEntries.getEmailEntity().stream()
      .filter(emailEntity -> StringUtils.isNotBlank(emailEntity.getId()))
      .map(emailEntity -> String.format(ESCAPE_TEMPLATE, emailEntity.getId()))
      .collect(Collectors.joining(DELIMITER_VAL));
  }

  private List<Object> entriesToListObjects(EmailEntries emailEntries) {
    return emailEntries.getEmailEntity().stream()
      .map(e -> (Object) e)
      .collect(Collectors.toList());
  }

  /**
   * Build CQL from request URL query
   *
   * @param query - query from URL
   * @param limit - limit of records for pagination
   * @return - CQL wrapper for building postgres request to database
   */
  private CQLWrapper getCQL(String query, int limit, int offset) throws FieldException {
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(EMAIL_MESSAGES_TABLE_NAME + COLUMN_EXTENSION);
    return new CQLWrapper(cql2pgJson, query)
      .setLimit(new Limit(limit))
      .setOffset(new Offset(offset));
  }
}
