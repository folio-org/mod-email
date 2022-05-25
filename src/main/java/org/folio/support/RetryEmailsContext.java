package org.folio.support;

import io.vertx.core.json.JsonObject;
import org.folio.rest.jaxrs.model.EmailEntries;

import java.util.Map;

public class RetryEmailsContext {

  private EmailEntries emailEntries;
  private JsonObject configuration;
  private Map<String, String> okapiHeaders;

  public RetryEmailsContext(Map<String, String> okapiHeaders) {
    this.okapiHeaders = okapiHeaders;
  }

  public RetryEmailsContext setEmailEntries(EmailEntries emailEntries) {
    this.emailEntries = emailEntries;
    return this;
  }

  public RetryEmailsContext setConfiguration(JsonObject configuration) {
    this.configuration = configuration;
    return this;
  }

  public RetryEmailsContext setOkapiHeaders(Map<String, String> okapiHeaders) {
    this.okapiHeaders = okapiHeaders;
    return this;
  }

  public EmailEntries getEmailEntries() {
    return emailEntries;
  }

  public JsonObject getConfiguration() {
    return configuration;
  }

  public Map<String, String> getOkapiHeaders() {
    return okapiHeaders;
  }
}
