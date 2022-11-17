package org.folio.repository;

import org.folio.rest.jaxrs.model.SmtpConfiguration;
import org.folio.rest.persist.PostgresClient;

public class SmtpConfigurationRepository extends BaseRepository<SmtpConfiguration> {
  public static final String SMTP_CONFIGURATION_TABLE_NAME = "smtp_configuration";

  public SmtpConfigurationRepository(PostgresClient pgClient) {
    super(pgClient, SMTP_CONFIGURATION_TABLE_NAME, SmtpConfiguration.class);
  }
}
