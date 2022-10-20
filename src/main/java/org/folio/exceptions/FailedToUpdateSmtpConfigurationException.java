package org.folio.exceptions;

public class FailedToUpdateSmtpConfigurationException extends SmtpConfigurationException {
  public FailedToUpdateSmtpConfigurationException() {
    super("Failed to update SMTP configuration");
  }
}
