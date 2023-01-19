package org.folio.services.storage.impl;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class StorageServiceImplTest {

  @Test
  public void saveEmailEntityShouldFail() {
    Promise<JsonObject> promise = Promise.promise();
    new StorageServiceImpl(Vertx.vertx()).saveEmailEntity(null, null, promise);
    assertTrue(promise.future().failed());
  }

  @Test
  public void findEmailEntriesShouldFail() {
    Promise<JsonObject> promise = Promise.promise();
    new StorageServiceImpl(Vertx.vertx()).findEmailEntries(null, 0, 0, null, promise);
    assertTrue(promise.future().failed());
  }

  @Test
  public void deleteEmailEntriesByExpirationDateAndStatusEmailEntriesShouldFail() {
    Promise<JsonObject> promise = Promise.promise();
    new StorageServiceImpl(Vertx.vertx()).deleteEmailEntriesByExpirationDateAndStatus(
      null, null, null, promise);
    assertTrue(promise.future().failed());
  }
}
