package org.folio.exceptions;

public class SmtpConfigurationException extends ConfigurationException {

  public SmtpConfigurationException(String message) {
    super(message, null, false, false);
  }
}
