package org.folio.services;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.lang.String.format;
import static org.folio.HttpStatus.HTTP_NO_CONTENT;
import static org.folio.HttpStatus.HTTP_OK;
import static org.folio.util.LogUtil.headersAsString;
import static org.folio.util.LogUtil.smtpConfigAsJson;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClientOptions;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.exceptions.ConfigurationException;
import org.folio.rest.client.OkapiClient;
import org.folio.rest.jaxrs.model.Config;
import org.folio.rest.jaxrs.model.Configurations;
import org.folio.rest.jaxrs.model.SmtpConfiguration;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.PostgresClient;
import org.folio.util.EmailUtils;

/**
 * Provider responsible for resolving SMTP configuration for a given tenant.
 *
 * <p>Lookup strategy:
 * <ol>
 *   <li>Try to read from local mail settings (Postgres) via {@code MailSettingsService}.</li>
 *   <li>If not found, try to read and migrate from the legacy SMTP repository ({@code SmtpConfigurationService}).</li>
 *   <li>If still not found, fetch entries from the mod-configuration, convert and persist them locally,
 *       and attempt to delete the entries from mod-configuration.</li>
 * </ol>
 *
 * <p>All operations are asynchronous and return a {@code Future} containing the resolved
 * {@code SmtpConfiguration} or a failure describing the problem.
 */
public class SmtpConfigurationProvider {

  protected static final Logger log = LogManager.getLogger(SmtpConfigurationProvider.class);

  private static final String CONFIG_BASE_PATH = "/configurations/entries";
  private static final String GET_CONFIG_PATH_TEMPLATE = "%s?query=module==%s";
  private static final String DELETE_CONFIG_PATH_TEMPLATE = "%s/%s";
  private static final String MODULE_EMAIL_SMTP_SERVER = "SMTP_SERVER";
  private static final String LOOKUP_TIMEOUT = "lookup.timeout";
  private static final String LOOKUP_TIMEOUT_VAL = "1000";

  private static final String ERROR_LOOKING_UP_MOD_CONFIG =
    "Error looking up config at %s | Expected status code 200, got %s | error message: %s";

  private final PostgresClient postgresClient;
  private final MailSettingsService mailSettingsService;
  private final Function<Map<String, String>, OkapiClient> okapiClientSupplier;
  private final Supplier<SmtpConfigurationService> smtpConfigurationServiceSupplier;

  public SmtpConfigurationProvider(Vertx vertx,
    MailSettingsService settingsService, PostgresClient postgresClient) {
    this(settingsService, postgresClient,
      headers -> new OkapiClient(vertx, headers, getWebClientOptions()),
      SmtpConfigurationService::new);
  }

  SmtpConfigurationProvider(
    MailSettingsService settingsService, PostgresClient postgresClient,
    Function<Map<String, String>, OkapiClient> okapiClientSupplier,
    Supplier<SmtpConfigurationService> smtpConfigurationServiceSupplier) {
    this.postgresClient = postgresClient;
    this.mailSettingsService = settingsService;
    this.okapiClientSupplier = okapiClientSupplier;
    this.smtpConfigurationServiceSupplier = smtpConfigurationServiceSupplier;
  }

  /**
   * Lookup SMTP configuration for the provider's tenant using the provided Okapi request headers.
   *
   * <p>The returned {@code Future} completes with the resolved {@code SmtpConfiguration} when:
   * <ul>
   *   <li>a configuration is found in local mail settings, or</li>
   *   <li>a configuration is found in the legacy SMTP repository and migrated, or</li>
   *   <li>configurations are fetched from mod-configuration, converted, persisted locally and returned.</li>
   * </ul>
   *
   * <p>Side effects:
   * <ul>
   *   <li>If a legacy SMTP repository entry is migrated, the old entry is removed.</li>
   *   <li>If entries are fetched from mod-config they are persisted locally and deletion requests are issued
   *       for the {@code mod-configuration} entries (deletion is best-effort and logged).</li>
   * </ul>
   *
   * @param headers Okapi request headers required for mod-config requests
   *                       (e.g. tenant, token); must not be {@code null}
   * @return a {@code Future} that completes with the resolved {@code SmtpConfiguration}
   * or fails with a {@code ConfigurationException} * or other runtime cause if lookup or migration fails
   */
  public Future<SmtpConfiguration> lookup(Map<String, String> headers) {
    log.debug("lookupSmtpConfiguration:: parameters requestHeaders: {}", () -> headersAsString(headers));

    return postgresClient.withTrans(conn ->
      mailSettingsService.getSmtpConfigSetting(conn)
        .recover(err -> tryFindAndMigrateSettingsFromSmtpRepo(conn, err))
        .recover(err -> tryFindAndMigrateSettingsFromModConfiguration(err, conn, headers))
        .onFailure(err -> log.warn("Failed to find SMTP configuration: {} {}",
          err.getClass().getSimpleName(), err.getMessage())));
  }

  private Future<SmtpConfiguration> tryFindAndMigrateSettingsFromSmtpRepo(Conn conn, Throwable err) {
    log.info("tryFindAndMigrateSettingsFromSmtpRepo:: setting is absent: {} {}",
      err.getClass().getSimpleName(), err.getMessage());

    var smtpConfigService = smtpConfigurationServiceSupplier.get();
    return smtpConfigService.getSmtpConfiguration(conn)
      .onSuccess(smtpConfig -> log.debug("tryFindAndMigrateSettingsFromSmtpRepo:: config found"))
      .compose(smtpConfig -> mailSettingsService.createSmtpConfigSetting(conn, smtpConfig)
        .map(newSmtpConfig -> Pair.of(smtpConfig.getId(), newSmtpConfig)))
      .compose(smtpConfigPair -> deleteDeprecatedSmtpConfiguration(conn, smtpConfigPair, smtpConfigService))
      .onSuccess(result -> log.debug("tryFindAndMigrateSettingsFromSmtpRepo:: result: {}",
        () -> smtpConfigAsJson(result)));
  }

  private static Future<SmtpConfiguration> deleteDeprecatedSmtpConfiguration(Conn conn,
    Pair<String, SmtpConfiguration> smtpConfigPair, SmtpConfigurationService smtpConfigService) {
    var oldId = smtpConfigPair.getKey();
    var newConfig = smtpConfigPair.getValue();
    log.info("tryFindAndMigrateSettingsFromSmtpRepo:: removing deprecated config: {}", oldId);
    return smtpConfigService.deleteSmtpConfiguration(conn, oldId).map(result -> newConfig);
  }

  private Future<SmtpConfiguration> tryFindAndMigrateSettingsFromModConfiguration(
    Throwable err, Conn conn, Map<String, String> requestHeaders) {
    log.info("tryFindAndMigrationSettingsFromConfigModule:: SMTP repository empty: {} {}",
      err.getClass().getSimpleName(), err.getMessage());

    log.debug("moveConfigsFromModConfigurationToLocalDb:: requestHeaders: {}",
      () -> headersAsString(requestHeaders));
    OkapiClient okapiClient = okapiClientSupplier.apply(requestHeaders);

    return fetchConfigFromModConfig(okapiClient)
      .compose(configs -> copyConfigurationAndDeleteFromModConfig(conn, configs, okapiClient))
      .onSuccess(result -> log.debug("tryFindAndMigrationSettingsFromConfigModule:: result: {}",
        () -> smtpConfigAsJson(result)));
  }

  private Future<Configurations> fetchConfigFromModConfig(OkapiClient okapiClient) {
    log.debug("fetchConfigFromModConfig:: fetching from mod-config");

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

  private Future<SmtpConfiguration> copyConfigurationAndDeleteFromModConfig(Conn conn,
    Configurations configurations, OkapiClient okapiClient) {

    log.debug("copyConfigurationAndDeleteFromModConfig:: configurations: " +
      "Configurations(totalRecords={})", configurations::getTotalRecords);

    return succeededFuture(configurations)
      .map(EmailUtils::convertSmtpConfiguration)
      .compose(EmailUtils::validateSmtpConfiguration)
      .compose(configuration -> mailSettingsService.createSmtpConfigSetting(conn, configuration))
      .onSuccess(smtpConfig -> deleteEntriesFromModConfiguration(configurations, okapiClient));
  }

  private void deleteEntriesFromModConfiguration(Configurations configurationsToDelete,
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
              log.debug("deleteEntriesFromModConfig:: Successfully deleted configuration entry {}", id);
              return;
            }
            log.warn("deleteEntriesFromModConfig:: Failed to delete configuration entry {}", id);
          })
          .onFailure(log::error);
      });
  }

  private static WebClientOptions getWebClientOptions() {
    final int lookupTimeout = Integer.parseInt(
      System.getProperty(LOOKUP_TIMEOUT, LOOKUP_TIMEOUT_VAL));

    var webClientOptions = new WebClientOptions();
    webClientOptions.setConnectTimeout(lookupTimeout);
    webClientOptions.setIdleTimeout(lookupTimeout);
    return webClientOptions;
  }
}
