package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.json.JsonObject;
import org.folio.rest.impl.base.AbstractEmail;
import org.folio.rest.jaxrs.model.Configurations;
import org.folio.rest.jaxrs.model.EmailEntity;
import org.folio.rest.jaxrs.resource.Email;
import org.folio.services.email.MailService;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.util.Map;

import static org.folio.util.EmailUtils.*;

public class EmailAPI extends AbstractEmail implements Email {

  public EmailAPI(Vertx vertx, String tenantId) {
    super(vertx, tenantId);
  }

  @Override
  public void postEmail(EmailEntity entity, Map<String, String> requestHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context context) {
    MultiMap caseInsensitiveHeaders = new CaseInsensitiveHeaders().addAll(requestHeaders);
    try {
      lookupConfig(caseInsensitiveHeaders).setHandler(lookupConfigHandler -> {
        if (lookupConfigHandler.failed()) {
          PostEmailResponse response = createResponse(Status.BAD_REQUEST, lookupConfigHandler.cause().getMessage());
          asyncResultHandler.handle(Future.succeededFuture(response));
          return;
        }
        Configurations configurations = lookupConfigHandler.result().mapTo(Configurations.class);
        if (checkMinConfigSmtpServer(configurations)) {
          String errorMessage = String.format(ERROR_MIN_REQUIREMENT_MOD_CONFIG, REQUIREMENTS_CONFIG_SET);
          logger.error(errorMessage);
          asyncResultHandler.handle(Future.succeededFuture(createResponse(Status.INTERNAL_SERVER_ERROR, errorMessage)));
          return;
        }

        MailService mailService = MailService.createProxy(vertx, MAIL_SERVICE_ADDRESS);
        JsonObject congJson = JsonObject.mapFrom(configurations);
        JsonObject entityJson = JsonObject.mapFrom(entity);
        mailService.sendEmail(congJson, entityJson, result -> {
          if (result.failed()) {
            String errorMessage = result.cause().getMessage();
            logger.error(errorMessage);
            asyncResultHandler.handle(Future.succeededFuture(createResponse(Status.INTERNAL_SERVER_ERROR, errorMessage)));
            return;
          }
          String message = result.result().getString(MESSAGE_RESULT);
          asyncResultHandler.handle(Future.succeededFuture(createResponse(Status.OK, message)));
        });
      });
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
      asyncResultHandler.handle(Future.succeededFuture(
        createResponse(Status.INTERNAL_SERVER_ERROR, ex.getMessage())));
    }
  }
}
