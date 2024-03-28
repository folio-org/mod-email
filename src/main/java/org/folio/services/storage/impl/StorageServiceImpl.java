package org.folio.services.storage.impl;

import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.persist.PostgresClient.convertToPsqlStandard;
import static org.folio.util.EmailUtils.EMAIL_STATISTICS_TABLE_NAME;
import static org.folio.util.LogUtil.asJson;

import io.vertx.sqlclient.Tuple;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.rest.jaxrs.model.EmailEntity;
import org.folio.rest.jaxrs.model.EmailEntries;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.persist.interfaces.Results;
import org.folio.services.storage.StorageService;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.folio.rest.jaxrs.model.SmtpConfiguration;

public class StorageServiceImpl implements StorageService {

  private static final Logger logger = LogManager.getLogger(StorageServiceImpl.class);

  private static final String DELETE_QUERY_BY_DATE = "DELETE FROM %s WHERE (jsonb->>'date')::date <= $1::text::date AND jsonb->>'status' = $2";
  private static final String DELETE_QUERY_INTERVAL_BY_HOURS = "DELETE FROM %s WHERE (jsonb->>'date')::timestamp < CURRENT_TIMESTAMP - make_interval(hours => $1) AND jsonb->>'status' = $2";
  private static final String COLUMN_EXTENSION = ".jsonb";
  private static final int DEFAULT_EXPIRATION_HOURS = 24;

  private final Vertx vertx;

  public StorageServiceImpl(Vertx vertx) {
    this.vertx = vertx;
  }

  @Override
  public void saveEmailEntity(String tenantId, JsonObject emailJson,
    Handler<AsyncResult<JsonObject>> resultHandler) {

    logger.debug("saveEmailEntity:: parameters tenantId: {}, emailJson: {}",
      () -> tenantId, () -> asJson(emailJson));
    try {
      EmailEntity emailEntity = emailJson.mapTo(EmailEntity.class);
      logger.debug("saveEmailEntity:: parameters emailEntity: {}", () -> asJson(emailEntity));
      String emailId = emailEntity.getId();
      PostgresClient.getInstance(vertx, tenantId)
        .save(EMAIL_STATISTICS_TABLE_NAME, emailId, emailEntity, true, true)
        .onSuccess(id -> logger.info("Email {} saved", emailId))
        .onFailure(t -> logger.error("Failed to save email {}: {}", emailId, t.getMessage()))
        .map(emailJson)
        .onComplete(resultHandler);
    } catch (Exception ex) {
      logger.warn("saveEmailEntity:: Failed to save email", ex);
      errorHandler(ex, resultHandler);
    }
  }

  @Override
  public void findEmailEntries(String tenantId, int limit, int offset, String query,
    Handler<AsyncResult<JsonObject>> resultHandler) {

    logger.debug("findEmailEntries:: parameters tenantId: {}, limit: {}, offset: {}, query: {}",
      tenantId, limit, offset, query);
    try {
      String[] fieldList = {"*"};
      CQLWrapper cql = getCQL(query, limit, offset);
      PostgresClient pgClient = PostgresClient.getInstance(vertx, tenantId);
      pgClient.get(EMAIL_STATISTICS_TABLE_NAME, EmailEntity.class, fieldList, cql, true, false,
        getReply -> {
          if (getReply.failed()) {
            logger.warn("findEmailEntries:: Failed to get email entries: ", getReply.cause());
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
      logger.warn("findEmailEntries:: Failed to get email entries", ex);
      errorHandler(ex, resultHandler);
    }
  }

  @Override
  public void deleteEmailEntriesByExpirationDateAndStatus(String tenantId, String expirationDate, String status,
                                                          Handler<AsyncResult<JsonObject>> resultHandler) {
    logger.debug("deleteEmailEntriesByExpirationDateAndStatus:: parameters expirationDate: {}, status: {}", expirationDate, status);
    try {
      getExpirationHoursFromConfig(tenantId)
        .compose(expirationHours -> {
          String fullTableName = getFullTableName(EMAIL_STATISTICS_TABLE_NAME, tenantId);
          var pgClient = PostgresClient.getInstance(vertx, tenantId);
          if (StringUtils.isBlank(expirationDate)) {
            logger.info("deleteEmailEntriesByExpirationDateAndStatus:: parameters expirationHours: {}, status: {}",
              expirationHours, status);
            return pgClient.execute(String.format(DELETE_QUERY_INTERVAL_BY_HOURS, fullTableName),
              Tuple.of(expirationHours, status));
          } else {
            logger.info("deleteEmailEntriesByExpirationDateAndStatus:: parameters expirationDate: {}, status: {}",
              expirationDate, status);
            return pgClient.execute(String.format(DELETE_QUERY_BY_DATE, fullTableName),
              Tuple.of(expirationDate, status));
          }
        })
        .onSuccess(result -> {
          logger.info("deleteEmailEntriesByExpirationDateAndStatus:: deleted {} entries", result.rowCount());
          resultHandler.handle(succeededFuture());
        })
        .onFailure(err -> {
          logger.warn("deleteEmailEntriesByExpirationDateAndStatus:: Error while deleting entries", err);
          errorHandler(err, resultHandler);
        });
    } catch (Exception ex) {
      logger.warn("deleteEmailEntriesByExpirationDateAndStatus:: Failed to delete email entries", ex);
      errorHandler(ex, resultHandler);
    }
  }

  private Future<Integer> getExpirationHoursFromConfig(String tenantId) {
    return PostgresClient.getInstance(vertx, tenantId)
      .get("smtp_configuration", SmtpConfiguration.class, new Criterion())
      .map(this::getExpirationHoursFromResult)
      .recover(e -> {
        logger.warn("getExpirationHoursFromConfig:: Failed to get expirationHours from smtp_configuration", e);
        return Future.succeededFuture(DEFAULT_EXPIRATION_HOURS);
      });
  }

  private Integer getExpirationHoursFromResult(Results<SmtpConfiguration> results) {
    return results != null && results.getResults() != null
      && !results.getResults().isEmpty()
      && results.getResults().get(0).getExpirationHours() != null
      ? results.getResults().get(0).getExpirationHours() : DEFAULT_EXPIRATION_HOURS;
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

  private static String getFullTableName(String tableName, String tenantId) {
    return convertToPsqlStandard(tenantId) + "." + tableName;
  }
}
