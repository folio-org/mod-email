package org.folio.rest.impl.base;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static io.vertx.core.json.JsonObject.mapFrom;
import static javax.ws.rs.core.HttpHeaders.ACCEPT;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.folio.HttpStatus.HTTP_OK;
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
import java.util.Map;
import java.util.regex.Pattern;

import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.exceptions.ConfigurationException;
import org.folio.exceptions.SmtpConfigurationException;
import org.folio.rest.jaxrs.model.Configurations;
import org.folio.rest.jaxrs.model.EmailEntity;
import org.folio.rest.jaxrs.model.EmailEntries;
import org.folio.services.email.MailService;
import org.folio.services.storage.StorageService;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

public abstract class AbstractEmail {

  private static final String REQUEST_URL_TEMPLATE = "%s/%s?query=module==%s";
  private static final String REQUEST_URI_PATH = "configurations/entries";
  private static final String OKAPI_URL_HEADER = "x-okapi-url";
  private static final String MODULE_EMAIL_SMTP_SERVER = "SMTP_SERVER";
  private static final String LOOKUP_TIMEOUT = "lookup.timeout";
  private static final String LOOKUP_TIMEOUT_VAL = "1000";

  private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
  private static final String ERROR_LOOKING_UP_MOD_CONFIG = "Error looking up config at url=%s | Expected status code 200, got %s | error message: %s";
  private static final String ERROR_MIN_REQUIREMENT_MOD_CONFIG = "The 'mod-config' module doesn't have a minimum config for SMTP server, the min config is: %s";
  private static final String ERROR_MESSAGE_INCORRECT_DATE_PARAMETER = "Invalid date value, the parameter must be in the format: yyyy-MM-dd";

  protected final Logger logger = LogManager.getLogger(AbstractEmail.class);
  protected final Vertx vertx;
  private final String tenantId;

  private WebClient httpClient;
  private MailService mailService;
  private StorageService storageService;

  /**
   * Timeout to wait for response
   */
  private final int lookupTimeout = Integer.parseInt(
    MODULE_SPECIFIC_ARGS.getOrDefault(LOOKUP_TIMEOUT, LOOKUP_TIMEOUT_VAL));

  protected AbstractEmail(Vertx vertx, String tenantId) {
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
    WebClientOptions options = new WebClientOptions();
    options.setConnectTimeout(lookupTimeout);
    options.setIdleTimeout(lookupTimeout);
    this.httpClient = WebClient.create(vertx, options);
  }

  protected Future<Configurations> lookupConfig(Map<String, String> requestHeaders) {
    MultiMap headers = MultiMap.caseInsensitiveMultiMap().addAll(requestHeaders);
    String okapiUrl = headers.get(OKAPI_URL_HEADER);
    String okapiToken = headers.get(OKAPI_HEADER_TOKEN);
    String url = String.format(REQUEST_URL_TEMPLATE, okapiUrl, REQUEST_URI_PATH, MODULE_EMAIL_SMTP_SERVER);

    return httpClient.getAbs(url)
      .putHeader(OKAPI_HEADER_TOKEN, okapiToken)
      .putHeader(OKAPI_HEADER_TENANT, tenantId)
      .putHeader(CONTENT_TYPE, APPLICATION_JSON)
      .putHeader(ACCEPT, APPLICATION_JSON)
      .send()
      .compose(response -> {
        if (response.statusCode() != HTTP_OK.toInt()) {
          String errorMessage = String.format(ERROR_LOOKING_UP_MOD_CONFIG,
            url, response.statusCode(), response.bodyAsString());
          logger.error(errorMessage);
          return failedFuture(new ConfigurationException(errorMessage));
        } else {
          Configurations configurations = response.bodyAsJsonObject().mapTo(Configurations.class);
          if (isIncorrectSmtpServerConfig(configurations)) {
            String errorMessage = String.format(ERROR_MIN_REQUIREMENT_MOD_CONFIG,
              REQUIREMENTS_CONFIG_SET);
            logger.error(errorMessage);
            return failedFuture(new SmtpConfigurationException(errorMessage));
          }

          return succeededFuture(configurations);
        }
      });
  }

  protected Future<JsonObject> sendEmail(Configurations configurations, EmailEntity entity) {
    Promise<JsonObject> promise = Promise.promise();
    mailService.sendEmail(mapFrom(configurations), mapFrom(entity), promise);
    return promise.future();
  }

  protected Future<JsonObject> handleFailure(Throwable throwable, EmailEntity email) {
    return saveEmail(mapFrom(email
      .withStatus(EmailEntity.Status.FAILURE)
      .withDate(Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC)))
      .withMessage(throwable.getMessage())));
  }

  protected Future<JsonObject> saveEmail(JsonObject emailEntityJson) {
    Promise<JsonObject> promise = Promise.promise();
    storageService.saveEmailEntity(tenantId, emailEntityJson, promise);
    return promise.future();
  }

  protected Future<JsonObject> findEmailEntries(int limit, int offset, String query) {
    Promise<JsonObject> promise = Promise.promise();
    storageService.findEmailEntries(tenantId, limit, offset, query, promise);
    return promise.future();
  }

  protected EmailEntries mapJsonObjectToEmailEntries(JsonObject emailEntriesJson) {
    return emailEntriesJson.mapTo(EmailEntries.class);
  }

  protected Future<Void> deleteEmailsByExpirationDate(String expirationDate, String emailStatus) {
    Promise<Void> promise = Promise.promise();
    storageService.deleteEmailEntriesByExpirationDateAndStatus(tenantId, expirationDate,
      emailStatus,
      result -> {
        if (result.failed()) {
          promise.fail(result.cause());
          return;
        }
        promise.complete();
      });
    return promise.future();
  }

  protected Future<String> determinateEmailStatus(String emailStatus) {
    Promise<String> promise = Promise.promise();
    String status = StringUtils.isBlank(emailStatus)
      ? EmailEntity.Status.DELIVERED.value()
      : findStatusByName(emailStatus);
    promise.complete(status);
    return promise.future();
  }

  protected Future<Void> checkExpirationDate(String expirationDate) {
    Promise<Void> promise = Promise.promise();
    if (StringUtils.isBlank(expirationDate) || isCorrectDateFormat(expirationDate)) {
      promise.complete();
    } else {
      promise.fail(new IllegalArgumentException(ERROR_MESSAGE_INCORRECT_DATE_PARAMETER));
    }
    return promise.future();
  }

  protected Response mapExceptionToResponse(Throwable t) {
    String errMsg = t.getMessage();
    logger.error(errMsg, t);

    if (t.getClass() == ConfigurationException.class) {
      return Response.status(400)
        .header(CONTENT_TYPE, TEXT_PLAIN)
        .entity(errMsg)
        .build();
    }

    if (t.getClass() == SmtpConfigurationException.class) {
      return Response.status(200)
        .header(CONTENT_TYPE, TEXT_PLAIN)
        .entity(errMsg)
        .build();
    }

    return Response.status(500)
      .header(CONTENT_TYPE, TEXT_PLAIN)
      .entity(Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase())
      .build();
  }

  private boolean isCorrectDateFormat(String expirationDate) {
    return DATE_PATTERN.matcher(expirationDate).matches();
  }

  protected static String extractMessage(JsonObject emailJson) {
    return emailJson.mapTo(EmailEntity.class)
      .getMessage();
  }

}
