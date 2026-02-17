package org.folio.util;

import static io.vertx.core.Future.failedFuture;
import static io.vertx.core.Future.succeededFuture;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.isNoneBlank;
import static org.folio.enums.SmtpEmail.AUTH_METHODS;
import static org.folio.enums.SmtpEmail.EMAIL_FROM;
import static org.folio.enums.SmtpEmail.EMAIL_PASSWORD;
import static org.folio.enums.SmtpEmail.EMAIL_SMTP_HOST;
import static org.folio.enums.SmtpEmail.EMAIL_SMTP_LOGIN_OPTION;
import static org.folio.enums.SmtpEmail.EMAIL_SMTP_PORT;
import static org.folio.enums.SmtpEmail.EMAIL_SMTP_SSL;
import static org.folio.enums.SmtpEmail.EMAIL_START_TLS_OPTIONS;
import static org.folio.enums.SmtpEmail.EMAIL_TRUST_ALL;
import static org.folio.enums.SmtpEmail.EMAIL_USERNAME;

import io.vertx.ext.mail.MailConfig;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.enums.SmtpEmail;
import org.folio.exceptions.SmtpConfigurationException;
import org.folio.rest.jaxrs.model.Config;
import org.folio.rest.jaxrs.model.Configurations;
import org.folio.rest.jaxrs.model.EmailEntity;
import org.folio.rest.jaxrs.model.EmailHeader;
import org.folio.rest.jaxrs.model.SmtpConfiguration;

import io.vertx.core.Future;
import io.vertx.ext.mail.LoginOption;
import io.vertx.ext.mail.StartTLSOptions;

public class EmailUtils {
  private static final Logger logger = LogManager.getLogger(EmailUtils.class);

  public static final String MAIL_SERVICE_ADDRESS = "mail-service.queue";
  public static final String STORAGE_SERVICE_ADDRESS = "storage-service.queue";
  public static final String EMAIL_STATISTICS_TABLE_NAME = "email_statistics";
  private static final String EMAIL_HEADERS_CONFIG_NAME = "email.headers";
  private static final String ERROR_MIN_REQUIREMENT_MOD_CONFIG = "The 'mod-config' module doesn't have a minimum config for SMTP server, the min config is: %s";
  private static final int MAIL_IDLE_TIMEOUT_SECONDS = 25;

  private EmailUtils() {
    //not called
  }

  /**
   * Minimum SMTP server configuration requirements
   */
  public static final Set<String> REQUIREMENTS_CONFIG_SET = Stream.of(EMAIL_SMTP_HOST, EMAIL_SMTP_PORT, EMAIL_USERNAME, EMAIL_PASSWORD)
    .map(Enum::name)
    .collect(collectingAndThen(toSet(), Collections::unmodifiableSet));

  /**
   * Check the minimum SMTP server configuration
   *
   * @return true if the configuration doesn't satisfy the minimum SMTP configs for sending the email
   */
  public static boolean isIncorrectSmtpServerConfig(Configurations configurations) {
    Set<String> configSet = configurations.getConfigs().stream()
      .filter(conf -> StringUtils.isNotEmpty(conf.getValue()))
      .map(val -> val.getCode().toUpperCase())
      .collect(Collectors.toSet());
    return !configSet.containsAll(REQUIREMENTS_CONFIG_SET);
  }

  public static Future<SmtpConfiguration> validateSmtpConfiguration(SmtpConfiguration smtpConfiguration) {
    boolean configurationIsValid =  smtpConfiguration.getPort() != null && isNoneBlank(
      smtpConfiguration.getHost(),
      smtpConfiguration.getUsername(),
      smtpConfiguration.getPassword());

    if (configurationIsValid) {
      return succeededFuture(smtpConfiguration);
    }

    String errorMessage = String.format(ERROR_MIN_REQUIREMENT_MOD_CONFIG,
      REQUIREMENTS_CONFIG_SET);
    logger.error(errorMessage);
    return failedFuture(new SmtpConfigurationException(errorMessage));
  }

  /**
   * Get the configuration value by class.
   * if the value is not present, the default value is returned (EMPTY(""), -1, false, LoginOption.NONE or StartTLSOptions.OPTIONAL)
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

  public static String findStatusByName(String name) {
    return Arrays.stream(EmailEntity.Status.values())
      .filter(status -> status.name().equalsIgnoreCase(name))
      .map(EmailEntity.Status::value)
      .findFirst()
      .orElse(EmailEntity.Status.DELIVERED.value());
  }

  public static SmtpConfiguration convertSmtpConfiguration(Configurations configurations) {
    return new SmtpConfiguration()
      .withHost(getEmailConfig(configurations, EMAIL_SMTP_HOST, String.class))
      .withPort(getEmailConfig(configurations, EMAIL_SMTP_PORT, Integer.class))
      .withUsername(getEmailConfig(configurations, EMAIL_USERNAME, String.class))
      .withPassword(getEmailConfig(configurations, EMAIL_PASSWORD, String.class))
      .withSsl(getEmailConfig(configurations, EMAIL_SMTP_SSL, Boolean.class))
      .withTrustAll(getEmailConfig(configurations, EMAIL_TRUST_ALL, Boolean.class))
      .withLoginOption(SmtpConfiguration.LoginOption.valueOf(
        getEmailConfig(configurations, EMAIL_SMTP_LOGIN_OPTION, LoginOption.class).toString()))
      .withStartTlsOptions(SmtpConfiguration.StartTlsOptions.valueOf(
        getEmailConfig(configurations, EMAIL_START_TLS_OPTIONS, StartTLSOptions.class).toString()))
      .withAuthMethods(getEmailConfig(configurations, AUTH_METHODS, String.class))
      .withFrom(getEmailConfig(configurations, EMAIL_FROM, String.class))
      .withEmailHeaders(configurations.getConfigs().stream()
        .filter(config -> EMAIL_HEADERS_CONFIG_NAME.equals(config.getConfigName()))
        .filter(config -> isNoneBlank(config.getCode(), config.getValue()))
        .map(config -> new EmailHeader()
          .withName(config.getCode())
          .withValue(config.getValue()))
        .collect(Collectors.toList()));

  }

  public static MailConfig getMailConfig(SmtpConfiguration smtpConfiguration) {
    boolean ssl = ofNullable(smtpConfiguration.getSsl()).orElse(false);

    StartTLSOptions startTLSOptions = StartTLSOptions.valueOf(
      ofNullable(smtpConfiguration.getStartTlsOptions())
        .orElse(SmtpConfiguration.StartTlsOptions.OPTIONAL)
        .value());

    boolean trustAll = ofNullable(smtpConfiguration.getTrustAll()).orElse(false);

    LoginOption loginOption = LoginOption.valueOf(
      ofNullable(smtpConfiguration.getLoginOption())
        .orElse(SmtpConfiguration.LoginOption.NONE)
        .value());

    String authMethods = ofNullable(smtpConfiguration.getAuthMethods()).orElse(StringUtils.EMPTY);

    return new MailConfig()
      .setHostname(smtpConfiguration.getHost())
      .setPort(smtpConfiguration.getPort())
      .setUsername(smtpConfiguration.getUsername())
      .setPassword(smtpConfiguration.getPassword())
      .setSsl(ssl)
      .setTrustAll(trustAll)
      .setLogin(loginOption)
      .setStarttls(startTLSOptions)
      .setAuthMethods(authMethods)
      .setIdleTimeout(MAIL_IDLE_TIMEOUT_SECONDS);
  }
}
