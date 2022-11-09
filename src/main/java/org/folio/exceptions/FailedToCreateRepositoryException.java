package org.folio.exceptions;

public class FailedToCreateRepositoryException extends RuntimeException {
  public FailedToCreateRepositoryException(Exception exception) {
    super(exception);
  }
}
