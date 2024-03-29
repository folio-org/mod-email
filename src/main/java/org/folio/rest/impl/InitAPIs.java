package org.folio.rest.impl;

import static org.folio.util.EmailUtils.MAIL_SERVICE_ADDRESS;
import static org.folio.util.EmailUtils.STORAGE_SERVICE_ADDRESS;

import java.lang.management.ManagementFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.resource.interfaces.InitAPI;
import org.folio.services.email.MailService;
import org.folio.services.storage.StorageService;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.serviceproxy.ServiceBinder;

/**
 * Performs preprocessing operations before the verticle is deployed,
 * e.g. components registration, initializing, binding.
 */
public class InitAPIs implements InitAPI {

  private final Logger log = LogManager.getLogger(InitAPIs.class);

  @Override
  public void init(Vertx vertx, Context context, Handler<AsyncResult<Boolean>> handler) {
    log.debug("init:: ");
    final int port = Integer.parseInt(
      System.getProperty("http.port", System.getProperty("port", "8080")));
    log.info("init:: {} on port {}", () -> ManagementFactory.getRuntimeMXBean().getName(),
      () -> port);
    new ServiceBinder(vertx)
      .setAddress(MAIL_SERVICE_ADDRESS)
      .register(MailService.class, MailService.create(vertx));
    new ServiceBinder(vertx)
      .setAddress(STORAGE_SERVICE_ADDRESS)
      .register(StorageService.class, StorageService.create(vertx));

    handler.handle(Future.succeededFuture(true));
  }
}
