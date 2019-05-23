package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.enums.SmtpEmail;
import org.folio.rest.impl.base.AbstractEmail;
import org.folio.rest.jaxrs.model.Config;
import org.folio.rest.jaxrs.model.Configurations;
import org.folio.rest.jaxrs.model.EmailEntries;
import org.folio.rest.jaxrs.resource.BatchEmails;
import org.folio.services.email.MailService;
import org.folio.services.storage.StorageService;

import javax.ws.rs.core.Response;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.folio.util.EmailUtils.*;

public class BatchEmailsAPI extends AbstractEmail implements BatchEmails {

  public BatchEmailsAPI(Vertx vertx, String tenantId) {
    super(vertx, tenantId);
  }

  @Override
  public void postBatchEmails(EmailEntries entries, Map<String, String> headers,
                              Handler<AsyncResult<Response>> resultHandler, Context context) {
    try {
      MultiMap caseInsensitiveHeaders = new CaseInsensitiveHeaders().addAll(headers);
      lookupConfig(caseInsensitiveHeaders).setHandler(lookupConfigHandler -> {
        if (lookupConfigHandler.failed()) {
          String errorMessage = lookupConfigHandler.cause().getMessage();
          logger.error(errorMessage);
          resultHandler.handle(Future.succeededFuture(
            PostBatchEmailsResponse.respond400WithTextPlain(errorMessage)));
          return;
        }
        Configurations configurations = lookupConfigHandler.result().mapTo(Configurations.class);
        if (checkMinConfigSmtpServer(configurations)) {
          String errorMessage = String.format(ERROR_MIN_REQUIREMENT_MOD_CONFIG, REQUIREMENTS_CONFIG_SET);
          logger.error(errorMessage);
          resultHandler.handle(Future.succeededFuture(
            PostBatchEmailsResponse.respond500WithTextPlain(errorMessage)));
          return;
        }

        MailService mailService = MailService.createProxy(vertx, MAIL_SERVICE_ADDRESS);
        mailService.createOrUpdateConfiguration(JsonObject.mapFrom(configurations));

        if (entries.getEmailEntity().isEmpty()) {
          resultHandler.handle(Future.succeededFuture(PostBatchEmailsResponse.respond204()));
          return;
        }

        JsonObject emailEntityArray = fillEntriesAndConvertToJson(entries, configurations);
        StorageService storageService = StorageService.createProxy(vertx, STORAGE_SERVICE_ADDRESS);
        storageService.saveEmailEntries(tenantId, emailEntityArray, result -> {
          if (result.failed()) {
            String errorMessage = result.cause().getMessage();
            resultHandler.handle(Future.succeededFuture(
              PostBatchEmailsResponse.respond400WithTextPlain(errorMessage)
            ));
            return;
          }
          resultHandler.handle(Future.succeededFuture(PostBatchEmailsResponse.respond204()));
        });
      });
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
      resultHandler.handle(Future.succeededFuture(PostBatchEmailsResponse.respond500WithTextPlain(ex)));
    }
  }

  @Override
  public void getBatchEmails(String query, int offset, int limit, String lang, Map<String, String> headers,
                             Handler<AsyncResult<Response>> resultHandler, Context context) {
    try {
      StorageService storageService = StorageService.createProxy(vertx, STORAGE_SERVICE_ADDRESS);
      storageService.findAllEmailEntries(tenantId, limit, offset, query, serviceHandler -> {
        if (serviceHandler.failed()) {
          String errorMessage = serviceHandler.cause().getMessage();
          logger.error(errorMessage);
          resultHandler.handle(Future.succeededFuture(GetBatchEmailsResponse.respond500WithTextPlain(errorMessage)));
          return;
        }
        EmailEntries emailEntries = serviceHandler.result().mapTo(EmailEntries.class);
        resultHandler.handle(Future.succeededFuture(GetBatchEmailsResponse.respond200WithApplicationJson(emailEntries)));
      });
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
      resultHandler.handle(Future.succeededFuture(GetBatchEmailsResponse.respond500WithTextPlain(ex)));
    }
  }

  private JsonObject fillEntriesAndConvertToJson(EmailEntries entries, Configurations configurations) {
    String senderAddress = getSenderFromConfigurations(configurations);
    entries.getEmailEntity().forEach(entity ->
    {
      String id = UUID.randomUUID().toString();
      entity.setId(id);

      Timestamp now = Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC));
      entity.setDate(now);

      if (StringUtils.isBlank(entity.getFrom())) {
        entity.setFrom(senderAddress);
      }
    });
    return JsonObject.mapFrom(entries
      .withTotalRecords(entries.getEmailEntity().size()));
  }

  private String getSenderFromConfigurations(Configurations configurations) {
    return configurations.getConfigs().stream()
      .filter(SmtpEmail::isContainsEmailFrom)
      .map(Config::getValue)
      .findFirst()
      .orElse(StringUtils.EMPTY);
  }
}
