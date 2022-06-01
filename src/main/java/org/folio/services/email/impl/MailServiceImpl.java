package org.folio.services.email.impl;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.lang.String.format;
import static java.lang.String.join;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.isNoneBlank;
import static org.folio.enums.SmtpEmail.AUTH_METHODS;
import static org.folio.enums.SmtpEmail.EMAIL_PASSWORD;
import static org.folio.enums.SmtpEmail.EMAIL_SMTP_HOST;
import static org.folio.enums.SmtpEmail.EMAIL_SMTP_LOGIN_OPTION;
import static org.folio.enums.SmtpEmail.EMAIL_SMTP_PORT;
import static org.folio.enums.SmtpEmail.EMAIL_SMTP_SSL;
import static org.folio.enums.SmtpEmail.EMAIL_START_TLS_OPTIONS;
import static org.folio.enums.SmtpEmail.EMAIL_TRUST_ALL;
import static org.folio.enums.SmtpEmail.EMAIL_USERNAME;
import static org.folio.rest.jaxrs.model.EmailEntity.Status.DELIVERED;
import static org.folio.rest.jaxrs.model.EmailEntity.Status.FAILURE;
import static org.folio.util.EmailUtils.getEmailConfig;
import static org.folio.util.EmailUtils.getMessageConfig;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.enums.SmtpEmail;
import org.folio.rest.jaxrs.model.Attachment;
import org.folio.rest.jaxrs.model.Config;
import org.folio.rest.jaxrs.model.Configurations;
import org.folio.rest.jaxrs.model.EmailEntity;
import org.folio.rest.jaxrs.model.EmailEntity.Status;
import org.folio.services.email.MailService;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mail.LoginOption;
import io.vertx.ext.mail.MailAttachment;
import io.vertx.ext.mail.MailClient;
import io.vertx.ext.mail.MailConfig;
import io.vertx.ext.mail.MailMessage;
import io.vertx.ext.mail.SMTPException;
import io.vertx.ext.mail.StartTLSOptions;

public class MailServiceImpl implements MailService {

  private static final Logger logger = LogManager.getLogger(MailServiceImpl.class);
  private static final String ERROR_SENDING_EMAIL = "Error in the 'mod-email' module, the module didn't send email | message: %s";
  private static final String ERROR_ATTACHMENT_DATA = "Error attaching the `%s` file to email!";
  private static final String INCORRECT_ATTACHMENT_DATA = "No data attachment!";
  private static final String SUCCESS_SEND_EMAIL = "The message has been delivered to %s";
  private static final String EMAIL_HEADERS_CONFIG_NAME = "email.headers";
  private static final int MAX_ATTEMPTS = 10;

  private final Vertx vertx;

  private MailClient client = null;
  private MailConfig config = null;

  public MailServiceImpl(Vertx vertx) {
    this.vertx = vertx;
  }

  @Override
  public void sendEmail(JsonObject config, JsonObject emailEntityJson,
    Handler<AsyncResult<JsonObject>> resultHandler) {

    try {
      Configurations configurations = config.mapTo(Configurations.class);
      EmailEntity email = buildEmailEntity(emailEntityJson, configurations);
      MailConfig mailConfig = getMailConfig(configurations);
      MailMessage mailMessage = getMailMessage(email, configurations);

      logger.info("Sending email {} (attempt {}/{})",
        email.getId(), email.getAttemptsCount(), MAX_ATTEMPTS);

      defineMailClient(mailConfig)
        .sendMail(mailMessage)
        .onSuccess(result -> {
          String message = format(SUCCESS_SEND_EMAIL, join(",", result.getRecipients()));
          logger.info(message);
          resultHandler.handle(succeededFuture(buildResult(email, DELIVERED, message, false)));
        })
        .onFailure(cause -> {
          boolean shouldRetry = shouldRetry(cause, email);
          String message = format(ERROR_SENDING_EMAIL, cause.getMessage());
          logger.error(message, cause);
          resultHandler.handle(succeededFuture(buildResult(email, FAILURE, message, shouldRetry)));
        });
    } catch (Exception ex) {
      logger.error(format(ERROR_SENDING_EMAIL, ex.getMessage()));
      resultHandler.handle(failedFuture(ex.getMessage()));
    }
  }

  private static boolean shouldRetry(Throwable error, EmailEntity email) {
    if (error instanceof SMTPException) {
      int replyCode = ((SMTPException) error).getReplyCode();
      return replyCode >= 400 && replyCode < 500 && email.getAttemptsCount() < MAX_ATTEMPTS;
    }
    return false;
  }

  private EmailEntity buildEmailEntity(JsonObject emailEntityJson, Configurations configurations) {
    EmailEntity emailEntity = emailEntityJson
      .mapTo(EmailEntity.class);

    if (StringUtils.isBlank(emailEntity.getFrom())) {
      emailEntity.withFrom(getEmailConfig(configurations, SmtpEmail.EMAIL_FROM, String.class));
    }

    return emailEntity.withDate(Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC)))
      .withAttemptsCount(emailEntity.getAttemptsCount() + 1);
  }

  private MailClient defineMailClient(MailConfig mailConfig) {
    if (Objects.isNull(config) || !config.equals(mailConfig)) {
      config = mailConfig;
      client = MailClient.create(vertx, mailConfig);
    }
    return client;
  }

  private MailConfig getMailConfig(Configurations configurations) {
    return new MailConfig()
      .setAuthMethods(getEmailConfig(configurations, AUTH_METHODS, String.class))
      .setHostname(getEmailConfig(configurations, EMAIL_SMTP_HOST, String.class))
      .setPort(getEmailConfig(configurations, EMAIL_SMTP_PORT, Integer.class))
      .setSsl(getEmailConfig(configurations, EMAIL_SMTP_SSL, Boolean.class))
      .setStarttls(getEmailConfig(configurations, EMAIL_START_TLS_OPTIONS, StartTLSOptions.class))
      .setTrustAll(getEmailConfig(configurations, EMAIL_TRUST_ALL, Boolean.class))
      .setLogin(getEmailConfig(configurations, EMAIL_SMTP_LOGIN_OPTION, LoginOption.class))
      .setUsername(getEmailConfig(configurations, EMAIL_USERNAME, String.class))
      .setPassword(getEmailConfig(configurations, EMAIL_PASSWORD, String.class));
  }

  private MailMessage getMailMessage(EmailEntity emailEntity, Configurations configurations) {
    MailMessage mailMessage = new MailMessage()
      .setFrom(getMessageConfig(emailEntity.getFrom()))
      .setTo(getMessageConfig(emailEntity.getTo()))
      .setSubject(getMessageConfig(emailEntity.getHeader()))
      .setAttachment(getMailAttachments(emailEntity.getAttachments()));

    String outputFormat = emailEntity.getOutputFormat();
    if (StringUtils.isNoneBlank(outputFormat) && outputFormat.trim().equalsIgnoreCase(MediaType.TEXT_HTML)) {
      mailMessage.setHtml(getMessageConfig(emailEntity.getBody()));
    } else {
      mailMessage.setText(getMessageConfig(emailEntity.getBody()));
    }

    addHeadersFromConfiguration(mailMessage, configurations);

    return mailMessage;
  }

  private List<MailAttachment> getMailAttachments(List<Attachment> attachments) {
    return attachments.stream()
      .map(this::getMailAttachment)
      .collect(Collectors.toList());
  }

  private MailAttachment getMailAttachment(Attachment data) {
    if (Objects.isNull(data) || StringUtils.isEmpty(data.getData())) {
      logger.error(INCORRECT_ATTACHMENT_DATA);
      return MailAttachment.create().setData(Buffer.buffer());
    }
    return MailAttachment.create()
      .setContentType(data.getContentType())
      .setName(data.getName())
      .setDescription(data.getDescription())
      .setDisposition(data.getDisposition())
      .setContentId(data.getContentId())
      .setData(getAttachmentData(data));
  }

  private Buffer getAttachmentData(Attachment data) {
    String file = data.getData();
    if (StringUtils.isEmpty(file)) {
      logger.error(format(ERROR_ATTACHMENT_DATA, data.getName()));
      return Buffer.buffer();
    }
    // Decode incoming data from JSON
    byte[] decode = Base64.getDecoder().decode(file);
    return Buffer.buffer(decode);
  }

  private static JsonObject buildResult(EmailEntity emailEntity, Status status,
    String message, boolean shouldRetry) {

    return JsonObject.mapFrom(emailEntity
      .withStatus(status)
      .withMessage(message)
      .withShouldRetry(shouldRetry)
    );
  }

  public static void addHeadersFromConfiguration(MailMessage message, Configurations configurations) {
    Map<String, String> headers = configurations.getConfigs().stream()
      .filter(config -> EMAIL_HEADERS_CONFIG_NAME.equals(config.getConfigName()))
      .filter(config -> isNoneBlank(config.getCode(), config.getValue()))
      .collect(toMap(Config::getCode, Config::getValue));

    if (headers.isEmpty()) {
      return;
    }

    if (message.getHeaders() == null) {
      message.setHeaders(new HeadersMultiMap());
    }

    message.getHeaders().addAll(headers);
  }
}
