package org.folio.rest.impl;

import java.util.Map;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;

import io.vertx.core.CompositeFuture;
import io.vertx.core.json.JsonObject;
import org.folio.rest.impl.base.AbstractEmail;
import org.folio.rest.jaxrs.model.EmailEntries;
import org.folio.rest.jaxrs.resource.DelayedTask;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

public class DelayedTasksAPI extends AbstractEmail implements DelayedTask {

  private static final String REQUEST_QUERY_SHOULD_RETRY = "shouldRetry=true";
  private static final int DEFAULT_LIMIT = 1000;

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
  public void postDelayedTaskRetry(Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    RetryEmailsContext retryEmailsContext = new RetryEmailsContext();
    Future.succeededFuture()
      .compose(v -> lookupConfig(okapiHeaders))
      .map(retryEmailsContext::setConfiguration)
      .compose(v -> findEmailEntries(DEFAULT_LIMIT, 0, REQUEST_QUERY_SHOULD_RETRY))
      .compose(this::mapJsonObjectToEmailEntries)
      .map(retryEmailsContext::setEmailEntries)
      .compose(this::resendFailedEmails)
      .map(DelayedTask.PostDelayedTaskRetryResponse::respond204WithTextPlain)
      .map(Response.class::cast)
      .otherwise(this::mapExceptionToResponse)
      .onComplete(asyncResultHandler);
  }

  private CompositeFuture resendFailedEmails(RetryEmailsContext context) {
    return CompositeFuture.all(context
      .getEmailEntries()
      .getEmailEntity()
      .stream()
      .map(email -> sendEmail(context.getConfiguration(), email)
        .compose(this::updateEmail))
      .collect(Collectors.toList()));
  }

  private static class RetryEmailsContext {
    private EmailEntries emailEntries;
    private JsonObject configuration;

    public RetryEmailsContext setEmailEntries(EmailEntries emailEntries) {
      this.emailEntries = emailEntries;
      return this;
    }

    public RetryEmailsContext setConfiguration(JsonObject configuration) {
      this.configuration = configuration;
      return this;
    }

    public EmailEntries getEmailEntries() {
      return emailEntries;
    }

    public JsonObject getConfiguration() {
      return configuration;
    }
  }
}
