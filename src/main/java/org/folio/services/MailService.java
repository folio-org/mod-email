package org.folio.services;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.folio.rest.jaxrs.model.Configurations;
import org.folio.rest.jaxrs.model.EmailEntity;

public interface MailService {
   Future<JsonObject> sendEmail(Configurations configurations, EmailEntity emailEntity);
}
