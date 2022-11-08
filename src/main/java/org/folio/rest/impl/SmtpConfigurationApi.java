package org.folio.rest.impl;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.jaxrs.model.SmtpConfiguration;
import org.folio.rest.jaxrs.model.SmtpConfigurations;
import org.folio.rest.persist.PgUtil;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;

public class SmtpConfigurationApi implements org.folio.rest.jaxrs.resource.SmtpConfiguration {
  public static final String SMTP_CONFIGURATION_TABLE_NAME = "smtp_configuration";

  @Override
  public void getSmtpConfiguration(int offset, int limit, String query, String lang,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PgUtil.get(SMTP_CONFIGURATION_TABLE_NAME, SmtpConfiguration.class, SmtpConfigurations.class,
      query, offset, limit, okapiHeaders, vertxContext, GetSmtpConfigurationResponse.class)
      .onComplete(asyncResultHandler);
  }

  @Override
  public void postSmtpConfiguration(String lang, SmtpConfiguration entity,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PgUtil.post(SMTP_CONFIGURATION_TABLE_NAME, entity, okapiHeaders, vertxContext,
      PostSmtpConfigurationResponse.class)
      .onComplete(asyncResultHandler);
  }

  @Override
  public void getSmtpConfigurationBySmtpConfigurationId(String smtpConfigurationId, String lang,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PgUtil.getById(SMTP_CONFIGURATION_TABLE_NAME, SmtpConfiguration.class,
      smtpConfigurationId, okapiHeaders, vertxContext,
      GetSmtpConfigurationBySmtpConfigurationIdResponse.class).onComplete(asyncResultHandler);
  }

  @Override
  public void putSmtpConfigurationBySmtpConfigurationId(String smtpConfigurationId, String lang,
    SmtpConfiguration entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    PgUtil.put(SMTP_CONFIGURATION_TABLE_NAME, entity, smtpConfigurationId, okapiHeaders,
      vertxContext, PutSmtpConfigurationBySmtpConfigurationIdResponse.class)
      .onComplete(asyncResultHandler);
  }

  @Override
  public void deleteSmtpConfigurationBySmtpConfigurationId(String smtpConfigurationId, String lang,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler,
    Context vertxContext) {

    PgUtil.deleteById(SMTP_CONFIGURATION_TABLE_NAME, smtpConfigurationId, okapiHeaders,
      vertxContext, DeleteSmtpConfigurationBySmtpConfigurationIdResponse.class)
      .onComplete(asyncResultHandler);
  }
}
