package org.folio.rest.impl;

import java.util.Map;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;

import io.vertx.core.json.JsonObject;
import org.folio.rest.impl.base.AbstractEmail;
import org.folio.rest.jaxrs.model.EmailEntity;
import org.folio.rest.jaxrs.model.EmailEntries;
import org.folio.rest.jaxrs.resource.Email;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.CompositeFuture;

public class EmailAPI extends AbstractEmail implements Email {

  public EmailAPI(Vertx vertx, String tenantId) {
    super(vertx, tenantId);
  }

  @Override
  public void postEmail(EmailEntity entity, Map<String, String> requestHeaders,
                        Handler<AsyncResult<Response>> resultHandler, Context context) {
    Future.succeededFuture()
      .compose(v -> lookupConfig(requestHeaders))
      .compose(this::checkConfiguration)
      .onFailure(c -> saveEmailWithErrorMessage(c, entity))
      .compose(conf -> sendEmail(conf, entity))
      .compose(this::saveEmail)
      .map(PostEmailResponse::respond200WithTextPlain)
      .map(Response.class::cast)
      .otherwise(this::mapExceptionToResponse)
      .onComplete(resultHandler);
  }

  @Override
  public void getEmail(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
                       Handler<AsyncResult<Response>> resultHandler, Context context) {
    Future.succeededFuture()
      .compose(v -> findEmailEntries(limit, offset, query))
      .compose(this::mapJsonObjectToEmailEntries)
      .map(GetEmailResponse::respond200WithApplicationJson)
      .map(Response.class::cast)
      .otherwise(this::mapExceptionToResponse)
      .onComplete(resultHandler);
  }

  @Override
  public void postEmailRetry(Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    RetryEmailsContext retryEmailsContext = new RetryEmailsContext(null, null);
    Future.succeededFuture()
      .compose(v -> lookupConfig(okapiHeaders))
      .compose(this::checkConfiguration)
      .map(retryEmailsContext::setConfiguration)
      .compose(v -> findEmailsForRetry())
      .compose(this::mapJsonObjectToEmailEntries)
      .map(retryEmailsContext::setEmailEntries)
      .compose(c -> CompositeFuture.all(c
        .getEmailEntries()
        .getEmailEntity()
        .stream()
        .map(e -> retrySendEmail(c.getConfiguration(), e)
          .compose(this::saveEmail))
        .collect(Collectors.toList())));
  }

  private static class RetryEmailsContext {
    private EmailEntries emailEntries;
    private JsonObject configuration;

    private RetryEmailsContext(EmailEntries emailEntries, JsonObject configuration) {
      this.emailEntries = emailEntries;
      this.configuration = configuration;
    }

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
