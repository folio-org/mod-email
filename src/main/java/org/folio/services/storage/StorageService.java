package org.folio.services.storage;

import org.folio.services.storage.impl.StorageServiceImpl;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

/**
 * The interface provides basic CRUD operations for storing and retrieving data from the storage
 */
@ProxyGen
public interface StorageService {

  static StorageService create(Vertx vertx) {
    return new StorageServiceImpl(vertx);
  }

  /**
   * Creates proxy instance that helps to push message into the message queue
   *
   * @param vertx   vertx instance
   * @param address host address
   * @return StorageService instance
   */
  static StorageService createProxy(Vertx vertx, String address) {
    return new StorageServiceVertxEBProxy(vertx, address);
  }

  /**
   * Persists an emailEntityJson object to the database for metrics
   *
   * @param emailEntityJson the object contains an {@link org.folio.rest.jaxrs.model.EmailEntity}
   *                        entity representation in a JSON format
   */
  void saveEmailEntity(String tenantId, JsonObject emailEntityJson,
                       Handler<AsyncResult<JsonObject>> resultHandler);

  /**
   * Updates an emailEntityJson object to the database
   *
   * @param emailEntityJson the object contains an {@link org.folio.rest.jaxrs.model.EmailEntity}
   *                        entity representation in a JSON format
   */
  void updateEmailEntity(String tenantId, JsonObject emailEntityJson,
                         Handler<AsyncResult<JsonObject>> resultHandler);

  /**
   * Find all EmailEntries by query
   * The query parameter may contain email status, expiration date or other parameters
   */
  void findEmailEntries(String tenantId, int limit, int offset, String query,
                        Handler<AsyncResult<JsonObject>> resultHandler);

  /**
   * Delete EmailEntries by expiration date and email status
   *
   * @param expirationDate the expiration date of email in format: `yyyy-MM-dd`
   * @param emailStatus    the status of email {@link org.folio.rest.jaxrs.model.EmailEntity.Status}
   */
  void deleteEmailEntriesByExpirationDateAndStatus(String tenantId, String expirationDate, String emailStatus,
                                                   Handler<AsyncResult<JsonObject>> resultHandler);
}
