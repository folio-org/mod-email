package org.folio.services.storage.impl;

import static org.junit.Assert.assertTrue;

import org.folio.services.storage.StorageService;
import org.junit.Test;

import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class StorageServiceImplTest {
  private StorageService storageService = new StorageServiceImpl(Vertx.vertx());

  @Test
  public void saveEmailEntityShouldFail() {
    Promise<JsonObject> promise = Promise.promise();
    storageService.saveEmailEntity(null, null, promise);
    assertTrue(promise.future().failed());
  }

  @Test
  public void findEmailEntriesShouldFail() {
    Promise<JsonObject> promise = Promise.promise();
    storageService.findEmailEntries(null, 0, 0, null, promise);
    assertTrue(promise.future().failed());
  }

  @Test
  public void deleteEmailEntriesByExpirationDateAndStatusEmailEntriesShouldFail() {
    Promise<JsonObject> promise = Promise.promise();
    new StorageServiceImpl(Vertx.vertx()).deleteEmailEntriesByExpirationDateAndStatus(
      "public", null, null, promise);
    assertTrue(promise.future().failed());
  }

  @Test
  public void deleteEmailEntriesShouldFailWithInvalidTenant() {
    Promise<JsonObject> promise = Promise.promise();
    // setting the tenantId as public to simulate the error
    new StorageServiceImpl(Vertx.vertx()).deleteEmailEntriesByExpirationDateAndStatus(
      "public", null, null, promise);
    assertTrue(promise.future().failed());
  }
}
