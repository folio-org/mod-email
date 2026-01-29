package org.folio.services.storage.impl;

import static org.junit.Assert.assertTrue;

import org.folio.services.storage.StorageService;
import org.junit.Test;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class StorageServiceImplTest {
  private StorageService storageService = new StorageServiceImpl(Vertx.vertx());

  @Test
  public void saveEmailEntityShouldFail() {
    var future = storageService.saveEmailEntity(null, null);
    assertTrue(future.failed());
  }

  @Test
  public void findEmailEntriesShouldFail() {
    var future = storageService.findEmailEntries(null, 0, 0, null);
    assertTrue(future.failed());
  }

  @Test
  public void deleteEmailEntriesByExpirationDateAndStatusEmailEntriesShouldFail() {
    var future = new StorageServiceImpl(Vertx.vertx()).deleteEmailEntriesByExpirationDateAndStatus(
      null, null, null);
    assertTrue(future.failed());
  }

  @Test
  public void deleteEmailEntriesShouldFailWithInvalidTenant() {
    // setting the tenantId as public to simulate the error
    var future = new StorageServiceImpl(Vertx.vertx()).deleteEmailEntriesByExpirationDateAndStatus(
      "public", null, null);
    assertTrue(future.failed());
  }
}
