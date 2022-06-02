package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

import javax.ws.rs.core.Response;

import org.folio.rest.impl.base.AbstractEmail;
import org.folio.rest.jaxrs.model.Configurations;
import org.folio.rest.jaxrs.model.EmailEntity;
import org.folio.rest.jaxrs.resource.DelayedTask;
import org.folio.support.RetryEmailsContext;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class DelayedTasksAPI extends AbstractEmail implements DelayedTask {

  private static final String FIND_EMAILS_FOR_RETRY_QUERY = "shouldRetry==true";
  private static final int DEFAULT_RETRY_BATCH_SIZE = 100;

  public DelayedTasksAPI(Vertx vertx, String tenantId) {
    super(vertx, tenantId);
  }

  @Override
  public void deleteDelayedTaskExpiredMessages(String expirationDate, String status,
    Map<String, String> headers, Handler<AsyncResult<Response>> resultHandler, Context context) {

    succeededFuture()
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

    logger.info("Starting email retry job...");

    asyncResultHandler.handle(succeededFuture(
      PostDelayedTaskRetryFailedEmailsResponse.respond202()));

    succeededFuture(new RetryEmailsContext(okapiHeaders))
      .compose(this::lookupConfiguration)
      .compose(this::findEmailsForRetry)
      .compose(this::retryEmails)
      .onComplete(this::logRetryResult);
  }

  private Future<RetryEmailsContext> lookupConfiguration(RetryEmailsContext context) {
    return lookupConfig(context.getOkapiHeaders())
      .map(context::withConfigurations);
  }

  private Future<RetryEmailsContext> findEmailsForRetry(RetryEmailsContext context){
    return findEmailEntries(DEFAULT_RETRY_BATCH_SIZE, 0, FIND_EMAILS_FOR_RETRY_QUERY)
      .map(this::mapJsonObjectToEmails)
      .onSuccess(emails -> logger.info("Found {} emails for retry", emails.size()))
      .map(context::withEmails);
  }

  private Collection<EmailEntity> mapJsonObjectToEmails(JsonObject emailEntriesJson) {
    return mapJsonObjectToEmailEntries(emailEntriesJson)
      .getEmailEntity();
  }

  private Future<Void> retryEmails(RetryEmailsContext context) {
    return chainFutures(context.getEmails(),
      email -> processEmail(context.getConfigurations(), email));
  }

  protected Future<Void> processEmail(Configurations configurations, EmailEntity email) {
    return sendEmail(configurations, email)
      .compose(this::saveEmail)
      .otherwiseEmpty()
      .mapEmpty();
  }

  private static <T> Future<Void> chainFutures(Collection<T> list, Function<T, Future<Void>> method) {
    return list.stream().reduce(succeededFuture(),
      (acc, email) -> acc.compose(v -> method.apply(email)),
      (a, b) -> succeededFuture());
  }

  private void logRetryResult(AsyncResult<Void> result) {
    if (result.succeeded()) {
      logger.info("Email retry job finished successfully");
    } else {
      logger.error("Email retry job failed", result.cause());
    }
  }
}
