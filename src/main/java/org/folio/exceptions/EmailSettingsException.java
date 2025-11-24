package org.folio.exceptions;

import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import java.util.List;
import javax.ws.rs.core.Response;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;

public class EmailSettingsException extends RuntimeException {

  private final int statusCode;
  private final transient Error error;

  /**
   * Constructs a MailServiceException without capturing a stack trace.
   *
   * <p>The stack trace is disabled to reduce allocation overhead for expected control-flow errors.
   *
   * @param error      the error payload to return to the client
   * @param statusCode the HTTP status to use for the response
   */
  public EmailSettingsException(Error error, Response.Status statusCode) {
    super(error.getMessage(), null, true, false);
    this.error = error;
    this.statusCode = statusCode.getStatusCode();
  }

  /**
   * Constructs a MailServiceException without capturing a stack trace.
   *
   * <p>The stack trace is disabled to reduce allocation overhead for expected control-flow errors.
   *
   * @param error      the error payload to return to the client
   * @param statusCode the HTTP status to use for the response
   */
  public EmailSettingsException(Error error, int statusCode) {
    super(error.getMessage(), null, true, false);
    this.error = error;
    this.statusCode = statusCode;
  }

  /**
   * Constructs a MailServiceException with a cause, without capturing a stack trace.
   *
   * <p>The stack trace is disabled to reduce allocation overhead for expected control-flow errors.
   *
   * @param error      the error payload to return to the client
   * @param statusCode the HTTP status to use for the response
   * @param cause      the cause of this exception
   */
  public EmailSettingsException(Error error, int statusCode, Throwable cause) {
    super(error.getMessage(), cause, true, false);
    this.error = error;
    this.statusCode = statusCode;
  }

  /**
   * Builds a JAX-RS {@link Response} from this exception using the configured error payload and status.
   *
   * @return a JAX-RS Response with JSON error entity and appropriate status
   */
  public Response buildErrorResponse() {
    return Response.status(statusCode)
      .entity(new Errors().withErrors(List.of(error)))
      .header(CONTENT_TYPE, APPLICATION_JSON)
      .build();
  }

  /**
   * Returns the error payload associated with this exception.
   *
   * @return the {@link Error} object containing error details
   */
  public Error getError() {
    return error;
  }
}
