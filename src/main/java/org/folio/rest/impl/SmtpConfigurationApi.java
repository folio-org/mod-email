package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.exceptions.SmtpConfigurationNotFoundException;
import org.folio.rest.jaxrs.model.SmtpConfiguration;
import org.folio.services.SmtpConfigurationService;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class SmtpConfigurationApi implements org.folio.rest.jaxrs.resource.SmtpConfiguration {
  @Override
  public void getSmtpConfiguration(Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    new SmtpConfigurationService(okapiHeaders, vertxContext)
      .getSmtpConfiguration()
      .onSuccess(config -> asyncResultHandler.handle(succeededFuture(
        org.folio.rest.jaxrs.resource.SmtpConfiguration.GetSmtpConfigurationResponse
          .respond200WithApplicationJson(config))))
      .onFailure(failure -> {
        if (failure instanceof SmtpConfigurationNotFoundException) {
          asyncResultHandler.handle(succeededFuture(
            org.folio.rest.jaxrs.resource.SmtpConfiguration.GetSmtpConfigurationResponse
              .respond404WithTextPlain(failure.getMessage())));
          return;
        }
        asyncResultHandler.handle(succeededFuture(
          org.folio.rest.jaxrs.resource.SmtpConfiguration.GetSmtpConfigurationResponse
            .respond500WithTextPlain(failure.getMessage())));
      });
  }

  @Override
  public void postSmtpConfiguration(SmtpConfiguration entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    new SmtpConfigurationService(okapiHeaders, vertxContext)
      .createSmtpConfiguration(entity)
      .onSuccess(createdEntity -> asyncResultHandler.handle(succeededFuture(
        org.folio.rest.jaxrs.resource.SmtpConfiguration.PostSmtpConfigurationResponse
          .respond201WithApplicationJson(createdEntity))))
      .onFailure(failure -> asyncResultHandler.handle(succeededFuture(
        org.folio.rest.jaxrs.resource.SmtpConfiguration.PostSmtpConfigurationResponse
          .respond500WithTextPlain(failure.getMessage()))));
  }

  @Override
  public void putSmtpConfiguration(SmtpConfiguration entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    new SmtpConfigurationService(okapiHeaders, vertxContext)
      .updateSmtpConfiguration(entity)
      .onSuccess(updatedEntity -> asyncResultHandler.handle(succeededFuture(
        org.folio.rest.jaxrs.resource.SmtpConfiguration.PutSmtpConfigurationResponse
          .respond200WithApplicationJson(updatedEntity))))
      .onFailure(failure -> {
        if (failure instanceof SmtpConfigurationNotFoundException) {
          asyncResultHandler.handle(succeededFuture(
            org.folio.rest.jaxrs.resource.SmtpConfiguration.PutSmtpConfigurationResponse
              .respond404WithTextPlain(failure.getMessage())));
          return;
        }
        asyncResultHandler.handle(succeededFuture(
          org.folio.rest.jaxrs.resource.SmtpConfiguration.PutSmtpConfigurationResponse
            .respond500WithTextPlain(failure.getMessage())));
      });
  }

  @Override
  public void deleteSmtpConfiguration(Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    new SmtpConfigurationService(okapiHeaders, vertxContext)
      .deleteSmtpConfiguration()
      .onSuccess(v -> asyncResultHandler.handle(succeededFuture(
        org.folio.rest.jaxrs.resource.SmtpConfiguration.DeleteSmtpConfigurationResponse
          .respond204())))
      .onFailure(failure -> {
        asyncResultHandler.handle(succeededFuture(
          org.folio.rest.jaxrs.resource.SmtpConfiguration.DeleteSmtpConfigurationResponse
            .respond500WithTextPlain(failure.getLocalizedMessage())));
      });
  }
}
