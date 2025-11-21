package org.folio.rest.impl;

import com.github.dockerjava.zerodep.shaded.org.apache.hc.client5.http.impl.classic.FutureRequestExecutionMetrics;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.Setting;
import org.folio.rest.jaxrs.resource.EmailSettings;
import org.folio.rest.impl.base.AbstractMailSettings;

public class EmailSettingsAPI extends AbstractMailSettings implements EmailSettings {

  protected static final Logger log = LogManager.getLogger(EmailSettingsAPI.class);

  @SuppressWarnings("unused")
  public EmailSettingsAPI(Vertx vertx, String tenantId) {
    super();
  }

  @Override
  public void getEmailSettings(String query, int offset, int limit, String lang,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> resultHandler,
    Context vertxContext) {

    findSettingsByQuery(query, offset, limit, okapiHeaders, vertxContext)
      .otherwise(this::handleServiceError)
      .onComplete(resultHandler);
  }

  @Override
  public void getEmailSettingsById(String id, String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    findSettingById(id, okapiHeaders, vertxContext)
      .otherwise(this::handleServiceError)
      .onComplete(asyncResultHandler);
  }

  @Override
  public void postEmailSettings(String lang, Setting entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> resultHandler, Context vertxContext) {
    Future.succeededFuture(entity)
      .compose(newEntity -> createSetting(newEntity, okapiHeaders, vertxContext))
      .otherwise(this::handleServiceError)
      .onComplete(resultHandler)
      .onFailure(e -> log.error("Failed to create setting", e));
  }

  @Override
  public void deleteEmailSettingsById(String id, String lang, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> resultHandler, Context vertxContext) {
    deleteSettingById(id, okapiHeaders, vertxContext)
      .otherwise(this::handleServiceError)
      .onComplete(resultHandler);
  }

  @Override
  public void putEmailSettingsById(String id, String lang, Setting entity, Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> resultHandler, Context vertxContext) {
    Future.succeededFuture(entity)
      .compose(updatedEntity -> updateSettingById(id, updatedEntity, okapiHeaders, vertxContext))
      .otherwise(this::handleServiceError)
      .onComplete(resultHandler);
  }
}
