package org.folio.util;

import static io.netty.handler.codec.http.HttpResponseStatus.UNPROCESSABLE_ENTITY;
import static java.util.Collections.emptyList;

import io.vertx.core.json.JsonObject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.exceptions.EmailSettingsException;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Identity;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.SmtpConfiguration;

public class SmtpConfigurationValueVerifier {

  private SmtpConfigurationValueVerifier() {
    throw new IllegalStateException("Utility class cannot be initialized");
  }

  /**
   * Verifies the provided value to ensure it is a valid SMTP configuration.
   *
   * @param value - the value to verify, expected to be a Map
   * @throws EmailSettingsException if the value is invalid or fails validation
   */
  public static void verify(Object value) {
    if (!(value instanceof Map<?, ?>)) {
      var invalidValueParam = new Parameter().withKey("value").withValue("must be an object");
      throw new EmailSettingsException(
        getValidationError(List.of(invalidValueParam)), UNPROCESSABLE_ENTITY.code());
    }

    try (var validator = Validation.buildDefaultValidatorFactory()) {
      var smtpConfiguration = JsonObject.mapFrom(value).mapTo(SmtpConfiguration.class);
      var parameters = new ArrayList<Parameter>(
        collectViolationsAsParameters(validator.getValidator().validate(smtpConfiguration)));
      parameters.addAll(collectDuplicateIdentityAddresses(smtpConfiguration));
      if (!parameters.isEmpty()) {
        throw new EmailSettingsException(getValidationError(parameters), UNPROCESSABLE_ENTITY.code());
      }
    } catch (IllegalArgumentException exception) {
      var error = getValidationError(exception.getMessage(), emptyList());
      throw new EmailSettingsException(error, UNPROCESSABLE_ENTITY.code(), exception);
    }
  }

  private static List<Parameter> collectDuplicateIdentityAddresses(SmtpConfiguration smtpConfiguration) {
    List<Identity> identities = smtpConfiguration.getIdentities();
    if (CollectionUtils.isEmpty(identities)) {
      return emptyList();
    }
    Set<String> seen = new HashSet<>();
    Set<String> duplicates = new LinkedHashSet<>();
    for (Identity identity : identities) {
      String address = identity.getAddress();
      if (address != null && !seen.add(address)) {
        duplicates.add(address);
      }
    }
    return duplicates.stream()
      .map(address -> new Parameter()
        .withKey("value.identities")
        .withValue("duplicate address: " + address))
      .toList();
  }

  private static Error getValidationError(List<Parameter> errors) {
    return getValidationError("Invalid value in setting", errors);
  }

  private static Error getValidationError(String message, List<Parameter> errors) {
    return new Error()
      .withCode("validation_error")
      .withMessage(message)
      .withParameters(errors);
  }

  private static List<Parameter> collectViolationsAsParameters(
    Collection<ConstraintViolation<SmtpConfiguration>> violations) {
    return violations.stream()
      .map(violation -> new Parameter()
        .withKey("value." + violation.getPropertyPath().toString())
        .withValue(violation.getMessage()))
      .toList();
  }
}
