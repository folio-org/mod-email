package org.folio.rest.impl.base;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static io.vertx.core.json.JsonObject.mapFrom;
import static java.lang.String.format;
import static java.lang.String.join;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static javax.ws.rs.core.HttpHeaders.ACCEPT;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.folio.HttpStatus.HTTP_OK;
import static org.folio.rest.RestVerticle.MODULE_SPECIFIC_ARGS;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TENANT;
import static org.folio.rest.RestVerticle.OKAPI_HEADER_TOKEN;
import static org.folio.rest.jaxrs.model.EmailEntity.Status.DELIVERED;
import static org.folio.rest.jaxrs.model.EmailEntity.Status.FAILURE;
import static org.folio.util.AsyncUtil.mapInOrder;
import static org.folio.util.EmailUtils.MAIL_SERVICE_ADDRESS;
import static org.folio.util.EmailUtils.STORAGE_SERVICE_ADDRESS;
import static org.folio.util.EmailUtils.findStatusByName;

import java.util.Collection;
import java.util.Date;
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
import org.folio.rest.jaxrs.model.EmailEntity.Status;
import org.folio.rest.jaxrs.model.EmailEntries;
import org.folio.rest.jaxrs.model.SmtpConfiguration;
import org.folio.services.SmtpConfigurationService;
import org.folio.services.email.MailService;
import org.folio.services.storage.StorageService;
import org.folio.util.ClockUtil;
import org.folio.util.EmailUtils;

import io.vertx.core.CompositeFuture;
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
  public static final int RETRY_MAX_ATTEMPTS = 3;

  private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
  private static final String ERROR_LOOKING_UP_MOD_CONFIG = "Error looking up config at url=%s | Expected status code 200, got %s | error message: %s";
  private static final String ERROR_MESSAGE_INCORRECT_DATE_PARAMETER = "Invalid date value, the parameter must be in the format: yyyy-MM-dd";
  private static final String ERROR_SENDING_EMAIL = "Error in the 'mod-email' module, the module didn't send email | message: %s";
  private static final String SUCCESS_SEND_EMAIL = "The message has been delivered to %s";

  protected static final Logger logger = LogManager.getLogger(AbstractEmail.class);
  protected final Vertx vertx;
  private String tenantId;

  private WebClient httpClient;
  private MailService mailService;
  private StorageService storageService;
  private SmtpConfigurationService smtpConfigurationService;

  /**
   * Timeout to wait for response
   */
  private final int lookupTimeout = Integer.parseInt(
    MODULE_SPECIFIC_ARGS.getOrDefault(LOOKUP_TIMEOUT, LOOKUP_TIMEOUT_VAL));

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
    smtpConfigurationService = new SmtpConfigurationService(vertx, tenantId);
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

  protected Future<EmailEntity> processEmail(EmailEntity email,
    Map<String, String> okapiHeaders) {

    return processEmails(singletonList(email), okapiHeaders)
      .map(emails -> emails.stream().findFirst().orElseThrow());
  }

  protected Future<Collection<EmailEntity>> processEmails(Collection<EmailEntity> emails,
    Map<String, String> okapiHeaders) {

    if (emails.isEmpty()) {
      return succeededFuture(emails);
    }

    logger.info("Start processing a batch of {} emails", emails.size());

    return lookupSmtpConfiguration(okapiHeaders)
      .compose(config -> mapInOrder(emails, email -> processEmail(email, config)))
      .recover(t -> handleFailure(emails, t));
  }

  protected Future<EmailEntity> processEmail(EmailEntity email, SmtpConfiguration smtpConfiguration) {
    logger.info("Start processing email {}", email.getId());

    applyConfiguration(email, smtpConfiguration);

    return sendEmail(email, smtpConfiguration)
      .map(this::handleSuccess)
      .otherwise(t -> handleFailure(email, t))
      .compose(this::saveEmail)
      .otherwiseEmpty();
  }

  protected EmailEntity handleSuccess(EmailEntity email) {
    String message = format(SUCCESS_SEND_EMAIL, join(",", email.getTo()));
    logger.info(message);

    return updateEmail(email, DELIVERED, message);
  }

  protected EmailEntity handleFailure(EmailEntity email, Throwable throwable) {
    String errorMessage = format(ERROR_SENDING_EMAIL, throwable.getMessage());
    logger.error(errorMessage);

    return updateEmail(email, FAILURE, errorMessage);
  }

  private static EmailEntity updateEmail(EmailEntity email, Status status, String message) {
    int newAttemptCount = email.getAttemptCount() + 1;

    return email
      .withStatus(status)
      .withMessage(message)
      .withDate(Date.from(ClockUtil.getZonedDateTime().toInstant()))
      .withAttemptCount(newAttemptCount)
      .withShouldRetry(status == FAILURE && newAttemptCount < RETRY_MAX_ATTEMPTS);
  }

  protected Future<Collection<EmailEntity>> handleFailure(Collection<EmailEntity> emails,
    Throwable throwable) {

    logger.error("Failed to process batch of {} emails: {}", emails.size(), throwable.getMessage());

    return CompositeFuture.all(
        emails.stream()
          .map(email -> handleFailure(email, throwable))
          .map(this::saveEmail)
          .collect(toList()))
      .compose(r -> failedFuture(throwable));
  }

  private Future<SmtpConfiguration> lookupSmtpConfiguration(Map<String, String> requestHeaders) {
    return smtpConfigurationService.getSmtpConfiguration()
      .recover(throwable -> fetchSmtpConfigurationFromModConfig(requestHeaders)
        .map(EmailUtils::convertSmtpConfiguration)
        .compose(EmailUtils::validateSmtpConfiguration)
        .compose(smtpConfigurationService::createSmtpConfiguration)
        .compose(smtpConfigurationId -> smtpConfigurationService.getSmtpConfiguration())
      )
      .compose(EmailUtils::validateSmtpConfiguration);
  }

  private Future<Configurations> fetchSmtpConfigurationFromModConfig(Map<String, String> requestHeaders) {
    logger.warn("Failed to find SMTP configuration in the DB, fetching from mod-config");

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
        if (response.statusCode() == HTTP_OK.toInt()) {
          Configurations config = response.bodyAsJsonObject().mapTo(Configurations.class);
          logger.info("Successfully fetched {} configuration entries", config.getConfigs().size());
          return succeededFuture(config);
        }
        String errorMessage = String.format(ERROR_LOOKING_UP_MOD_CONFIG,
          url, response.statusCode(), response.bodyAsString());
        logger.error(errorMessage);
        return failedFuture(new ConfigurationException(errorMessage));
      });
  }

  protected Future<EmailEntity> sendEmail(EmailEntity email, SmtpConfiguration smtpConfiguration) {
    Promise<JsonObject> promise = Promise.promise();
    mailService.sendEmail(mapFrom(smtpConfiguration), mapFrom(email), promise);

    return promise.future().map(email);
  }

  protected Future<EmailEntity> saveEmail(EmailEntity email) {
    Promise<JsonObject> promise = Promise.promise();
    storageService.saveEmailEntity(tenantId, JsonObject.mapFrom(email), promise);

    return promise.future().map(email);
  }

  protected Future<EmailEntries> findEmailEntries(int limit, int offset, String query) {
    Promise<JsonObject> promise = Promise.promise();
    storageService.findEmailEntries(tenantId, limit, offset, query, promise);

    return promise.future()
      .map(json -> json.mapTo(EmailEntries.class));
  }

  protected Future<Void> deleteEmailsByExpirationDate(String expirationDate, String emailStatus) {
    Promise<Void> promise = Promise.promise();
    storageService.deleteEmailEntriesByExpirationDateAndStatus(tenantId, expirationDate, emailStatus,
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
      ? DELIVERED.value()
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

  private static void applyConfiguration(EmailEntity email, SmtpConfiguration smtpConfiguration) {
    if (StringUtils.isBlank(email.getFrom())) {
      email.withFrom(smtpConfiguration.getFrom());
    }
  }

}
