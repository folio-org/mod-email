package org.folio.services.email.impl;

import static io.vertx.core.Future.failedFuture;
import static java.lang.String.format;
import static java.lang.System.currentTimeMillis;
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
import static org.folio.rest.impl.base.AbstractEmail.RETRY_MAX_ATTEMPTS;
import static org.folio.util.EmailUtils.getEmailConfig;
import static org.folio.util.EmailUtils.getMessageConfig;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.Attachment;
import org.folio.rest.jaxrs.model.Config;
import org.folio.rest.jaxrs.model.Configurations;
import org.folio.rest.jaxrs.model.EmailEntity;
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
import io.vertx.ext.mail.StartTLSOptions;

public class MailServiceImpl implements MailService {

  private static final Logger logger = LogManager.getLogger(MailServiceImpl.class);
  private static final String ERROR_SENDING_EMAIL = "Error in the 'mod-email' module, the module didn't send email | message: %s";
  private static final String ERROR_ATTACHMENT_DATA = "Error attaching the `%s` file to email!";
  private static final String INCORRECT_ATTACHMENT_DATA = "No data attachment!";
  private static final String EMAIL_HEADERS_CONFIG_NAME = "email.headers";

  private final Vertx vertx;

  private MailClient client = null;
  private MailConfig config = null;

  public MailServiceImpl(Vertx vertx) {
    this.vertx = vertx;
  }

  @Override
  public void sendEmail(JsonObject configJson, JsonObject emailJson,
    Handler<AsyncResult<JsonObject>> resultHandler) {

    try {
      Configurations configurations = configJson.mapTo(Configurations.class);
      EmailEntity emailEntity = emailJson.mapTo(EmailEntity.class);
      MailConfig mailConfig = getMailConfig(configurations);
      MailMessage mailMessage = getMailMessage(emailEntity, configurations);
      String emailId = emailEntity.getId();
      long start = currentTimeMillis();

      logger.info("Sending email {}: attempt {}/{}",
        emailId, emailEntity.getAttemptCount() + 1, RETRY_MAX_ATTEMPTS);

      defineMailClient(mailConfig)
        .sendMail(mailMessage)
        .onSuccess(r -> logger.info("Email {} sent in {} ms", emailId, currentTimeMillis() - start))
        .onFailure(t -> logger.error("Failed to send email {}: {}", emailId, t.getMessage()))
        .map(emailJson)
        .onComplete(resultHandler);
    } catch (Exception ex) {
      logger.error(format(ERROR_SENDING_EMAIL, ex.getMessage()));
      resultHandler.handle(failedFuture(ex.getMessage()));
    }
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
