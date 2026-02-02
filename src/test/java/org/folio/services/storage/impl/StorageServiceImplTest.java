package org.folio.services.storage.impl;

import org.folio.services.storage.StorageService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class StorageServiceImplTest {
  
  private Vertx vertx;
  private StorageService storageService;

  @Before
  public void setUp() {
    vertx = Vertx.vertx();
    storageService = new StorageServiceImpl(vertx);
  }

  @Test
  public void saveEmailEntityShouldFail(TestContext context) {
    storageService.saveEmailEntity(null, null)
      .onComplete(context.asyncAssertFailure());
  }

  @Test
  public void findEmailEntriesShouldFail(TestContext context) {
    storageService.findEmailEntries(null, 0, 0, null)
      .onComplete(context.asyncAssertFailure());
  }

  @Test
  public void deleteEmailEntriesByExpirationDateAndStatusEmailEntriesShouldFail(TestContext context) {
    storageService.deleteEmailEntriesByExpirationDateAndStatus(null, null, null)
      .onComplete(context.asyncAssertFailure());
  }

  @Test
  public void deleteEmailEntriesShouldFailWithInvalidTenant(TestContext context) {
    // setting the tenantId as public to simulate the error
    storageService.deleteEmailEntriesByExpirationDateAndStatus("public", null, null)
      .onComplete(context.asyncAssertFailure());
  }
}
