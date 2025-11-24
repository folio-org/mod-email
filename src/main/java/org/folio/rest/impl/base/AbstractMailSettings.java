package org.folio.rest.impl.base;

import static io.netty.handler.codec.http.HttpResponseStatus.UNPROCESSABLE_ENTITY;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static org.folio.rest.jaxrs.resource.EmailSettings.PutEmailSettingsByIdResponse.respond204;
import static org.folio.services.MailSettingsService.SETTINGS_TABLE;
import static org.folio.services.MailSettingsService.SMTP_CONFIG_KEY;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javax.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.exceptions.EmailSettingsException;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.Setting;
import org.folio.rest.jaxrs.model.SettingCollection;
import org.folio.rest.jaxrs.resource.EmailSettings.DeleteEmailSettingsByIdResponse;
import org.folio.rest.jaxrs.resource.EmailSettings.GetEmailSettingsByIdResponse;
import org.folio.rest.jaxrs.resource.EmailSettings.GetEmailSettingsResponse;
import org.folio.rest.jaxrs.resource.EmailSettings.PostEmailSettingsResponse;
import org.folio.rest.persist.Conn;
import org.folio.rest.persist.PgUtil;
import org.folio.util.SmtpConfigurationValueVerifier;

public abstract class AbstractMailSettings {

  private static final Logger log = LogManager.getLogger(AbstractMailSettings.class);
  private static final Map<String, Consumer<Object>> MAIL_SETTINGS_VERIFIERS = Map.of(
    SMTP_CONFIG_KEY, SmtpConfigurationValueVerifier::verify
  );

  protected AbstractMailSettings() {
    // empty constructor
  }

  /**
   * Finds settings by a CQL query.
   *
   * @param query  the CQL query string (may be null or empty for default)
   * @param offset the result offset for paging
   * @param limit  the maximum number of results to return
   * @return a Future containing a JAX-RS Response with SettingCollection on success
   */
  protected Future<Response> findSettingsByQuery(String query, int offset, int limit,
    Map<String, String> okapiHeaders, Context context) {
    return PgUtil.get(SETTINGS_TABLE, Setting.class, SettingCollection.class, query, offset, limit,
      okapiHeaders, context, GetEmailSettingsResponse.class);
  }

  /**
   * Retrieves a setting by its id.
   *
   * @param id the setting id
   * @return a Future containing a JAX-RS Response with the Setting on success or 404 if not found
   */
  protected Future<Response> findSettingById(String id, Map<String, String> okapiHeaders, Context context) {
    return PgUtil.getById(SETTINGS_TABLE, Setting.class, id,
      okapiHeaders, context, GetEmailSettingsByIdResponse.class);
  }

  /**
   * Creates a new setting.
   *
   * <p>The method validates the setting key and its value using configured verifiers.
   * If the key is invalid a 400 Response is returned.</p>
   *
   * @param setting the Setting to create
   * @return a Future containing a JAX-RS Response with the created Setting on success,
   * or a 400 Response if validation fails
   */
  protected Future<Response> createSetting(Setting setting,
    Map<String, String> okapiHeaders, Context vertxContext) {

    var settingVerifier = MAIL_SETTINGS_VERIFIERS.get(setting.getKey());
    if (settingVerifier == null) {
      var invalidKeyErrorEntity = getInvalidKeyErrorEntity();
      throw new EmailSettingsException(invalidKeyErrorEntity, UNPROCESSABLE_ENTITY.code());
    }

    settingVerifier.accept(setting.getValue());
    removeIdFromValueIfPresent(setting);

    return PgUtil.post(SETTINGS_TABLE, setting, okapiHeaders, vertxContext, PostEmailSettingsResponse.class);
  }

  /**
   * Updates an existing setting by id.
   *
   * <p>The method validates that the setting exists and that the setting key is not changed.
   * It applies key-specific validation before persisting the update.</p>
   *
   * @param id       the id of the setting to update
   * @param newValue the new Setting value
   * @return a Future containing a JAX-RS Response with 204 No Content on success,
   * or an error Response (e.g. 400/404/500) if validation or update fails
   */
  protected Future<Response> updateSettingById(String id, Setting newValue,
    Map<String, String> okapiHeaders, Context vertxContext) {

    var settingVerifier = MAIL_SETTINGS_VERIFIERS.get(newValue.getKey());
    if (settingVerifier == null) {
      var invalidKeyErrorEntity = getInvalidKeyErrorEntity();
      throw new EmailSettingsException(invalidKeyErrorEntity, UNPROCESSABLE_ENTITY.code());
    }

    var postgresClient = PgUtil.postgresClient(vertxContext, okapiHeaders);
    return postgresClient.withTrans(conn -> conn.getById(SETTINGS_TABLE, id)
      .compose(oldValue -> updateEntity(conn, id, oldValue, newValue)));
  }

  /**
   * Deletes a setting by id.
   *
   * @param id the id of the setting to delete
   * @return a Future containing a JAX-RS Response confirming deletion
   */
  protected Future<Response> deleteSettingById(String id,
    Map<String, String> okapiHeaders, Context context) {
    return PgUtil.deleteById(SETTINGS_TABLE, id,
      okapiHeaders, context, DeleteEmailSettingsByIdResponse.class);
  }

  protected Response handleServiceError(Throwable throwable) {
    if (throwable instanceof EmailSettingsException emailSettingsException) {
      log.debug("handleError:: Handling MailServiceException: ", throwable);
      return emailSettingsException.buildErrorResponse();
    }

    log.warn("handleError:: Handling unexpected error: ", throwable);
    var error = new Error()
      .withCode("service_error")
      .withType(throwable.getClass().getSimpleName())
      .withMessage("Unexpected error occurred: " + throwable.getMessage());

    return Response.status(INTERNAL_SERVER_ERROR)
      .header(CONTENT_TYPE, APPLICATION_JSON)
      .entity(error)
      .build();
  }

  private Future<Response> updateEntity(Conn conn, String id, JsonObject prevValue, Setting newValue) {
    if (prevValue == null) {
      throw new EmailSettingsException(getEntityNotFoundErrorEntity(id), NOT_FOUND);
    }

    MAIL_SETTINGS_VERIFIERS.get(newValue.getKey()).accept(newValue.getValue());
    removeIdFromValueIfPresent(newValue);
    return conn.update(SETTINGS_TABLE, newValue, id).map(updatedRs -> respond204());
  }

  private Error getInvalidKeyErrorEntity() {
    return new Error()
      .withCode("validation_error")
      .withMessage("Key must be one of: " + MAIL_SETTINGS_VERIFIERS.keySet())
      .withParameters(List.of(new Parameter().withKey("key").withValue("invalid value")));
  }

  private static Error getEntityNotFoundErrorEntity(String id) {
    return new Error()
      .withMessage("Setting entity not found by id: " + id)
      .withCode("not_found_error")
      .withParameters(List.of(new Parameter().withKey("id").withValue(id)));
  }

  private static void removeIdFromValueIfPresent(Setting setting) {
    ((Map<?, ?>) setting.getValue()).remove("id");
  }
}
