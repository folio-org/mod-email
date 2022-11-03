package org.folio.services;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.folio.rest.tools.utils.TenantTool.tenantId;

import java.util.Map;
import java.util.UUID;

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

  public Future<SmtpConfiguration> getSmtpConfigurationById(String id) {
    return repository.get(id)
      .compose(config -> config == null
        ? failedFuture(new SmtpConfigurationNotFoundException())
        : succeededFuture(config));
  }

  public Future<SmtpConfiguration> createSmtpConfiguration(SmtpConfiguration smtpConfiguration) {
    String proposedId = smtpConfiguration.getId() == null
      ? UUID.randomUUID().toString()
      : smtpConfiguration.getId();

    return getSmtpConfigurationId(proposedId)
      .compose(id -> repository.save(smtpConfiguration, id))
      .map(smtpConfiguration::withId);
  }

  public Future<SmtpConfiguration> updateSmtpConfiguration(SmtpConfiguration smtpConfiguration) {
    Future<SmtpConfiguration> smtpConfigurationFuture = smtpConfiguration.getId() == null
      ? getSmtpConfiguration()
      : getSmtpConfigurationById(smtpConfiguration.getId());

    return smtpConfigurationFuture
      .compose(config -> repository.update(smtpConfiguration, config.getId()))
      .compose(updateSucceeded -> updateSucceeded
        ? succeededFuture(smtpConfiguration)
        : failedFuture(new FailedToUpdateSmtpConfigurationException()));
  }

  public Future<Void> deleteSmtpConfiguration() {
    return repository.removeAll(tenantId);
  }

  private Future<String> getSmtpConfigurationId(String proposedId) {
    return getSmtpConfiguration()
      .map(SmtpConfiguration::getId)
      .recover(throwable -> succeededFuture(proposedId));
  }
}
