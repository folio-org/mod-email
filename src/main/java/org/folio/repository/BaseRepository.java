package org.folio.repository;

import static org.folio.util.LogUtil.asJson;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.cql2pgjson.CQL2PgJSON;
import org.folio.cql2pgjson.exception.FieldException;
import org.folio.exceptions.FailedToCreateRepositoryException;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.persist.interfaces.Results;
import org.folio.rest.tools.utils.ModuleName;

import io.vertx.core.Future;
import io.vertx.sqlclient.RowSet;

public class BaseRepository<T> {

  private static final Logger log = LogManager.getLogger(BaseRepository.class);
  private static final String OPERATION_EQUALS = "=";
  private static final int DEFAULT_LIMIT = 100;

  protected final PostgresClient pgClient;
  protected final String tableName;
  private final Class<T> entityType;
  private final CQL2PgJSON cql2pgJson;

  public BaseRepository(PostgresClient pgClient, String tableName, Class<T> entityType) {
    this.pgClient = pgClient;
    this.tableName = tableName;
    this.entityType = entityType;
    try {
      this.cql2pgJson = new CQL2PgJSON(tableName + ".jsonb");
    } catch (FieldException e) {
      throw new FailedToCreateRepositoryException(e);
    }
  }

  public Future<List<T>> get(String query, int offset, int limit) {
    log.debug("get:: parameters query: {}, offset: {}, limit: {}", query, offset, limit);
    CQLWrapper cql = new CQLWrapper(cql2pgJson, query, limit, offset);
    return pgClient.get(tableName, entityType, cql, true)
      .map(Results::getResults)
      .onSuccess(result -> log.debug("get:: result: {}", () -> asJson(result)));
  }

  public Future<List<T>> get(Criterion criterion) {
    log.debug("get:: parameters criterion: {}", criterion);
    return pgClient.get(tableName, entityType, criterion, true)
      .map(Results::getResults)
      .onSuccess(result -> log.debug("get:: result: {}", () -> asJson(result)));
  }

  public Future<T> get(String id) {
    log.debug("get:: parameters id: {}", id);
    return pgClient.getById(tableName, id, entityType)
      .onSuccess(result -> log.debug("get:: result: {}", () -> asJson(result)));
  }

  public Future<List<T>> getAllWithLimit(int limit) {
    log.debug("getAllWithLimit:: parameters limit: {}", limit);
    return get(null, 0, limit);
  }

  public Future<String> save(T entity, String id) {
    log.debug("save:: parameters entity: {}, id: {}", () -> asJson(entity), () -> id);
    return pgClient.save(tableName, id, entity)
      .onSuccess(result -> log.debug("save:: result: {}", result));
  }

  public Future<Boolean> delete(String id) {
    log.debug("delete:: parameters id: {}", id);
    return pgClient.delete(tableName, id)
      .map(updateResult -> updateResult.rowCount() == 1)
      .onSuccess(result -> log.debug("delete:: result: {}", result));
  }

  public Future<Boolean> delete(Criterion criterion) {
    log.debug("delete:: parameters criterion: {}", criterion);
    return pgClient.delete(tableName, criterion)
      .map(RowSet::rowCount)
      .onSuccess(rowCount -> log.debug("Deleted {} record(s) from table {} using query: {}",
        rowCount, tableName, criterion))
      .map(rowCount -> rowCount > 0)
      .onSuccess(result -> log.debug("delete:: result: {}", result));
  }

  public Future<Void> removeAll(String tenantId) {
    log.debug("removeAll:: parameters tenantId: {}", tenantId);
    String deleteAllQuery = String.format("DELETE FROM %s_%s.%s", tenantId,
      ModuleName.getModuleName(), tableName);
    return pgClient.execute(deleteAllQuery).mapEmpty();
  }

  static Criterion buildCriterion(String key, String value) {
    return new Criterion(new Criteria()
      .addField(key)
      .setOperation(OPERATION_EQUALS)
      .setVal(value)
      .setJSONB(true));
  }

}

