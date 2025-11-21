package org.folio.util;

import static io.netty.handler.codec.http.HttpResponseStatus.UNPROCESSABLE_ENTITY;
import static java.util.Collections.emptyList;

import io.vertx.core.json.JsonObject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.exceptions.EmailSettingsException;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.SmtpConfiguration;

public class SmtpConfigurationValueVerifier {

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
      var violations = validator.getValidator().validate(smtpConfiguration);
      if (CollectionUtils.isNotEmpty(violations)) {
        var error = getValidationError(collectViolationsAsParameters(violations));
        throw new EmailSettingsException(error, UNPROCESSABLE_ENTITY.code());
      }
    } catch (IllegalArgumentException exception) {
      var error = getValidationError(exception.getMessage(), emptyList());
      throw new EmailSettingsException(error, UNPROCESSABLE_ENTITY.code(), exception);
    }
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
