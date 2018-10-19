package org.folio.util;

import io.vertx.ext.mail.LoginOption;
import io.vertx.ext.mail.StartTLSOptions;
import org.apache.commons.lang3.StringUtils;
import org.folio.enums.SmtpEmail;
import org.folio.rest.jaxrs.model.Config;
import org.folio.rest.jaxrs.model.Configurations;
import org.folio.rest.jaxrs.resource.Email.PostEmailResponse;

import javax.ws.rs.core.Response.Status;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.folio.enums.SmtpEmail.*;

public class EmailUtils {
  /**
   * Minimum SMTP server configuration requirements
   */
  public final static Set<String> REQUIREMENTS_CONFIG_SET = Stream.of(EMAIL_SMTP_HOST, EMAIL_SMTP_PORT, EMAIL_USERNAME, EMAIL_PASSWORD)
    .map(Enum::name)
    .collect(Collectors.toSet());

  /**
   * Check the minimum SMTP server configuration
   */
  public static boolean checkMinConfigSntpServer(Configurations configurations) {
    Set<String> configSet = configurations.getConfigs().stream()
      .map(val -> val.getCode().toUpperCase())
      .collect(Collectors.toSet());
    return !configSet.containsAll(REQUIREMENTS_CONFIG_SET);
  }

  public static PostEmailResponse createResponse(Status status, String message) {
    switch (status) {
      case OK:
        return PostEmailResponse.respond200WithTextPlain(message);
      case BAD_REQUEST:
        return PostEmailResponse.respond400WithTextPlain(message);
      case INTERNAL_SERVER_ERROR:
        return PostEmailResponse.respond500WithTextPlain(message);
      default:
        return PostEmailResponse.respond500WithTextPlain("Internal Server Error");
    }
  }

  /**
   * Get the configuration value by class.
   * if the value is not present, the default value is returned (EMPTY, -1, false, LoginOption.NONE or StartTLSOptions.OPTIONAL)
   */
  public static <T> T getEmailConfig(Configurations configurations, SmtpEmail smtpEmail, Class<T> t) {
    String conf = getConfigurationsBySmtpEmailVal(configurations, smtpEmail);
    switch (t.getSimpleName()) {
      case "String":
        return StringUtils.isEmpty(conf) ? t.cast(StringUtils.EMPTY) : t.cast(conf);
      case "Integer":
        return StringUtils.isEmpty(conf) ? t.cast(-1) : t.cast(Integer.valueOf(conf));
      case "Boolean":
        return StringUtils.isEmpty(conf) ? t.cast(false) : t.cast(Boolean.valueOf(conf));
      case "LoginOption":
        return StringUtils.isEmpty(conf) ? t.cast(LoginOption.NONE) : t.cast(LoginOption.valueOf(conf));
      case "StartTLSOptions":
        return StringUtils.isEmpty(conf) ? t.cast(StartTLSOptions.OPTIONAL) : t.cast(StartTLSOptions.valueOf(conf));
      default:
        throw new IllegalArgumentException("Incorrect SMTP server configuration value: " + conf);
    }
  }

  public static String getMessageConfig(String val) {
    if (StringUtils.isEmpty(val)) {
      return StringUtils.EMPTY;
    }
    return val;
  }

  private static String getConfigurationsBySmtpEmailVal(Configurations configurations, SmtpEmail smtpEmail) {
    return configurations.getConfigs().stream()
      .filter(config -> config.getCode().equalsIgnoreCase(smtpEmail.name()))
      .map(Config::getValue)
      .findFirst()
      .orElse(StringUtils.EMPTY);
  }
}
