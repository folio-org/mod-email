package org.folio.services;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.folio.util.LogUtil.smtpConfigAsJson;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.exceptions.SmtpConfigurationNotFoundException;
import org.folio.repository.SmtpConfigurationRepository;
import org.folio.rest.jaxrs.model.SmtpConfiguration;
import org.folio.rest.persist.PostgresClient;

import io.vertx.core.Future;
import io.vertx.core.Vertx;

public class SmtpConfigurationService {
  private static final Logger log = LogManager.getLogger(SmtpConfigurationService.class);

  private final SmtpConfigurationRepository repository;

  public SmtpConfigurationService(Vertx vertx, String tenantId) {
    PostgresClient pgClient = PostgresClient.getInstance(vertx, tenantId);
    repository = new SmtpConfigurationRepository(pgClient);
  }

  public Future<SmtpConfiguration> getSmtpConfiguration() {
    log.debug("getSmtpConfiguration::");
    return repository.getAllWithLimit(1)
      .compose(configs -> configs.isEmpty()
        ? failedFuture(new SmtpConfigurationNotFoundException())
        : succeededFuture(configs.get(0)));
  }

  public Future<SmtpConfiguration> createSmtpConfiguration(SmtpConfiguration smtpConfiguration) {
    log.debug("createSmtpConfiguration::");
    return repository.save(smtpConfiguration, smtpConfiguration.getId())
      .map(smtpConfiguration::withId)
      .onSuccess(result -> log.debug("createSmtpConfiguration:: result: {}",
        () -> smtpConfigAsJson(result)));
  }
}
