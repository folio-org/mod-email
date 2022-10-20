package org.folio.exceptions;

public class SmtpConfigurationNotFoundException extends SmtpConfigurationException {
  public SmtpConfigurationNotFoundException() {
    super("SMTP configuration not found");
  }
}
