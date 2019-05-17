package org.folio.services.email;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.mail.LoginOption;
import io.vertx.ext.mail.MailAttachment;
import io.vertx.ext.mail.MailClient;
import io.vertx.ext.mail.MailConfig;
import io.vertx.ext.mail.MailMessage;
import io.vertx.ext.mail.MailResult;
import io.vertx.ext.mail.StartTLSOptions;
import org.apache.commons.lang3.StringUtils;
import org.folio.enums.SmtpEmail;
import org.folio.rest.jaxrs.model.Attachment;
import org.folio.rest.jaxrs.model.Configurations;
import org.folio.rest.jaxrs.model.EmailEntity;

import javax.ws.rs.core.MediaType;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.folio.enums.SmtpEmail.EMAIL_SMTP_HOST;
import static org.folio.enums.SmtpEmail.EMAIL_SMTP_PORT;
import static org.folio.util.EmailUtils.*;

public class AbstractMailService {

  protected static final Logger logger = LoggerFactory.getLogger(AbstractMailService.class);

  protected static final String ERROR_SENDING_EMAIL = "Error in the 'mod-email' module, the module didn't send email | message: %s";
  protected static final String ERROR_UPDATING_MAIL_CLIENT_CONFIG = "Error creating and updating the MailClient configuration | message: %s";
  protected static final String SUCCESS_SEND_BATCH_EMAILS = "All messages have been delivered";
  private static final String ERROR_ATTACHMENT_DATA = "Error attaching the `%s` file to email!";
  private static final String INCORRECT_ATTACHMENT_DATA = "No data attachment!";
  private static final String SUCCESS_SEND_EMAIL = "The message has been delivered to %s";

  private final Vertx vertx;

  protected MailClient client = null;
  private MailConfig config = null;

  public AbstractMailService(Vertx vertx) {
    this.vertx = vertx;
  }

  protected MailClient defineMailClient(MailConfig mailConfig) {
    return (Objects.isNull(config) || !config.equals(mailConfig))
      ? createMailClient(mailConfig)
      : client;
  }

  private MailClient createMailClient(MailConfig mailConfig) {
    if (client != null) {
      logger.debug("The MailClient has been closed");
      client.close();
    }

    config = mailConfig;
    client = MailClient.createShared(vertx, mailConfig);
    logger.debug("A new MailClient has been created");
    return client;
  }

  protected MailConfig createMailConfig(Configurations configurations) {
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

  protected MailMessage buildMailMessage(EmailEntity emailEntity, Configurations configurations) {
    String from = emailEntity.getFrom();
    if (StringUtils.isBlank(from)) {
      emailEntity.setFrom(getEmailConfig(configurations, SmtpEmail.EMAIL_FROM, String.class));
    }
    return buildMailMessage(emailEntity);
  }

  protected MailMessage buildMailMessage(EmailEntity emailEntity) {
    MailMessage mailMessage = new MailMessage();
    String outputFormat = emailEntity.getOutputFormat();
    if (StringUtils.isNoneBlank(outputFormat) && outputFormat.trim().equalsIgnoreCase(MediaType.TEXT_HTML)) {
      mailMessage.setHtml(getMessageConfig(emailEntity.getBody()));
    } else {
      mailMessage.setText(getMessageConfig(emailEntity.getBody()));
    }
    return mailMessage
      .setFrom(getMessageConfig(emailEntity.getFrom()))
      .setTo(getMessageConfig(emailEntity.getTo()))
      .setSubject(getMessageConfig(emailEntity.getHeader()))
      .setAttachment(fillMailAttachments(emailEntity.getAttachments()));
  }

  private List<MailAttachment> fillMailAttachments(List<Attachment> attachments) {
    return attachments.stream()
      .map(this::fillMailAttachment)
      .collect(Collectors.toList());
  }

  private MailAttachment fillMailAttachment(Attachment data) {
    if (Objects.isNull(data) || StringUtils.isEmpty(data.getData())) {
      logger.error(INCORRECT_ATTACHMENT_DATA);
      return new MailAttachment().setData(Buffer.buffer());
    }
    return new MailAttachment()
      .setContentType(data.getContentType())
      .setName(data.getName())
      .setDescription(data.getDescription())
      .setDisposition(data.getDisposition())
      .setContentId(data.getContentId())
      .setData(fillAttachmentData(data));
  }

  private Buffer fillAttachmentData(Attachment data) {
    String file = data.getData();
    if (StringUtils.isEmpty(file)) {
      logger.error(String.format(ERROR_ATTACHMENT_DATA, data.getName()));
      return Buffer.buffer();
    }
    // Decode incoming data from JSON
    byte[] decode = Base64.getDecoder().decode(file);
    return Buffer.buffer(decode);
  }

  protected JsonObject createSuccessMessage(AsyncResult<MailResult> mailHandler) {
    String message = String.format(SUCCESS_SEND_EMAIL, String.join(",", mailHandler.result().getRecipients()));
    return new JsonObject().put(MESSAGE_RESULT, message);
  }

  protected Future sendMessage(MailClient mailClient, MailMessage mailMessage) {
    Future future = Future.future();
    mailClient.sendMail(mailMessage, mailHandler -> {
      if (mailHandler.failed()) {
        logger.error(String.format(ERROR_SENDING_EMAIL, mailHandler.cause().getMessage()));
        future.fail(mailHandler.cause());
        return;
      }
      future.complete();
    });
    return future;
  }
}
