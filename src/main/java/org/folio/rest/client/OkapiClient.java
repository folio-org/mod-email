package org.folio.rest.client;

import static javax.ws.rs.core.HttpHeaders.ACCEPT;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.folio.okapi.common.XOkapiHeaders.TENANT;
import static org.folio.okapi.common.XOkapiHeaders.TOKEN;
import static org.folio.okapi.common.XOkapiHeaders.URL;
import static org.folio.rest.client.WebClientProvider.getWebClient;

import java.util.Map;

import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

public class OkapiClient {
  private final WebClient webClient;
  private final String okapiUrl;
  private final String tenant;
  private final String token;

  public OkapiClient(Vertx vertx, Map<String, String> okapiHeaders, WebClientOptions options) {
    CaseInsensitiveMap<String, String> headers = new CaseInsensitiveMap<>(okapiHeaders);
    this.webClient = getWebClient(vertx, options);
    okapiUrl = headers.get(URL);
    tenant = headers.get(TENANT);
    token = headers.get(TOKEN);
  }

  public HttpRequest<Buffer> getAbs(String path) {
    return webClient.requestAbs(HttpMethod.GET, okapiUrl + path)
      .putHeader(ACCEPT, APPLICATION_JSON)
      .putHeader(URL, okapiUrl)
      .putHeader(TENANT, tenant)
      .putHeader(TOKEN, token);
  }

  public HttpRequest<Buffer> deleteAbs(String path) {
    return webClient.requestAbs(HttpMethod.DELETE, okapiUrl + path)
      .putHeader(ACCEPT, APPLICATION_JSON)
      .putHeader(URL, okapiUrl)
      .putHeader(TENANT, tenant)
      .putHeader(TOKEN, token);
  }
}
