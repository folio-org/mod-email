package org.folio.rest.impl.base;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.folio.rest.impl.EmailAPI;

import javax.ws.rs.core.MediaType;

import static org.folio.rest.RestVerticle.*;

public abstract class AbstractEmail {

  private static final String REQUEST_URL_TEMPLATE = "%s/%s?query=module==%s";
  private static final String REQUEST_URI_PATH = "configurations/entries";
  private static final String HTTP_HEADER_ACCEPT = HttpHeaders.ACCEPT.toString();
  private static final String HTTP_HEADER_CONTENT_TYPE = HttpHeaders.CONTENT_TYPE.toString();
  private static final String OKAPI_URL_HEADER = "x-okapi-url";
  private static final String MODULE_EMAIL_SMTP_SERVER = "SMPT_SERVER";
  private static final String LOOKUP_TIMEOUT = "lookup.timeout";
  private static final String LOOKUP_TIMEOUT_VAL = "1000";

  private static final String ERROR_LOOKING_UP_MOD_CONFIG = "Error looking up config at url=%s | Expected status code 200, got %s | error message: %s";
  protected static final String ERROR_MIN_REQUIREMENT_MOD_CONFIG = "The 'mod-config' module doesn't have a minimum config for SMTP server, the min config is: %s";

  protected final Logger logger = LoggerFactory.getLogger(EmailAPI.class);
  protected final Vertx vertx;

  protected String tenantId;
  private HttpClient httpClient;

  /**
   * Timeout to wait for response
   */
  private int lookupTimeout = Integer.parseInt(MODULE_SPECIFIC_ARGS.getOrDefault(LOOKUP_TIMEOUT, LOOKUP_TIMEOUT_VAL));

  public AbstractEmail(Vertx vertx, String tenantId) {
    this.vertx = vertx;
    this.tenantId = tenantId;
    initHttpClient();
  }

  /**
   * init the http client to 'mod-config'
   */
  private void initHttpClient() {
    HttpClientOptions options = new HttpClientOptions();
    options.setConnectTimeout(lookupTimeout);
    options.setIdleTimeout(lookupTimeout);
    this.httpClient = vertx.createHttpClient(options);
  }

  protected Future<JsonObject> lookupConfig(MultiMap headers) {
    Future<JsonObject> future = Future.future();
    String okapiUrl = headers.get(OKAPI_URL_HEADER);
    String okapiToken = headers.get(OKAPI_HEADER_TOKEN);
    String requestUrl = String.format(REQUEST_URL_TEMPLATE, okapiUrl, REQUEST_URI_PATH, MODULE_EMAIL_SMTP_SERVER);
    HttpClientRequest request = httpClient.getAbs(requestUrl);
    request
      .putHeader(OKAPI_HEADER_TOKEN, okapiToken)
      .putHeader(OKAPI_HEADER_TENANT, tenantId)
      .putHeader(HTTP_HEADER_CONTENT_TYPE, MediaType.APPLICATION_JSON)
      .putHeader(HTTP_HEADER_ACCEPT, MediaType.APPLICATION_JSON)
      .handler(response -> {
        if (response.statusCode() != 200) {
          response.bodyHandler(bufHandler ->
            future.fail(String.format(ERROR_LOOKING_UP_MOD_CONFIG, requestUrl, response.statusCode(), bufHandler.toString())));
        } else {
          response.bodyHandler(bufHandler -> {
            JsonObject resultObject = bufHandler.toJsonObject();
            future.complete(resultObject);
          });
        }
      });
    request.end();
    return future;
  }
}
