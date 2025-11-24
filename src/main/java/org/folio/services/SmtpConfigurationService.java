package org.folio.services;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static org.folio.repository.SmtpConfigurationRepository.SMTP_CONFIGURATION_TABLE_NAME;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.exceptions.SmtpConfigurationNotFoundException;
import org.folio.rest.jaxrs.model.SmtpConfiguration;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;

import io.vertx.core.Future;
import org.folio.util.EmailUtils;

public class SmtpConfigurationService {
  private static final Logger log = LogManager.getLogger(SmtpConfigurationService.class);

  public Future<SmtpConfiguration> getSmtpConfiguration(Conn conn) {
    log.debug("getSmtpConfiguration::");
    Criterion criterion = new Criterion();
    criterion.setLimit(new Limit(1));

    return conn.get(SMTP_CONFIGURATION_TABLE_NAME, SmtpConfiguration.class, criterion)
      .compose(results -> results.getResults().isEmpty()
        ? failedFuture(new SmtpConfigurationNotFoundException())
        : succeededFuture(results.getResults().getFirst()))
      .compose(EmailUtils::validateSmtpConfiguration);
  }

  public Future<Boolean> deleteSmtpConfiguration(Conn conn, String id) {
    log.debug("deleteSmtpConfiguration:: parameters id: {}", id);
    return conn.delete(SMTP_CONFIGURATION_TABLE_NAME, id)
      .map(result -> result.rowCount() > 0)
      .onSuccess(result -> log.debug("deleteSmtpConfiguration:: result: {}", result));
  }
}
