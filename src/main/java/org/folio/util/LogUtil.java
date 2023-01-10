package org.folio.util;

import static com.google.common.primitives.Ints.min;
import static java.lang.String.format;
import static java.util.function.Predicate.not;
import static java.util.function.UnaryOperator.identity;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.persist.PostgresClient;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;

public class LogUtil {
  private static final Logger log = LogManager.getLogger(LogUtil.class);
  public static final String R_N_LINE_SEPARATOR = "[\\r\\n]";
  public static final String R_LINE_SEPARATOR = "\\r";
  private static final int MAX_OBJECT_JSON_LENGTH = 10 * 1024;
  private static final int DEFAULT_NUM_OF_LIST_ELEMENTS_TO_LOG = 10;

  private LogUtil() {
    throw new IllegalStateException("Utility class");
  }

  public static String logAsJson(Object object) {
    if (object == null) {
      return null;
    }

    if (object instanceof JsonObject) {
      try {
        return crop(((JsonObject) object).encode());
      } catch (Exception ex) {
        log.warn("logAsJson:: Error while logging JsonObject", ex);
        return null;
      }
    }

    try {
      return crop(PostgresClient.pojo2JsonObject(object).encode());
    } catch (JsonProcessingException jsonProcessingException) {
      log.warn("logAsJson:: Error while logging an object of type {}",
        object.getClass().getCanonicalName(), jsonProcessingException);
      return null;
    } catch (Exception ex) {
      log.warn("logAsJson:: Unexpected error while logging an object of type {}",
        object.getClass().getCanonicalName(), ex);
      return null;
    }
  }

  public static String logOkapiHeaders(Map<String, String> okapiHeaders) {
    try {
      return logAsJson(new JsonObject(okapiHeaders.keySet().stream()
        .filter(not("x-okapi-token"::equalsIgnoreCase))
        .collect(Collectors.toMap(identity(), okapiHeaders::get))));
    } catch (Exception ex) {
      log.warn("logOkapiHeaders:: Failed to log Okapi headers", ex);
      return null;
    }
  }
  public static Handler<AsyncResult<Response>> loggingResponseHandler(String methodName,
                                                                      Handler<AsyncResult<Response>> asyncResultHandler, Logger logger) {

    try {
      return responseAsyncResult -> {
        Response response = responseAsyncResult.result();
        logger.info("{}:: result: HTTP response (code: {}, body: {})", () -> methodName,
          response::getStatus, () -> logAsJson(response.getEntity()));
        asyncResultHandler.handle(responseAsyncResult);
      };
    } catch (Exception ex) {
      log.warn("loggingResponseHandler:: Failed to create a logging HTTP response " +
        "handler", ex);
      return null;
    }
  }

  private static String crop(String str) {
    try {
      return str.substring(0, min(str.length(), MAX_OBJECT_JSON_LENGTH));
    } catch (Exception ex) {
      log.warn("crop:: Failed to crop a string", ex);
      return null;
    }
  }
}