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
import org.folio.rest.tools.utils.TenantTool;

import io.vertx.core.Context;
import io.vertx.core.Future;

public class SmtpConfigurationService {
  private final Map<String, String> okapiHeaders;
  private final SmtpConfigurationRepository repository;
  public SmtpConfigurationService(Map<String, String> okapiHeaders, Context vertxContext) {
    this.okapiHeaders = okapiHeaders;
    PostgresClient pgClient = PostgresClient.getInstance(vertxContext.owner(),
      tenantId(okapiHeaders));
    repository = new SmtpConfigurationRepository(pgClient);
  }

  public Future<SmtpConfiguration> getSmtpConfiguration() {
    return repository.getAllWithLimit(1)
      .map(configs -> configs.stream().findFirst().orElse(null));
  }

  public Future<String> createSmtpConfiguration(SmtpConfiguration smtpConfiguration) {
    return getSmtpConfigurationId()
      .compose(id -> repository.save(smtpConfiguration, id));
  }

  public Future<SmtpConfiguration> updateSmtpConfiguration(SmtpConfiguration smtpConfiguration) {
    return getSmtpConfiguration()
      .compose(config -> config == null
        ? Future.failedFuture(new SmtpConfigurationNotFoundException())
        : succeededFuture(config))
      .compose(config -> repository.update(config, config.getId()))
      .compose(updateSucceeded -> updateSucceeded
        ? getSmtpConfiguration()
        : failedFuture(new FailedToUpdateSmtpConfigurationException()));
  }

  public Future<Void> deleteSmtpConfiguration() {
    return repository.removeAll(TenantTool.tenantId(okapiHeaders));
  }

  private Future<String> getSmtpConfigurationId() {
    return getSmtpConfiguration()
      .map(config -> config == null ? randomUUID().toString() : config.getId());
  }
}
