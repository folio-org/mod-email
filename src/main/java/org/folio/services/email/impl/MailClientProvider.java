package org.folio.services.email.impl;

import static org.folio.util.LogUtil.smtpConfigAsJson;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.mail.MailClient;
import io.vertx.ext.mail.MailConfig;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.SmtpConfiguration;
import org.folio.util.EmailUtils;

public class MailClientProvider {

  private static final Logger log = LogManager.getLogger(MailClientProvider.class);

  private final Vertx vertx;
  private final Map<String, MailClientHolder> mailClientsCache;

  /**
   * Creates a new MailClientProvider using the given Vert.x instance.
   *
   * @param vertx Vert.x instance used to create MailClient instances
   */
  public MailClientProvider(Vertx vertx) {
    this.vertx = vertx;
    this.mailClientsCache = new ConcurrentHashMap<>();
  }

  /**
   * Returns a {@link MailClient} for the given tenant id and SMTP configuration.
   *
   * <p>
   * If a cached client exists and its configuration equals the provided
   * {@code smtpConfiguration}, the cached client is returned. Otherwise, a new
   * client is created, cached and returned.
   *
   * @param tenantId          the tenant identifier
   * @param smtpConfiguration the SMTP configuration to use for creating the client
   * @return a {@link Future} that completes with the {@link MailClient} instance or fails if creation fails
   */
  public Future<MailClient> get(String tenantId, SmtpConfiguration smtpConfiguration) {
    log.debug("getOrCreateClient:: tenantId: {}, smtpConfiguration: {}",
      () -> tenantId, () -> smtpConfigAsJson(smtpConfiguration));

    var currentTenantClient = mailClientsCache.get(tenantId);
    if (shouldInitNewClient(currentTenantClient, smtpConfiguration)) {
      return Future.future(promise -> {
        log.info("getOrCreateClient:: Creating new mail client...");
        createNewClient(tenantId, smtpConfiguration)
          .onSuccess(promise::complete)
          .onFailure(promise::fail);
      });
    }

    return Future.succeededFuture(currentTenantClient.client());
  }

  /**
   * Removes and closes the mail client for the specified tenant.
   * This should be called when a tenant is deactivated or removed.
   *
   * @param tenantId The tenant identifier
   * @return A Future that completes when the client is closed
   */
  public Future<Void> remove(String tenantId) {
    log.info("removeClient:: Removing mail client for tenant: {}", tenantId);

    var mailClientHolder = mailClientsCache.remove(tenantId);
    if (mailClientHolder != null) {
      return closeClient(mailClientHolder);
    }

    log.debug("removeClient:: No client found for tenant: {}", tenantId);
    return Future.succeededFuture();
  }

  /**
   * Closes all mail clients. This should be called during shutdown.
   *
   * @return A Future that completes when all clients are closed
   */
  public Future<Void> cleanAll() {
    log.info("closeAll:: Closing all mail clients");

    var mailClients = mailClientsCache.keySet().stream().map(this::remove).toList();
    return Future.all(mailClients).mapEmpty();
  }

  /**
   * Returns the {@link SmtpConfiguration} currently associated with the given tenant.
   *
   * @param tenantId the tenant identifier
   * @return the {@link SmtpConfiguration} for the tenant, or {@code null} if no client/configuration is present
   */
  public SmtpConfiguration getConfiguration(String tenantId) {
    var emptyConfiguration = new MailClientHolder(null, null);
    return mailClientsCache.getOrDefault(tenantId, emptyConfiguration).configuration();
  }

  private static boolean shouldInitNewClient(MailClientHolder mch, SmtpConfiguration newConfig) {
    return mch == null || !mch.configuration().equals(newConfig);
  }

  private Future<MailClient> createNewClient(String tenantId, SmtpConfiguration smtpConfiguration) {
    log.debug("createNewClient:: tenantId: {}", tenantId);
    return Optional.ofNullable(mailClientsCache.get(tenantId))
      .map(MailClientProvider::closeClient)
      .orElseGet(Future::succeededFuture)
      .compose(unused -> {
        var newClient = MailClient.create(vertx, getMailClientConfig(smtpConfiguration));
        mailClientsCache.put(tenantId, new MailClientHolder(newClient, smtpConfiguration));

        log.debug("createNewClient:: Successfully created mail client");
        return Future.succeededFuture(newClient);
      });
  }

  private static Future<Void> closeClient(MailClientHolder clientHolder) {
    log.debug("closeClient:: Closing existing mail client...");

    return clientHolder.client().close()
      .onSuccess(v -> log.debug("closeClient:: Successfully closed mail client"))
      .onFailure(error -> log.warn("closeClient:: Failed to close mail client", error));
  }

  private static MailConfig getMailClientConfig(SmtpConfiguration smtpConfiguration) {
    log.debug("getMailConfig:: parameters smtpConfiguration: {}",
      () -> smtpConfigAsJson(smtpConfiguration));
    return EmailUtils.getMailConfig(smtpConfiguration);
  }

  /**
   * Internal class to hold a MailClient and its associated configuration.
   */
  private record MailClientHolder(MailClient client, SmtpConfiguration configuration) {}
}
