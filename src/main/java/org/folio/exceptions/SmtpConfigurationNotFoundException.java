package org.folio.exceptions;

public class SmtpConfigurationNotFoundException extends RuntimeException {
  public SmtpConfigurationNotFoundException() {
    super("SMTP configuration not found");
  }
}
