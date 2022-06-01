package org.folio.services.storage.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.util.EmailUtils.EMAIL_STATISTICS_TABLE_NAME;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.EmailEntity;
import org.folio.rest.jaxrs.model.EmailEntries;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.persist.interfaces.Results;
import org.folio.services.storage.StorageService;
import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.cql2pgjson.exception.FieldException;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class StorageServiceImpl implements StorageService {

  private static final Logger logger = LogManager.getLogger(StorageServiceImpl.class);

  private static final String DELETE_QUERY_BY_DATE = "DELETE FROM %1$s WHERE (jsonb->>'date')::date <= ('%2$s')::date AND jsonb->>'status' = '%3$s'";
  private static final String DELETE_QUERY_INTERVAL_ONE_DAY = "DELETE FROM %1$s WHERE (jsonb->>'date')::date < CURRENT_DATE - INTERVAL '1' DAY AND jsonb->>'status' = '%2$s'";
  private static final String COLUMN_EXTENSION = ".jsonb";

  private final Vertx vertx;

  public StorageServiceImpl(Vertx vertx) {
    this.vertx = vertx;
  }

  @Override
  public void saveEmailEntity(String tenantId, JsonObject emailJson,
    Handler<AsyncResult<JsonObject>> resultHandler) {

    try {
      EmailEntity emailEntity = emailJson.mapTo(EmailEntity.class);
      PostgresClient.getInstance(vertx, tenantId)
        .save(EMAIL_STATISTICS_TABLE_NAME, emailEntity.getId(), emailEntity, true, true)
        .onFailure(logger::error)
        .map(emailJson)
        .onComplete(resultHandler);
    } catch (Exception ex) {
      errorHandler(ex, resultHandler);
    }
  }

  @Override
  public void findEmailEntries(String tenantId, int limit, int offset, String query,
                               Handler<AsyncResult<JsonObject>> resultHandler) {
    try {
      String[] fieldList = {"*"};
      CQLWrapper cql = getCQL(query, limit, offset);
      PostgresClient pgClient = PostgresClient.getInstance(vertx, tenantId);
      pgClient.get(EMAIL_STATISTICS_TABLE_NAME, EmailEntity.class, fieldList, cql, true, false,
        getReply -> {
          if (getReply.failed()) {
            errorHandler(getReply.cause(), resultHandler);
            return;
          }

          Results<EmailEntity> result = getReply.result();
          Integer totalRecords = result.getResultInfo().getTotalRecords();
          EmailEntries emailEntries = new EmailEntries()
            .withEmailEntity(result.getResults())
            .withTotalRecords(totalRecords);

          JsonObject entries = JsonObject.mapFrom(emailEntries);
          resultHandler.handle(succeededFuture(entries));
        });
    } catch (Exception ex) {
      errorHandler(ex, resultHandler);
    }
  }

  @Override
  public void deleteEmailEntriesByExpirationDateAndStatus(String tenantId, String expirationDate, String status,
                                                          Handler<AsyncResult<JsonObject>> resultHandler) {
    try {
      String fullTableName = String.format("%s.%s", PostgresClient.convertToPsqlStandard(tenantId), EMAIL_STATISTICS_TABLE_NAME);
      String query = StringUtils.isBlank(expirationDate)
        ? String.format(DELETE_QUERY_INTERVAL_ONE_DAY, fullTableName, status)
        : String.format(DELETE_QUERY_BY_DATE, fullTableName, expirationDate, status);

      PostgresClient.getInstance(vertx, tenantId).execute(query, result -> {
        if (result.failed()) {
          errorHandler(result.cause(), resultHandler);
          return;
        }
        resultHandler.handle(succeededFuture());
      });
    } catch (Exception ex) {
      errorHandler(ex, resultHandler);
    }
  }

  private void errorHandler(Throwable ex, Handler<AsyncResult<JsonObject>> asyncResultHandler) {
    logger.error(ex.getMessage(), ex);
    asyncResultHandler.handle(Future.failedFuture(ex));
  }

  /**
   * Build CQL from request URL query
   *
   * @param query - query from URL
   * @param limit - limit of records for pagination
   * @return - CQL wrapper for building postgres request to database
   */
  private CQLWrapper getCQL(String query, int limit, int offset) throws FieldException {
    CQL2PgJSON cql2pgJson = new CQL2PgJSON(EMAIL_STATISTICS_TABLE_NAME + COLUMN_EXTENSION);
    return new CQLWrapper(cql2pgJson, query)
      .setLimit(new Limit(limit))
      .setOffset(new Offset(offset));
  }
}
