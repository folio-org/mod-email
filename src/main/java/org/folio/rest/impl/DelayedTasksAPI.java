package org.folio.rest.impl;

import static javax.ws.rs.core.Response.Status;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.impl.base.AbstractEmail;
import org.folio.rest.jaxrs.resource.DelayedTask;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

public class DelayedTasksAPI extends AbstractEmail implements DelayedTask {

  public DelayedTasksAPI(Vertx vertx, String tenantId) {
    super(vertx, tenantId);
  }

  @Override
  public void deleteDelayedTaskExpiredMessages(String expirationDate, String status, Map<String, String> headers,
                                               Handler<AsyncResult<Response>> resultHandler, Context context) {
    Future.succeededFuture()
      .compose(v -> checkExpirationDate(expirationDate))
      .compose(v -> determinateEmailStatus(status))
      .compose(emailStatus -> deleteEmailsByExpirationDate(expirationDate, emailStatus))
      .map(v -> DeleteDelayedTaskExpiredMessagesResponse.respond204WithTextPlain(Status.NO_CONTENT))
      .map(Response.class::cast)
      .otherwise(this::mapExceptionToResponse)
      .setHandler(resultHandler);
  }
}
