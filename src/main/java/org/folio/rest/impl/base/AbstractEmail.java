package org.folio.rest.impl.base;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static io.vertx.core.json.JsonObject.mapFrom;
import static java.lang.String.format;
import static java.lang.String.join;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static org.folio.HttpStatus.HTTP_NO_CONTENT;
import static org.folio.HttpStatus.HTTP_OK;
import static org.folio.rest.RestVerticle.MODULE_SPECIFIC_ARGS;
import static org.folio.rest.jaxrs.model.EmailEntity.Status.DELIVERED;
import static org.folio.rest.jaxrs.model.EmailEntity.Status.FAILURE;
import static org.folio.util.AsyncUtil.mapInOrder;
import static org.folio.util.EmailUtils.MAIL_SERVICE_ADDRESS;
import static org.folio.util.EmailUtils.STORAGE_SERVICE_ADDRESS;
import static org.folio.util.EmailUtils.findStatusByName;
import static org.folio.util.LogUtil.asJson;
import static org.folio.util.LogUtil.emailAsJson;
import static org.folio.util.LogUtil.emailIdsAsString;
import static org.folio.util.LogUtil.headersAsString;
import static org.folio.util.LogUtil.smtpConfigAsJson;

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
import org.folio.rest.client.OkapiClient;
import org.folio.rest.jaxrs.model.Config;
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
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClientOptions;

public abstract class AbstractEmail {

  private static final String CONFIG_BASE_PATH = "/configurations/entries";
  private static final String GET_CONFIG_PATH_TEMPLATE = "%s?query=module==%s";
  private static final String DELETE_CONFIG_PATH_TEMPLATE = "%s/%s";
  private static final String MODULE_EMAIL_SMTP_SERVER = "SMTP_SERVER";
  private static final String LOOKUP_TIMEOUT = "lookup.timeout";
  private static final String LOOKUP_TIMEOUT_VAL = "1000";
  public static final int RETRY_MAX_ATTEMPTS = 3;

  private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
  private static final String ERROR_LOOKING_UP_MOD_CONFIG = "Error looking up config at %s | Expected status code 200, got %s | error message: %s";
  private static final String ERROR_MESSAGE_INCORRECT_DATE_PARAMETER = "Invalid date value, the parameter must be in the format: yyyy-MM-dd";
  private static final String ERROR_SENDING_EMAIL = "Error in the 'mod-email' module, the module didn't send email | message: %s";
  private static final String SUCCESS_SEND_EMAIL = "The message has been delivered to %s";

  protected static final Logger log = LogManager.getLogger(AbstractEmail.class);
  protected final Vertx vertx;
  private final String tenantId;
  private final WebClientOptions webClientOptions;

  private MailService mailService;
  private StorageService storageService;
  private SmtpConfigurationService smtpConfigurationService;


  public AbstractEmail(Vertx vertx, String tenantId) {
    this.vertx = vertx;
    this.tenantId = tenantId;

    final int lookupTimeout = Integer.parseInt(
      MODULE_SPECIFIC_ARGS.getOrDefault(LOOKUP_TIMEOUT, LOOKUP_TIMEOUT_VAL));

    this.webClientOptions = new WebClientOptions();
    this.webClientOptions.setConnectTimeout(lookupTimeout);
    this.webClientOptions.setIdleTimeout(lookupTimeout);

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

  protected Future<EmailEntity> processEmail(EmailEntity email,
    Map<String, String> okapiHeaders) {

    log.debug("processEmail:: parameters email: {}, requestHeaders={}", () -> emailAsJson(email),
      () -> headersAsString(okapiHeaders));

    return processEmails(singletonList(email), okapiHeaders)
      .map(emails -> emails.stream().findFirst().orElseThrow())
      .onSuccess(result -> log.debug("processEmail:: result: {}", () -> emailAsJson(result)));
  }

  protected Future<Collection<EmailEntity>> processEmails(Collection<EmailEntity> emails,
    Map<String, String> okapiHeaders) {

    log.debug("processEmails:: emails: Collection<EmailEntity>(ids={}), okapiHeaders: {}",
      () -> emailIdsAsString(emails), () -> headersAsString(okapiHeaders));

    if (emails.isEmpty()) {
      log.info("processEmails:: emails is empty");
      return succeededFuture(emails);
    }
    log.debug("processEmails:: Trying to process a batch of {} emails", emails.size());

    return lookupSmtpConfiguration(okapiHeaders)
      .compose(config -> mapInOrder(emails, email -> processEmail(email, config)))
      .recover(t -> handleFailure(emails, t))
      .onSuccess(r -> log.debug("processEmails:: result: Collection<EmailEntity>(ids={})",
        () -> emailIdsAsString(r)));
  }

  protected Future<EmailEntity> processEmail(EmailEntity email,
    SmtpConfiguration smtpConfiguration) {

    log.debug("processEmail:: email: {}, smtpConfiguration: {}", () -> emailAsJson(email),
      () -> asJson(smtpConfiguration));

    applyConfiguration(email, smtpConfiguration);

    return sendEmail(email, smtpConfiguration)
      .map(this::handleSuccess)
      .otherwise(t -> handleFailure(email, t))
      .compose(this::saveEmail)
      .otherwiseEmpty()
      .onSuccess(result -> log.debug("processEmail:: result: {}", () -> emailAsJson(email)));
  }

  protected EmailEntity handleSuccess(EmailEntity email) {
    log.debug("handleSuccess:: parameters email: {}", () -> emailAsJson(email));
    String message = format(SUCCESS_SEND_EMAIL, join(",", email.getTo()));
    EmailEntity emailEntity = updateEmail(email, DELIVERED, message);
    log.debug("handleSuccess:: result: {}", () -> emailAsJson(email));
    return emailEntity;
  }

  protected EmailEntity handleFailure(EmailEntity email, Throwable throwable) {
    if (log.isDebugEnabled()) {
      log.debug("handleFailure:: email: {}", emailAsJson(email), throwable);
    }
    String errorMessage = format(ERROR_SENDING_EMAIL, throwable.getMessage());
    EmailEntity emailEntity = updateEmail(email, FAILURE, errorMessage);
    log.warn("handleFailure:: result: {}", () -> emailAsJson(emailEntity));
    return emailEntity;
  }

  private static EmailEntity updateEmail(EmailEntity email, Status status, String message) {
    log.debug("updateEmail:: parameters email: {}, status: {}, message: {}",
      () -> emailAsJson(email), () -> status, () -> message);
    int newAttemptCount = email.getAttemptCount() + 1;
    EmailEntity result = email
      .withStatus(status)
      .withMessage(message)
      .withDate(Date.from(ClockUtil.getZonedDateTime().toInstant()))
      .withAttemptCount(newAttemptCount)
      .withShouldRetry(status == FAILURE && newAttemptCount < RETRY_MAX_ATTEMPTS);
    log.debug("updateEmail:: result: {}", () -> emailAsJson(result));
    return result;
  }

  protected Future<Collection<EmailEntity>> handleFailure(Collection<EmailEntity> emails,
    Throwable throwable) {

    if (log.isWarnEnabled()) {
      log.warn("handleFailure:: Failed to process a batch of emails (ids={})",
        emailIdsAsString(emails), throwable);
    }

    return CompositeFuture.all(
        emails.stream()
          .map(email -> handleFailure(email, throwable))
          .map(this::saveEmail)
          .collect(toList()))
      .compose(r -> failedFuture(throwable));
  }

  private Future<SmtpConfiguration> lookupSmtpConfiguration(Map<String, String> requestHeaders) {
    log.debug("lookupSmtpConfiguration:: parameters requestHeaders: {}",
      () -> headersAsString(requestHeaders));
    return smtpConfigurationService.getSmtpConfiguration()
      .compose(EmailUtils::validateSmtpConfiguration)
      .recover(throwable -> moveConfigsFromModConfigurationToLocalDb(requestHeaders))
      .onSuccess(result -> log.debug("lookupSmtpConfiguration:: result: {}",
        () -> smtpConfigAsJson(result)));
  }

  private Future<SmtpConfiguration> moveConfigsFromModConfigurationToLocalDb(
    Map<String, String> requestHeaders) {

    log.debug("moveConfigsFromModConfigurationToLocalDb:: requestHeaders: {}",
      () -> headersAsString(requestHeaders));
    OkapiClient okapiClient = new OkapiClient(vertx, requestHeaders, webClientOptions);

    return fetchSmtpConfigurationFromModConfig(okapiClient)
      .compose(configs -> copyConfigurationAndDeleteFromModConfig(configs, okapiClient))
      .onSuccess(result -> log.debug("moveConfigsFromModConfigurationToLocalDb:: result: {}",
        () -> smtpConfigAsJson(result)));
  }

  private Future<Configurations> fetchSmtpConfigurationFromModConfig(OkapiClient okapiClient) {
    log.info("fetchSmtpConfigurationFromModConfig:: Failed to find SMTP configuration in the DB, " +
      "fetching from mod-config");

    String path = format(GET_CONFIG_PATH_TEMPLATE, CONFIG_BASE_PATH, MODULE_EMAIL_SMTP_SERVER);

    return okapiClient.getAbs(path)
      .send()
      .compose(response -> {
        if (response.statusCode() == HTTP_OK.toInt()) {
          log.info("fetchSmtpConfigurationFromModConfig:: Successfully fetched configuration " +
            "entries");
          Configurations config = response.bodyAsJsonObject().mapTo(Configurations.class);
          return succeededFuture(config);
        }
        String errorMessage = String.format(ERROR_LOOKING_UP_MOD_CONFIG,
          path, response.statusCode(), response.bodyAsString());
        log.warn("fetchSmtpConfigurationFromModConfig:: Failed to fetch SMTP configuration " +
          "entries: {}", errorMessage);
        return failedFuture(new ConfigurationException(errorMessage));
      });
  }

  private Future<SmtpConfiguration> copyConfigurationAndDeleteFromModConfig(
    Configurations configurations, OkapiClient okapiClient) {

    log.debug("copyConfigurationAndDeleteFromModConfig:: configurations: " +
      "Configurations(totalRecords={})", configurations::getTotalRecords);

    return succeededFuture(configurations)
      .map(EmailUtils::convertSmtpConfiguration)
      .compose(EmailUtils::validateSmtpConfiguration)
      .compose(smtpConfigurationService::createSmtpConfiguration)
      .onSuccess(smtpConfig -> deleteEntriesFromModConfig(configurations, okapiClient))
      .onSuccess(result -> log.debug("copyConfigurationAndDeleteFromModConfig:: result: {}",
        smtpConfigAsJson(result)));
  }

  private void deleteEntriesFromModConfig(Configurations configurationsToDelete,
    OkapiClient okapiClient) {

    log.debug("deleteEntriesFromModConfig:: configurations: Configurations(totalRecords={})",
      configurationsToDelete::getTotalRecords);

    configurationsToDelete.getConfigs().stream()
      .map(Config::getId)
      .forEach(id -> {
        log.debug("deleteEntriesFromModConfig:: Deleting configuration entry {}", id);
        String path = format(DELETE_CONFIG_PATH_TEMPLATE, CONFIG_BASE_PATH, id);
        okapiClient.deleteAbs(path)
          .send()
          .onSuccess(response -> {
            if (response.statusCode() == HTTP_NO_CONTENT.toInt()) {
              log.debug("deleteEntriesFromModConfig:: Successfully deleted configuration entry {}",
                id);
              return;
            }
            log.warn("deleteEntriesFromModConfig:: Failed to delete configuration entry {}", id);
          })
          .onFailure(log::error);
      });
  }

  protected Future<EmailEntity> sendEmail(EmailEntity email, SmtpConfiguration smtpConfiguration) {
    log.debug("sendEmail:: email: {}, smtpConfiguration: {}", () -> emailAsJson(email),
      () -> smtpConfigAsJson(smtpConfiguration));
    Promise<JsonObject> promise = Promise.promise();
    mailService.sendEmail(mapFrom(smtpConfiguration), mapFrom(email), promise);

    return promise.future()
      .map(email)
      .onSuccess(result -> log.debug("sendEmail:: result: {}", () -> emailAsJson(result)));
  }

  protected Future<EmailEntity> saveEmail(EmailEntity email) {
    log.debug("saveEmail:: parameters email: {}", () -> asJson(email));
    Promise<JsonObject> promise = Promise.promise();
    storageService.saveEmailEntity(tenantId, JsonObject.mapFrom(email), promise);

    return promise.future()
      .map(email)
      .onSuccess(result -> log.debug("saveEmail:: result: {}", () -> emailAsJson(result)));
  }

  protected Future<EmailEntries> findEmailEntries(int limit, int offset, String query) {
    log.debug("findEmailEntries:: parameters limit: {}, offset: {}, query: {}", limit, offset,
      query);
    Promise<JsonObject> promise = Promise.promise();
    storageService.findEmailEntries(tenantId, limit, offset, query, promise);

    return promise.future()
      .map(json -> json.mapTo(EmailEntries.class))
      .onSuccess(result -> log.debug("findEmailEntries:: result: {}",
        () -> emailIdsAsString(result)));
  }

  protected Future<Void> deleteEmailsByExpirationDate(String expirationDate, String emailStatus) {
    log.debug("deleteEmailsByExpirationDate:: parameters expirationDate: {}, emailStatus: {}", expirationDate, emailStatus);
    Promise<Void> promise = Promise.promise();
    storageService.deleteEmailEntriesByExpirationDateAndStatus(tenantId, expirationDate, emailStatus,
      result -> {
        if (result.failed()) {
          log.warn("deleteEmailsByExpirationDate:: Failed to delete emails with expiration date {}",
            expirationDate, result.cause());
          promise.fail(result.cause());
          return;
        }
        log.info("deleteEmailsByExpirationDate:: Successfully deleted emails with expiration date {}",
          expirationDate);
        promise.complete();
      });
    return promise.future();
  }

  protected Future<String> determinateEmailStatus(String emailStatus) {
    log.debug("determinateEmailStatus:: parameters emailStatus: {}", emailStatus);
    Promise<String> promise = Promise.promise();
    String status = StringUtils.isBlank(emailStatus)
      ? DELIVERED.value()
      : findStatusByName(emailStatus);
    promise.complete(status);
    log.info("determinateEmailStatus:: Successfully determinated email status {}", status);
    return promise.future();
  }

  protected Future<Void> checkExpirationDate(String expirationDate) {
    log.debug("checkExpirationDate:: parameters expirationDate: {}", expirationDate);
    Promise<Void> promise = Promise.promise();
    if (StringUtils.isBlank(expirationDate) || isCorrectDateFormat(expirationDate)) {
      promise.complete();
    } else {
      log.warn("checkExpirationDate:: Failed to check expiration date {}", expirationDate);
      promise.fail(new IllegalArgumentException(ERROR_MESSAGE_INCORRECT_DATE_PARAMETER));
    }
    return promise.future();
  }

  protected Response mapExceptionToResponse(Throwable t) {
    log.debug("mapExceptionToResponse:: throwable: ", t);
    String errMsg = t.getMessage();

    if (t.getClass() == ConfigurationException.class) {
      log.warn("mapExceptionToResponse:: exception class is {}", t.getClass());
      return Response.status(400)
        .header(CONTENT_TYPE, TEXT_PLAIN)
        .entity(errMsg)
        .build();
    }

    if (t.getClass() == SmtpConfigurationException.class) {
      log.warn("mapExceptionToResponse:: exception class is {}, responding with 200", t.getClass());
      return Response.status(200)
        .header(CONTENT_TYPE, TEXT_PLAIN)
        .entity(errMsg)
        .build();
    }

    log.warn("mapExceptionToResponse:: responding with 500");
    return Response.status(500)
      .header(CONTENT_TYPE, TEXT_PLAIN)
      .entity(Response.Status.INTERNAL_SERVER_ERROR.getReasonPhrase())
      .build();
  }

  private boolean isCorrectDateFormat(String expirationDate) {
    return DATE_PATTERN.matcher(expirationDate).matches();
  }

  private static void applyConfiguration(EmailEntity email, SmtpConfiguration smtpConfiguration) {
    log.debug("applyConfiguration:: email: {}, smtpConfiguration: {}", () -> emailAsJson(email),
      () -> smtpConfigAsJson(smtpConfiguration));
    if (StringUtils.isBlank(email.getFrom())) {
      log.debug("applyConfiguration:: 'from' field is blank, copying it from SMTP configuration");
      email.withFrom(smtpConfiguration.getFrom());
    }
  }

}
