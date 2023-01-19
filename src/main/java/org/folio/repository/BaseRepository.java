package org.folio.repository;

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
    CQLWrapper cql = new CQLWrapper(cql2pgJson, query, limit, offset);
    return pgClient.get(tableName, entityType, cql, true)
      .map(Results::getResults);
  }

  public Future<List<T>> get(Criterion criterion) {
    return pgClient.get(tableName, entityType, criterion, true)
      .map(Results::getResults);
  }

  public Future<T> get(String id) {
    return pgClient.getById(tableName, id, entityType);
  }

  public Future<List<T>> getAllWithDefaultLimit() {
    return getAllWithLimit(DEFAULT_LIMIT);
  }

  public Future<List<T>> getAllWithLimit(int limit) {
    return get(null, 0, limit);
  }

  public Future<String> save(T entity, String id) {
    return pgClient.save(tableName, id, entity);
  }

  public Future<String> upsert(T entity, String id) {
    return pgClient.upsert(tableName, id, entity);
  }

  public Future<Boolean> update(T entity, String id) {
    return pgClient.update(tableName, entity, id)
      .map(updateResult -> updateResult.rowCount() == 1);
  }

  public Future<Boolean> delete(String id) {
    return pgClient.delete(tableName, id)
      .map(updateResult -> updateResult.rowCount() == 1);
  }

  public Future<Boolean> delete(Criterion criterion) {
    return pgClient.delete(tableName, criterion)
      .map(RowSet::rowCount)
      .onSuccess(rowCount -> log.info("Deleted {} record(s) from table {} using query: {}",
        rowCount, tableName, criterion))
      .map(rowCount -> rowCount > 0);
  }

  public Future<Void> removeAll(String tenantId) {
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

