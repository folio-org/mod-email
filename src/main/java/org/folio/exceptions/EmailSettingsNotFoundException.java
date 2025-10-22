package org.folio.exceptions;

public class EmailSettingsNotFoundException extends RuntimeException {
  public EmailSettingsNotFoundException() {
    super("Email settings not found");
  }
}
