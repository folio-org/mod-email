package org.folio.services;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.util.UUID.randomUUID;
import static org.folio.rest.tools.utils.TenantTool.tenantId;

import java.util.Map;

import org.folio.exceptions.FailedToUpdateSmtpConfigurationException;
import org.folio.exceptions.SmtpConfigurationNotFoundException;
import org.folio.repository.SmtpConfigurationRepository;
import org.folio.rest.jaxrs.model.SmtpConfiguration;
import org.folio.rest.persist.PostgresClient;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

public class SmtpConfigurationService {
  private final String tenantId;
  private final SmtpConfigurationRepository repository;

  public SmtpConfigurationService(Vertx vertx, String tenantId) {
    this.tenantId = tenantId;

    PostgresClient pgClient = PostgresClient.getInstance(vertx, tenantId);
    repository = new SmtpConfigurationRepository(pgClient);
  }

  public SmtpConfigurationService(Map<String, String> okapiHeaders, Context vertxContext) {
    this(vertxContext.owner(), tenantId(okapiHeaders));
  }

  public Future<SmtpConfiguration> getSmtpConfiguration() {
    return repository.getAllWithLimit(1)
      .compose(configs -> configs.size() < 1
        ? failedFuture(new SmtpConfigurationNotFoundException())
        : succeededFuture(configs.get(0)));
  }

  public Future<SmtpConfiguration> createSmtpConfiguration(SmtpConfiguration smtpConfiguration) {
    return getSmtpConfigurationId()
      .compose(id -> repository.save(smtpConfiguration, id))
      .map(smtpConfiguration::withId);
  }

  public Future<SmtpConfiguration> updateSmtpConfiguration(SmtpConfiguration smtpConfiguration) {
    return getSmtpConfiguration()
      .compose(config -> repository.update(config, config.getId()))
      .compose(updateSucceeded -> updateSucceeded
        ? getSmtpConfiguration()
        : failedFuture(new FailedToUpdateSmtpConfigurationException()));
  }

  public Future<Void> deleteSmtpConfiguration() {
    return repository.removeAll(tenantId);
  }

  private Future<String> getSmtpConfigurationId() {
    return getSmtpConfiguration()
      .map(SmtpConfiguration::getId)
      .recover(throwable -> succeededFuture(randomUUID().toString()));
  }
}
