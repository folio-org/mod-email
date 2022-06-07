package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;
import static java.lang.System.currentTimeMillis;
import static java.time.ZoneOffset.UTC;
import static java.time.format.DateTimeFormatter.ISO_ZONED_DATE_TIME;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.impl.base.AbstractEmail;
import org.folio.rest.jaxrs.model.EmailEntity;
import org.folio.rest.jaxrs.model.EmailEntries;
import org.folio.rest.jaxrs.resource.DelayedTask;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

public class DelayedTasksAPI extends AbstractEmail implements DelayedTask {

  private static final int RETRY_AGE_THRESHOLD_MINUTES = 10;
  private static final int RETRY_BATCH_SIZE = 50;
  private static final String FIND_EMAILS_FOR_RETRY_QUERY_TEMPLATE =
    "shouldRetry==true and date > %s sortBy attemptsCount/sort.ascending";

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

    asyncResultHandler.handle(succeededFuture(
      PostDelayedTaskRetryFailedEmailsResponse.respond202()));

    logger.info("Starting email retry job");
    final long startTimeMillis = currentTimeMillis();

    findEmailsForRetry()
      .compose(emails -> processEmails(emails, okapiHeaders))
      .onComplete(r -> logRetryResult(r, startTimeMillis));
  }

  private Future<List<EmailEntity>> findEmailsForRetry() {
    String thresholdDate = ZonedDateTime.now(UTC)
      .minusMinutes(RETRY_AGE_THRESHOLD_MINUTES)
      .format(ISO_ZONED_DATE_TIME);

    String query = String.format(FIND_EMAILS_FOR_RETRY_QUERY_TEMPLATE, thresholdDate);

    return findEmailEntries(RETRY_BATCH_SIZE, 0, query)
      .map(EmailEntries::getEmailEntity)
      .onSuccess(emails -> logger.info("Found {} emails for retry", emails.size()));
  }

  private static void logRetryResult(AsyncResult<List<EmailEntity>> result, long startTimeMillis) {
    long duration = currentTimeMillis() - startTimeMillis;
    if (result.succeeded()) {
      logger.info("Email retry job took {} ms and finished successfully", duration);
    } else {
      logger.error("Email retry job took {} ms and failed: {}", duration, result.cause().getMessage());
    }
  }

}
