package org.folio.rest.impl.base;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static io.vertx.core.json.JsonObject.mapFrom;
import static java.lang.String.format;
import static java.lang.String.join;
import static java.util.Collections.singletonList;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
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
import org.folio.rest.jaxrs.model.EmailEntity;
import org.folio.rest.jaxrs.model.EmailEntity.Status;
import org.folio.rest.jaxrs.model.EmailEntries;
import org.folio.rest.jaxrs.model.SmtpConfiguration;
import org.folio.rest.persist.PostgresClient;
import org.folio.services.MailSettingsService;
import org.folio.services.SmtpConfigurationProvider;
import org.folio.services.email.MailService;
import org.folio.services.storage.StorageService;
import org.folio.util.ClockUtil;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public abstract class AbstractEmail {

  public static final int RETRY_MAX_ATTEMPTS = 3;

  private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
  private static final String ERROR_MESSAGE_INCORRECT_DATE_PARAMETER = "Invalid date value, the parameter must be in the format: yyyy-MM-dd";
  private static final String ERROR_SENDING_EMAIL = "Error in the 'mod-email' module, the module didn't send email | message: %s";
  private static final String SUCCESS_SEND_EMAIL = "The message has been delivered to %s";

  protected static final Logger log = LogManager.getLogger(AbstractEmail.class);
  protected final Vertx vertx;
  private final String tenantId;

  private MailService mailService;
  private StorageService storageService;
  private SmtpConfigurationProvider smtpConfigurationProvider;


  public AbstractEmail(Vertx vertx, String tenantId) {
    this.vertx = vertx;
    this.tenantId = tenantId;

    initServices();
  }

  /**
   * Initialization of the email sending and storage service
   */
  private void initServices() {
    mailService = MailService.createProxy(vertx, MAIL_SERVICE_ADDRESS);
    storageService = StorageService.createProxy(vertx, STORAGE_SERVICE_ADDRESS);

    var mailSettingsService = new MailSettingsService();
    var postgresClient = PostgresClient.getInstance(vertx, tenantId);
    smtpConfigurationProvider = new SmtpConfigurationProvider(vertx, mailSettingsService, postgresClient);
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

    return smtpConfigurationProvider.lookup(okapiHeaders)
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
      .onFailure(t -> log.error("Failed to save email", t))
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

    return Future.all(
        emails.stream()
          .map(email -> handleFailure(email, throwable))
          .map(this::saveEmail)
          .toList())
      .compose(r -> failedFuture(throwable));
  }

  protected Future<EmailEntity> sendEmail(EmailEntity email, SmtpConfiguration smtpConfiguration) {
    log.debug("sendEmail:: email: {}, smtpConfiguration: {}", () -> emailAsJson(email),
      () -> smtpConfigAsJson(smtpConfiguration));

    return mailService.sendEmail(tenantId, mapFrom(smtpConfiguration), mapFrom(email))
      .map(email)
      .onSuccess(result -> log.debug("sendEmail:: result: {}", () -> emailAsJson(result)));
  }

  protected Future<EmailEntity> saveEmail(EmailEntity email) {
    log.debug("saveEmail:: parameters email: {}", () -> asJson(email));

    return storageService.saveEmailEntity(tenantId, JsonObject.mapFrom(email))
      .map(email)
      .onSuccess(result -> log.debug("saveEmail:: result: {}", () -> emailAsJson(result)));
  }

  protected Future<EmailEntries> findEmailEntries(int limit, int offset, String query) {
    log.debug("findEmailEntries:: parameters limit: {}, offset: {}, query: {}", limit, offset,
      query);

    return storageService.findEmailEntries(tenantId, limit, offset, query)
      .map(json -> json.mapTo(EmailEntries.class))
      .onSuccess(result -> log.debug("findEmailEntries:: result: {}",
        () -> emailIdsAsString(result)));
  }

  protected Future<Void> deleteEmailsByExpirationDate(String expirationDate, String emailStatus) {
    log.debug("deleteEmailsByExpirationDate:: parameters expirationDate: {}, emailStatus: {}", expirationDate, emailStatus);

    return storageService.deleteEmailEntriesByExpirationDateAndStatus(tenantId, expirationDate, emailStatus)
      .onSuccess(r -> log.info("deleteEmailsByExpirationDate:: Successfully deleted emails with expiration date {}", expirationDate))
      .onFailure(t -> log.warn("deleteEmailsByExpirationDate:: Failed to delete emails with expiration date {}", expirationDate, t))
      .mapEmpty();
  }

  protected Future<String> determinateEmailStatus(String emailStatus) {
    log.debug("determinateEmailStatus:: parameters emailStatus: {}", emailStatus);
    String status = StringUtils.isBlank(emailStatus)
      ? DELIVERED.value()
      : findStatusByName(emailStatus);
    log.info("determinateEmailStatus:: Successfully determinated email status {}", status);
    return succeededFuture(status);
  }

  protected Future<Void> checkExpirationDate(String expirationDate) {
    log.debug("checkExpirationDate:: parameters expirationDate: {}", expirationDate);
    if (StringUtils.isBlank(expirationDate) || isCorrectDateFormat(expirationDate)) {
      return Future.succeededFuture();
    } else {
      log.warn("checkExpirationDate:: Failed to check expiration date {}", expirationDate);
      return Future.failedFuture(new IllegalArgumentException(ERROR_MESSAGE_INCORRECT_DATE_PARAMETER));
    }
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
