package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import org.folio.rest.jaxrs.model.Setting;
import org.folio.rest.jaxrs.model.SettingCollection;
import org.folio.rest.jaxrs.resource.EmailSettings;
import org.folio.rest.persist.PgUtil;

import javax.ws.rs.core.Response;
import java.util.Map;

public class EmailSettingsAPI implements EmailSettings {

  private static final String SETTINGS_TABLE = "settings";

  @Override
  public void getEmailSettings(String query, int offset, int limit, String lang, Map<String, String> okapiHeaders,
                               Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(SETTINGS_TABLE, Setting.class, SettingCollection.class, query, offset, limit,
      okapiHeaders, vertxContext, GetEmailSettingsResponse.class, asyncResultHandler);
  }

  @Override
  public void postEmailSettings(String lang, Setting entity, Map<String, String> okapiHeaders,
                                Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(SETTINGS_TABLE, entity, okapiHeaders, vertxContext,
      PostEmailSettingsResponse.class, asyncResultHandler);
  }

  @Override
  public void getEmailSettingsById(String id, String lang, Map<String, String> okapiHeaders,
                                   Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(SETTINGS_TABLE, Setting.class, id, okapiHeaders, vertxContext,
      GetEmailSettingsByIdResponse.class, asyncResultHandler);
  }

  @Override
  public void deleteEmailSettingsById(String id, String lang, Map<String, String> okapiHeaders,
                                      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(SETTINGS_TABLE, id, okapiHeaders, vertxContext,
      DeleteEmailSettingsByIdResponse.class, asyncResultHandler);
  }

  @Override
  public void putEmailSettingsById(String id, String lang, Setting entity, Map<String, String> okapiHeaders,
                                   Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(SETTINGS_TABLE, entity, id, okapiHeaders, vertxContext,
      PutEmailSettingsByIdResponse.class, asyncResultHandler);
  }
}
