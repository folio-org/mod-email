package org.folio.services.storage;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.folio.services.storage.impl.StorageServiceImpl;

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
   * Save list of MailMessages
   *
   * @param emailEntriesJson the object containing the list of MailMessages
   */
  void saveEmailEntries(String tenantId, JsonObject emailEntriesJson,
                        Handler<AsyncResult<JsonObject>> resultHandler);

  /**
   * Find all MailMessages by query
   */
  void findAllEmailEntries(String tenantId, int limit, int offset, String query,
                           Handler<AsyncResult<JsonObject>> asyncResultHandler);

  /**
   * Update the status of MailMessages
   *
   * @param emailEntriesJson the object containing the list of MailMessages
   * @param status           the status of a MailMessage
   */
  void updateStatusEmailEntries(String tenantId, JsonObject emailEntriesJson, String status,
                                Handler<AsyncResult<JsonObject>> resultHandler);
}
