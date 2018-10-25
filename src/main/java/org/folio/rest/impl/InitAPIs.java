package org.folio.rest.impl;

import io.vertx.core.*;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.serviceproxy.ServiceBinder;
import org.folio.rest.resource.interfaces.InitAPI;
import org.folio.services.MailService;

import java.lang.management.ManagementFactory;

import static org.folio.util.EmailUtils.MAIL_SERVICE_ADDRESS;

/**
 * Performs preprocessing operations before the verticle is deployed,
 * e.g. components registration, initializing, binding.
 */
public class InitAPIs implements InitAPI {

  private final Logger logger = LoggerFactory.getLogger(InitAPIs.class);

  @Override
  public void init(Vertx vertx, Context context, Handler<AsyncResult<Boolean>> handler) {
    final int port = Integer.parseInt(System.getProperty("port", "8080"));
    logger.info(ManagementFactory.getRuntimeMXBean().getName() + " on port " + port);
    new ServiceBinder(vertx)
      .setAddress(MAIL_SERVICE_ADDRESS)
      .register(MailService.class, MailService.create(vertx));

    handler.handle(Future.succeededFuture(true));
  }
}
