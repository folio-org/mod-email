package org.folio.rest.impl;

import io.vertx.core.*;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.jaxrs.model.Configurations;
import org.folio.rest.jaxrs.model.EmailEntity;
import org.folio.rest.jaxrs.resource.Email;
import org.folio.services.MailService;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.util.Map;

import static org.folio.rest.RestVerticle.*;
import static org.folio.util.EmailUtils.*;

public class EmailAPI implements Email {

  private static final String REQUEST_URL_TEMPLATE = "%s/%s?query=module==%s";
  private static final String REQUEST_URI_PATH = "configurations/entries";
  private static final String HTTP_HEADER_ACCEPT = HttpHeaders.ACCEPT.toString();
  private static final String HTTP_HEADER_CONTENT_TYPE = HttpHeaders.CONTENT_TYPE.toString();
  private static final String OKAPI_URL_HEADER = "x-okapi-url";
  private static final String MODULE_EMAIL_SMTP_SERVER = "SMTP_SERVER";
  private static final String LOOKUP_TIMEOUT = "lookup.timeout";
  private static final String LOOKUP_TIMEOUT_VAL = "1000";

  private static final String ERROR_LOOKING_UP_MOD_CONFIG = "Error looking up config at url=%s | Expected status code 200, got %s | error message: %s";
  private static final String ERROR_MIN_REQUIREMENT_MOD_CONFIG = "The 'mod-config' module doesn't have a minimum config for SMTP server, the min config is: %s";

  private final Logger logger = LoggerFactory.getLogger(EmailAPI.class);
  private final Vertx vertx;

  private String tenantId;
  private HttpClient httpClient;

  /**
   * Timeout to wait for response
   */
  private int lookupTimeout = Integer.parseInt(MODULE_SPECIFIC_ARGS.getOrDefault(LOOKUP_TIMEOUT, LOOKUP_TIMEOUT_VAL));

  public EmailAPI(final Vertx vertx, final String tenantId) {
    this.tenantId = tenantId;
    this.vertx = vertx;
    initHttpClient();
  }

  /**
   * init the http client to 'mod-config'
   */
  private void initHttpClient() {
    HttpClientOptions options = new HttpClientOptions();
    options.setConnectTimeout(lookupTimeout);
    options.setIdleTimeout(lookupTimeout);
    this.httpClient = vertx.createHttpClient(options);
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
      asyncResultHandler.handle(Future.failedFuture(ex));
    }
  }

  private Future<JsonObject> lookupConfig(MultiMap headers) {
    Future<JsonObject> future = Future.future();
    String okapiUrl = headers.get(OKAPI_URL_HEADER);
    String okapiToken = headers.get(OKAPI_HEADER_TOKEN);
    String requestUrl = String.format(REQUEST_URL_TEMPLATE, okapiUrl, REQUEST_URI_PATH, MODULE_EMAIL_SMTP_SERVER);
    HttpClientRequest request = httpClient.getAbs(requestUrl);
    request
      .putHeader(OKAPI_HEADER_TOKEN, okapiToken)
      .putHeader(OKAPI_HEADER_TENANT, tenantId)
      .putHeader(HTTP_HEADER_CONTENT_TYPE, MediaType.APPLICATION_JSON)
      .putHeader(HTTP_HEADER_ACCEPT, MediaType.APPLICATION_JSON)
      .handler(response -> {
        if (response.statusCode() != 200) {
          response.bodyHandler(bufHandler ->
            future.fail(String.format(ERROR_LOOKING_UP_MOD_CONFIG, requestUrl, response.statusCode(), bufHandler.toString())));
        } else {
          response.bodyHandler(bufHandler -> {
            JsonObject resultObject = bufHandler.toJsonObject();
            future.complete(resultObject);
          });
        }
      });
    request.end();
    return future;
  }
}
