package org.folio.enums;

import org.folio.rest.jaxrs.model.Config;

/**
 * Represents a mail configuration with mail server hostname,
 * port, security options, login options and login/password
 */
public enum SmtpEmail {
  EMAIL_SMTP_HOST,
  EMAIL_SMTP_LOGIN_OPTION,
  EMAIL_SMTP_PORT,
  EMAIL_TRUST_ALL,
  EMAIL_SMTP_SSL,
  EMAIL_START_TLS_OPTIONS,
  EMAIL_USERNAME,
  EMAIL_PASSWORD,
  EMAIL_FROM;

  public static boolean isContainsEmailFrom(Config from) {
    return EMAIL_FROM.name().equalsIgnoreCase(from.getCode());
  }
}
