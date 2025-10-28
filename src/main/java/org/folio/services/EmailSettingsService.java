package org.folio.services;

import io.vertx.core.Future;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.exceptions.EmailSettingsNotFoundException;
import org.folio.rest.jaxrs.model.Setting;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.folio.util.LogUtil.asJson;

public class EmailSettingsService {
  private static final Logger log = LogManager.getLogger(EmailSettingsService.class);
  public static final String SETTINGS_TABLE_NAME = "settings";


  public Future<Setting> getEmailSettings(Conn conn) {
    log.debug("getEmailSettings:: starting to fetch email settings");

    Criterion criterion = new org.folio.rest.persist.Criteria.Criterion();
    criterion.setLimit(new Limit(1));

    return conn.get(SETTINGS_TABLE_NAME, Setting.class, criterion)
      .compose(results -> results.getResults().isEmpty()
        ? failedFuture(new EmailSettingsNotFoundException())
        : succeededFuture(results.getResults().getFirst()))
      .onSuccess(result -> log.debug("getEmailSettings:: result: {}",
        () -> emailSettingsAsJson(result)));
  }

  public Future<Setting> getEmailSettingsByKey(String key, Conn conn) {
    log.debug("getEmailSettingsByKey:: key: {}", key);
    Criterion criterion = new Criterion();
    criterion.addCriterion(new Criteria()
      .addField("'key'")
      .setOperation("=")
      .setVal(key));
    criterion.setLimit(new Limit(1));

    return conn.get(SETTINGS_TABLE_NAME, Setting.class, criterion)
      .compose(results -> results.getResults().isEmpty()
        ? failedFuture(new EmailSettingsNotFoundException())
        : succeededFuture(results.getResults().getFirst()))
      .onSuccess(result -> log.debug("getEmailSettingsByKey:: result: {}",
        () -> emailSettingsAsJson(result)));
  }

  public Future<Setting> upsertEmailSettings(Setting emailSettings, Conn conn) {
    log.debug("upsertEmailSettings::");
    return conn.upsert(SETTINGS_TABLE_NAME, String.valueOf(emailSettings.getId()), emailSettings)
      .map(emailSettings::withId)
      .onSuccess(result -> log.debug("upsertEmailSettings:: Settings with key were updated/created"));
  }

  public static String emailSettingsAsJson(Setting emailSettings) {
    if (emailSettings == null) {
      return null;
    }

    return asJson(new Setting()
      .withId(emailSettings.getId())
      .withKey(emailSettings.getKey())
      .withValue(emailSettings.getValue())
      .withScope(emailSettings.getScope())
      .withUserId(emailSettings.getUserId()));
  }
}
