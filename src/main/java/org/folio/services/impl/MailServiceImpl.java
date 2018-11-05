package org.folio.services.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.mail.*;
import org.apache.commons.lang3.StringUtils;
import org.folio.enums.SmtpEmail;
import org.folio.rest.jaxrs.model.Attachment;
import org.folio.rest.jaxrs.model.Configurations;
import org.folio.rest.jaxrs.model.EmailEntity;
import org.folio.services.MailService;

import javax.ws.rs.core.MediaType;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.folio.enums.SmtpEmail.EMAIL_SMTP_HOST;
import static org.folio.enums.SmtpEmail.EMAIL_SMTP_PORT;
import static org.folio.util.EmailUtils.*;

public class MailServiceImpl implements MailService {

  private static final String ERROR_SENDING_EMAIL = "Error in the 'mod-email' module, the module didn't send email | message: %s";
  private static final String ERROR_ATTACHMENT_DATA = "Error attaching the `%s` file to email!";
  private static final String INCORRECT_ATTACHMENT_DATA = "No data attachment!";
  private static final String SUCCESS_SEND_EMAIL = "The message has been delivered to %s";

  private final Logger logger = LoggerFactory.getLogger(MailServiceImpl.class);
  private final Vertx vertx;

  public MailServiceImpl(Vertx vertx) {
    this.vertx = vertx;
  }

  @Override
  public void sendEmail(JsonObject configJson, JsonObject emailEntityJson, Handler<AsyncResult<JsonObject>> resultHandler) {
    try {
      EmailEntity emailEntity = emailEntityJson.mapTo(EmailEntity.class);
      Configurations configurations = configJson.mapTo(Configurations.class);
      MailConfig mailConfig = getMailConfig(configurations);
      MailMessage mailMessage = getMailMessage(emailEntity);
      MailClient
        .createShared(vertx, mailConfig)
        .sendMail(mailMessage, mailHandler -> {
          if (mailHandler.failed()) {
            logger.error(String.format(ERROR_SENDING_EMAIL, mailHandler.cause().getMessage()));
            resultHandler.handle(Future.failedFuture(mailHandler.cause()));
            return;
          }
          // the logic of sending the result of sending email to `mod-notify`
          JsonObject message = createMessage(mailHandler);
          resultHandler.handle(Future.succeededFuture(message));
        });
    } catch (Exception ex) {
      logger.error(String.format(ERROR_SENDING_EMAIL, ex.getMessage()));
      resultHandler.handle(Future.failedFuture(ex.getMessage()));
    }
  }

  private MailConfig getMailConfig(Configurations configurations) {
    return new MailConfig()
      .setHostname(getEmailConfig(configurations, EMAIL_SMTP_HOST, String.class))
      .setPort(getEmailConfig(configurations, EMAIL_SMTP_PORT, Integer.class))
      .setSsl(getEmailConfig(configurations, SmtpEmail.EMAIL_SMTP_SSL, Boolean.class))
      .setStarttls(getEmailConfig(configurations, SmtpEmail.EMAIL_START_TLS_OPTIONS, StartTLSOptions.class))
      .setTrustAll(getEmailConfig(configurations, SmtpEmail.EMAIL_TRUST_ALL, Boolean.class))
      .setLogin(getEmailConfig(configurations, SmtpEmail.EMAIL_SMTP_LOGIN_OPTION, LoginOption.class))
      .setUsername(getEmailConfig(configurations, SmtpEmail.EMAIL_USERNAME, String.class))
      .setPassword(getEmailConfig(configurations, SmtpEmail.EMAIL_PASSWORD, String.class));
  }

  private MailMessage getMailMessage(EmailEntity emailEntity) {
    MailMessage mailMessage = new MailMessage()
      .setFrom(getMessageConfig(emailEntity.getFrom()))
      .setTo(getMessageConfig(emailEntity.getTo()))
      .setSubject(getMessageConfig(emailEntity.getHeader()))
      .setAttachment(getMailAttachments(emailEntity.getAttachments()));

    String outputFormat = emailEntity.getOutputFormat();
    if (StringUtils.isNoneBlank(outputFormat) && outputFormat.equalsIgnoreCase(MediaType.TEXT_HTML)) {
      mailMessage.setHtml(getMessageConfig(emailEntity.getBody()));
    } else {
      mailMessage.setText(getMessageConfig(emailEntity.getBody()));
    }
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
      return new MailAttachment();
    }
    return new MailAttachment()
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
      logger.error(String.format(ERROR_ATTACHMENT_DATA, data.getName()));
      return Buffer.buffer();
    }
    // Decode incoming data from JSON
    byte[] decode = Base64.getDecoder().decode(file);
    return Buffer.buffer(decode);
  }

  private JsonObject createMessage(AsyncResult<MailResult> mailHandler) {
    String message = String.format(SUCCESS_SEND_EMAIL, String.join(",", mailHandler.result().getRecipients()));
    return new JsonObject().put(MESSAGE_RESULT, message);
  }
}
