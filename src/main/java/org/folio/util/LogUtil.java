package org.folio.util;

import static com.google.common.primitives.Ints.min;
import static java.lang.String.format;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;

import org.apache.commons.collections4.map.CaseInsensitiveMap;
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

  public static String asJson(Object object) {
    if (object == null) {
      return null;
    }

    if (object instanceof String) {
      return (String) object;
    }

    if (object instanceof Number) {
      return object.toString();
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

  public static String asJson(List<?> list) {
    return asJson(list, DEFAULT_NUM_OF_LIST_ELEMENTS_TO_LOG);
  }

  public static String asJson(List<?> list, int maxNumberOfElementsToLog) {
    try {
      if (list == null) {
        return null;
      } else {
        int numberOfElementsToLog = min(list.size(), maxNumberOfElementsToLog);
        return format("list(size: %d, %s: [%s])", list.size(),
          numberOfElementsToLog == list.size() ? "elements"
            : format("first %d element%s", numberOfElementsToLog, plural(numberOfElementsToLog)),
          list.subList(0, numberOfElementsToLog).stream()
            .map(LogUtil::asJson)
            .collect(Collectors.joining(", ")));
      }
    } catch (Exception ex) {
      log.warn("logList:: Failed to log a list", ex);
      return null;
    }
  }

  private static String plural(int number) {
    return number == 1 ? "" : "s";
  }

  public static String headersAsString(Map<String, String> okapiHeaders) {
    try {
      Map<String, String> headersCopy= new CaseInsensitiveMap<>(okapiHeaders);
      headersCopy.remove("x-okapi-token");
      return headersCopy.toString();
    } catch (Exception ex) {
      log.warn("logOkapiHeaders:: Failed to log Okapi headers", ex);
      return null;
    }
  }

  public static String bodyAsString(HttpResponse<Buffer> response) {
    try {
      return crop(response.bodyAsString().replaceAll(R_N_LINE_SEPARATOR, R_LINE_SEPARATOR));
    } catch (Exception ex) {
      log.warn("logResponseBody:: Failed to log an HTTP response", ex);
      return null;
    }
  }

  public static Handler<AsyncResult<Response>> loggingResponseHandler(String methodName,
    Handler<AsyncResult<Response>> asyncResultHandler, Logger logger) {

    try {
      return responseAsyncResult -> {
        Response response = responseAsyncResult.result();
        Object entity = response.getEntity();
        String template = "{}:: result: HTTP response (code: {}, body: {})";
        if (entity instanceof String) {
          logger.info(template, methodName, response.getStatus(), crop((String) entity));
        } else {
          logger.info(template, () -> methodName, response::getStatus,
            () -> asJson(response.getEntity()));
        }
        asyncResultHandler.handle(responseAsyncResult);
      };
    } catch (Exception ex) {
      log.warn("loggingResponseHandler:: Failed to create a logging HTTP response " +
        "handler", ex);
      return asyncResultHandler;
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

