package org.folio.services;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.exceptions.SmtpConfigurationNotFoundException;
import org.folio.rest.jaxrs.model.Setting;
import org.folio.rest.jaxrs.model.SmtpConfiguration;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.interfaces.Results;
import org.folio.util.EmailUtils;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.folio.util.LogUtil.asJson;
import static org.folio.util.LogUtil.smtpConfigAsJson;

public class MailSettingsService {

  private static final Logger log = LogManager.getLogger(MailSettingsService.class);
  public static final String SETTINGS_TABLE = "settings";
  public static final String SMTP_CONFIG_KEY = "smtp-configuration";

  public Future<SmtpConfiguration> getSmtpConfigSetting(Conn conn) {
    log.debug("getSmtpConfiguration::");
    var searchCriteria = new Criteria().addField("'key'").setOperation("=").setVal(SMTP_CONFIG_KEY);
    var criterion = new Criterion().addCriterion(searchCriteria).setLimit(new Limit(1));

    return conn
      .get(SETTINGS_TABLE, Setting.class, criterion)
      .compose(this::getFirstExceptionally)
      .map(Setting::getValue)
      .map(settingValue -> JsonObject.mapFrom(settingValue).mapTo(SmtpConfiguration.class))
      .compose(EmailUtils::validateSmtpConfiguration)
      .onSuccess(config -> log.debug("getSmtpConfiguration:: found entity: {}", () -> smtpConfigAsJson(config)))
      .onFailure(err -> log.debug("getSmtpConfiguration:: failed", err));
  }

  public Future<SmtpConfiguration> createSmtpConfigSetting(Conn conn, SmtpConfiguration config) {
    log.debug("createSmtpConfigSetting:: {}", () -> smtpConfigAsJson(config));
    var id = ObjectUtils.defaultIfNull(config.getId(), UUID.randomUUID().toString());
    var settingValue = JsonObject.mapFrom(config).mapTo(Map.class);
    settingValue.remove("id");

    var setting = new Setting()
      .withId(id)
      .withKey(SMTP_CONFIG_KEY)
      .withScope(Setting.Scope.MOD_EMAIL)
      .withValue(settingValue);

    return conn
      .save(SETTINGS_TABLE, id, setting)
      .onSuccess(entityId -> log.debug("createSmtpConfigSetting:: parameter id: {}", entityId))
      .compose(entityId -> getSmtpConfigSetting(conn));
  }

  private Future<Setting> getFirstExceptionally(Results<Setting> results) {
    return results.getResults().isEmpty()
      ? failedFuture(new SmtpConfigurationNotFoundException())
      : succeededFuture(results.getResults().getFirst());
  }
}
