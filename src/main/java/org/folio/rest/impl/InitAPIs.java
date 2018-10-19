package org.folio.rest.impl;

import io.vertx.core.*;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.resource.interfaces.InitAPI;

import java.lang.management.ManagementFactory;

/**
 * Performs preprocessing operations before the verticle is deployed,
 * e.g. components registration, initializing, binding.
 */
public class InitAPIs implements InitAPI {

  private final Logger logger = LoggerFactory.getLogger(InitAPIs.class);

  @Override
  public void init(Vertx vertx, Context context, Handler<AsyncResult<Boolean>> handler) {
    final int port = Integer.parseInt(System.getProperty("port", "8080"));
    logger.info(InitAPIs.class.getSimpleName()+ " | " + ManagementFactory.getRuntimeMXBean().getName() + " on port " + port);
    handler.handle(Future.succeededFuture(true));
  }
}
