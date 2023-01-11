package org.folio.rest.impl;

import static io.vertx.core.Future.succeededFuture;

import java.util.Map;

import javax.ws.rs.core.Response;

import org.folio.rest.impl.base.AbstractEmail;
import org.folio.rest.jaxrs.model.EmailEntity;
import org.folio.rest.jaxrs.resource.Email;
import static org.folio.util.LogUtil.logAsJson;
import static org.folio.util.LogUtil.logOkapiHeaders;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

public class EmailAPI extends AbstractEmail implements Email {

  public EmailAPI(Vertx vertx, String tenantId) {
    super(vertx, tenantId);
  }

  @Override
  public void postEmail(EmailEntity email, Map<String, String> requestHeaders,
    Handler<AsyncResult<Response>> resultHandler, Context vertxContext) {

    logger.debug("postEmail:: parameters: email={}, requestHeaders={}",
      () -> logAsJson(email), () -> logOkapiHeaders(requestHeaders));

    succeededFuture()
      .compose(v -> processEmail(email, requestHeaders))
      .map(EmailEntity::getMessage)
      .map(PostEmailResponse::respond200WithTextPlain)
      .map(Response.class::cast)
      .otherwise(this::mapExceptionToResponse)
      .onComplete(resultHandler);
  }

  @Override
  public void getEmail(String query, int offset, int limit, String lang,
    Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> resultHandler,
    Context context) {
    logger.debug("getEmail:: parameters: query={}, offset={}, limit={}, lang={}, okapiHeaders={}",
      () -> query, () -> offset, () -> limit, () -> lang, () -> logOkapiHeaders(okapiHeaders));
    succeededFuture()
      .compose(v -> findEmailEntries(limit, offset, query))
      .map(GetEmailResponse::respond200WithApplicationJson)
      .map(Response.class::cast)
      .otherwise(this::mapExceptionToResponse)
      .onComplete(resultHandler);
  }

}
