package org.folio.services.email.impl;

import static io.vertx.core.Future.failedFuture;
import static java.lang.String.format;
import static java.lang.System.currentTimeMillis;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.isNoneBlank;
import static org.folio.rest.impl.base.AbstractEmail.RETRY_MAX_ATTEMPTS;
import static org.folio.util.EmailUtils.getMessageConfig;
import static org.folio.util.LogUtil.asJson;

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
import org.folio.rest.jaxrs.model.EmailEntity;
import org.folio.rest.jaxrs.model.EmailHeader;
import org.folio.rest.jaxrs.model.SmtpConfiguration;
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

  private final Vertx vertx;

  private MailClient client = null;
  private MailConfig config = null;

  public MailServiceImpl(Vertx vertx) {
    this.vertx = vertx;
  }

  @Override
  public void sendEmail(JsonObject smtpConfigurationJson, JsonObject emailJson,
    Handler<AsyncResult<JsonObject>> resultHandler) {

    logger.debug("sendEmail:: ");
    SmtpConfiguration smtpConfiguration = smtpConfigurationJson.mapTo(SmtpConfiguration.class);

    try {
      EmailEntity emailEntity = emailJson.mapTo(EmailEntity.class);
      MailConfig mailConfig = getMailConfig(smtpConfiguration);
      MailMessage mailMessage = getMailMessage(emailEntity, smtpConfiguration);
      String emailId = emailEntity.getId();
      long start = currentTimeMillis();

      logger.info("sendEmail:: Sending email {}: attempt {}/{}",
        emailId, emailEntity.getAttemptCount() + 1, RETRY_MAX_ATTEMPTS);

      defineMailClient(mailConfig)
        .sendMail(mailMessage)
        .onSuccess(r -> logger.info("sendEmail:: Email {} sent in {} ms", emailId, currentTimeMillis() - start))
        .onFailure(t -> logger.error("sendEmail:: Failed to send email {}: {}", emailId, t.getMessage()))
        .map(emailJson)
        .onComplete(resultHandler);
    } catch (Exception ex) {
      logger.warn("sendEmail:: {}", format(ERROR_SENDING_EMAIL, ex));
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

  private MailConfig getMailConfig(SmtpConfiguration smtpConfiguration) {
    boolean ssl = ofNullable(smtpConfiguration.getSsl()).orElse(false);

    StartTLSOptions startTLSOptions = StartTLSOptions.valueOf(
      ofNullable(smtpConfiguration.getStartTlsOptions())
        .orElse(SmtpConfiguration.StartTlsOptions.OPTIONAL)
        .value());

    boolean trustAll = ofNullable(smtpConfiguration.getTrustAll()).orElse(false);

    LoginOption loginOption = LoginOption.valueOf(
      ofNullable(smtpConfiguration.getLoginOption())
        .orElse(SmtpConfiguration.LoginOption.NONE)
        .value());

    String authMethods = ofNullable(smtpConfiguration.getAuthMethods()).orElse(StringUtils.EMPTY);

    return new MailConfig()
      .setHostname(smtpConfiguration.getHost())
      .setPort(smtpConfiguration.getPort())
      .setUsername(smtpConfiguration.getUsername())
      .setPassword(smtpConfiguration.getPassword())
      .setSsl(ssl)
      .setTrustAll(trustAll)
      .setLogin(loginOption)
      .setStarttls(startTLSOptions)
      .setAuthMethods(authMethods);
  }

  private MailMessage getMailMessage(EmailEntity emailEntity, SmtpConfiguration smtpConfiguration) {
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

    addHeadersFromConfiguration(mailMessage, smtpConfiguration);

    return mailMessage;
  }

  private List<MailAttachment> getMailAttachments(List<Attachment> attachments) {
    return attachments.stream()
      .map(this::getMailAttachment)
      .collect(Collectors.toList());
  }

  private MailAttachment getMailAttachment(Attachment data) {
    if (Objects.isNull(data) || StringUtils.isEmpty(data.getData())) {
      logger.warn("getMailAttachment:: {}", INCORRECT_ATTACHMENT_DATA);
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
      logger.warn("getAttachmentData:: {}", ERROR_ATTACHMENT_DATA);
      return Buffer.buffer();
    }
    // Decode incoming data from JSON
    byte[] decode = Base64.getDecoder().decode(file);
    return Buffer.buffer(decode);
  }

  public static void addHeadersFromConfiguration(MailMessage message, SmtpConfiguration smtpConfiguration) {
    logger.debug("addHeadersFromConfiguration:: parameters: message: {}, smtpConfiguration: {}",
      () -> asJson(message), () -> asJson(smtpConfiguration));
    Map<String, String> headers = smtpConfiguration.getEmailHeaders().stream()
      .filter(header -> isNoneBlank(header.getName(), header.getValue()))
      .collect(toMap(EmailHeader::getName, EmailHeader::getValue));

    if (headers.isEmpty()) {
      logger.warn("addHeadersFromConfiguration:: No headers were found in configuration");
      return;
    }

    if (message.getHeaders() == null) {
      message.setHeaders(new HeadersMultiMap());
    }

    message.getHeaders().addAll(headers);
  }
}
