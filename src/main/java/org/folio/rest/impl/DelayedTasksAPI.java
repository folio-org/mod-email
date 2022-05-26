package org.folio.rest.impl;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

import javax.ws.rs.core.Response;

import org.folio.rest.impl.base.AbstractEmail;
import org.folio.rest.jaxrs.resource.DelayedTask;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.folio.support.RetryEmailsContext;

import static io.vertx.core.Future.succeededFuture;

public class DelayedTasksAPI extends AbstractEmail implements DelayedTask {

  private static final int DEFAULT_LIMIT = 100;

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
      .map(v -> DeleteDelayedTaskExpiredMessagesResponse.respond204())
      .map(Response.class::cast)
      .otherwise(this::mapExceptionToResponse)
      .onComplete(resultHandler);
  }

  @Override
  public void postDelayedTaskRetryFailedEmails(Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    asyncResultHandler.handle(Future.succeededFuture(
      DeleteDelayedTaskExpiredMessagesResponse.respond204()));

    Future.succeededFuture(new RetryEmailsContext(okapiHeaders))
      .compose(this::lookupConfiguration)
      .compose(this::findEmailsForRetry)
      .compose(this::resendFailedEmails);
  }

  private Future<RetryEmailsContext> lookupConfiguration(RetryEmailsContext retryEmailsContext) {
    return Future.succeededFuture(retryEmailsContext)
      .compose(entry -> lookupConfig(entry.getOkapiHeaders())
      .map(entry::setConfiguration));
  }

  private Future<RetryEmailsContext> findEmailsForRetry(RetryEmailsContext retryEmailsContext){
    return Future.succeededFuture(retryEmailsContext)
      .compose(entry -> findEmailEntries(DEFAULT_LIMIT, 0, "shouldRetry=true")
      .compose(this::mapJsonObjectToEmailEntries)
      .map(retryEmailsContext::setEmailEntries));
  }

  private Future<Void> resendFailedEmails(RetryEmailsContext context) {
    return chainFutures(context.getEmailEntries().getEmailEntity(),
      emailEntity -> processEmail(context.getConfiguration(), emailEntity));
  }

  private  <T> Future<Void> chainFutures(Collection<T> list, Function<T, Future<Void>> method) {
    return list.stream().reduce(succeededFuture(),
      (acc, item) -> acc.compose(v -> method.apply(item)),
      (a, b) -> succeededFuture());
  }
}
