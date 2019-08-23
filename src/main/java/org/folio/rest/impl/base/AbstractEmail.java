package org.folio.rest.impl.base;

import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.folio.rest.RestVerticle.MODULE_SPECIFIC_ARGS;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TOKEN;
import static org.folio.util.EmailUtils.MAIL_SERVICE_ADDRESS;
import static org.folio.util.EmailUtils.REQUIREMENTS_CONFIG_SET;
import static org.folio.util.EmailUtils.STORAGE_SERVICE_ADDRESS;
import static org.folio.util.EmailUtils.findStatusByName;
import static org.folio.util.EmailUtils.isIncorrectSmtpServerConfig;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.regex.Pattern;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.folio.rest.jaxrs.model.Configurations;
import org.folio.rest.jaxrs.model.EmailEntity;
import org.folio.rest.jaxrs.model.EmailEntries;
import org.folio.services.email.MailService;
import org.folio.services.storage.StorageService;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public abstract class AbstractEmail {

  private static final String REQUEST_URL_TEMPLATE = "%s/%s?query=module==%s";
  private static final String REQUEST_URI_PATH = "configurations/entries";
  private static final String HTTP_HEADER_ACCEPT = HttpHeaders.ACCEPT.toString();
  private static final String HTTP_HEADER_CONTENT_TYPE = HttpHeaders.CONTENT_TYPE.toString();
  private static final String OKAPI_URL_HEADER = "x-okapi-url";
  private static final String MODULE_EMAIL_SMTP_SERVER = "SMTP_SERVER";
  private static final String LOOKUP_TIMEOUT = "lookup.timeout";
  private static final String LOOKUP_TIMEOUT_VAL = "1000";

  private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
  private static final String ERROR_LOOKING_UP_MOD_CONFIG = "Error looking up config at url=%s | Expected status code 200, got %s | error message: %s";
  private static final String ERROR_MIN_REQUIREMENT_MOD_CONFIG = "The 'mod-config' module doesn't have a minimum config for SMTP server, the min config is: %s";
  private static final String ERROR_MESSAGE_INCORRECT_DATE_PARAMETER = "Invalid date value, the parameter must be in the format: yyyy-MM-dd";

  protected final Logger logger = LoggerFactory.getLogger(AbstractEmail.class);
  protected final Vertx vertx;
  private String tenantId;

  private HttpClient httpClient;
  private MailService mailService;
  private StorageService storageService;

  /**
   * Timeout to wait for response
   */
  private int lookupTimeout = Integer.parseInt(MODULE_SPECIFIC_ARGS.getOrDefault(LOOKUP_TIMEOUT, LOOKUP_TIMEOUT_VAL));

  public AbstractEmail(Vertx vertx, String tenantId) {
    this.vertx = vertx;
    this.tenantId = tenantId;
    initHttpClient();
    initServices();
  }

  /**
   * Initialization of the email sending and storage service
   */
  private void initServices() {
    mailService = MailService.createProxy(vertx, MAIL_SERVICE_ADDRESS);
    storageService = StorageService.createProxy(vertx, STORAGE_SERVICE_ADDRESS);
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

  protected Future<JsonObject> lookupConfig(MultiMap headers) {
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

  protected Future<JsonObject> checkConfiguration(JsonObject conf, EmailEntity entity) {
    Future<JsonObject> future = Future.future();
    Configurations configurations = conf.mapTo(Configurations.class);
    if (isIncorrectSmtpServerConfig(configurations)) {
      String errorMessage = String.format(ERROR_MIN_REQUIREMENT_MOD_CONFIG, REQUIREMENTS_CONFIG_SET);
      JsonObject emailEntityJson = JsonObject.mapFrom(entity
        .withStatus(EmailEntity.Status.FAILURE)
        .withDate(Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC)))
        .withMessage(errorMessage));

      saveEmail(emailEntityJson).setHandler(result -> {
        if (result.failed()) {
          logger.error(result.cause());
        }
      });

      logger.error(errorMessage);
      future.fail(errorMessage);
    } else {
      future.complete(conf);
    }
    return future;
  }

  protected Future<JsonObject> sendEmail(JsonObject configJson, EmailEntity entity) {
    Future<JsonObject> future = Future.future();
    JsonObject emailEntityJson = JsonObject.mapFrom(entity);
    mailService.sendEmail(configJson, emailEntityJson, future);
    return future;
  }

  protected Future<String> saveEmail(JsonObject emailEntityJson) {
    Future<String> future = Future.future();
    storageService.saveEmailEntity(tenantId, emailEntityJson, result -> {
      if (result.failed()) {
        future.fail(result.cause());
        return;
      }
      EmailEntity emailEntity = emailEntityJson.mapTo(EmailEntity.class);
      future.complete(emailEntity.getMessage());
    });
    return future;
  }

  protected Future<JsonObject> findEmailEntries(int limit, int offset, String query) {
    Future<JsonObject> future = Future.future();
    storageService.findEmailEntries(tenantId, limit, offset, query, future);
    return future;
  }

  protected Future<EmailEntries> mapJsonObjectToEmailEntries(JsonObject emailEntriesJson) {
    return Future.succeededFuture(emailEntriesJson.mapTo(EmailEntries.class));
  }

  protected Future<Void> deleteEmailsByExpirationDate(String expirationDate, String emailStatus) {
    Future<Void> future = Future.future();
    storageService.deleteEmailEntriesByExpirationDateAndStatus(tenantId, expirationDate, emailStatus,
      result -> {
        if (result.failed()) {
          future.fail(result.cause());
          return;
        }
        future.complete();
      });
    return future;
  }

  protected Future<String> determinateEmailStatus(String emailStatus) {
    Future<String> future = Future.future();
    String status = StringUtils.isBlank(emailStatus)
      ? EmailEntity.Status.DELIVERED.value()
      : findStatusByName(emailStatus);
    future.complete(status);
    return future;
  }

  protected Future<Void> checkExpirationDate(String expirationDate) {
    Future<Void> future = Future.future();
    if (StringUtils.isBlank(expirationDate) || isCorrectDateFormat(expirationDate)) {
      future.complete();
    } else {
      future.fail(new IllegalArgumentException(ERROR_MESSAGE_INCORRECT_DATE_PARAMETER));
    }
    return future;
  }

  protected Response mapExceptionToResponse(Throwable t) {
    logger.error(t.getMessage(), t);
    return Response.status(500)
      .header(CONTENT_TYPE, TEXT_PLAIN)
      .entity(Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase())
      .build();
  }

  private boolean isCorrectDateFormat(String expirationDate) {
    return DATE_PATTERN.matcher(expirationDate).matches();
  }
}
