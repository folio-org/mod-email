package org.folio.enums;

import java.util.Arrays;

public enum SendingStatus {
  NEW,
  IN_PROCESS,
  DELIVERED,
  FAILURE;

  public static SendingStatus findStatusByName(String name) {
    return Arrays.stream(SendingStatus.values())
      .filter(status -> status.name().equalsIgnoreCase(name))
      .findFirst()
      .orElse(SendingStatus.NEW);
  }

  public static String getStatus(SendingStatus sendingStatus) {
    return Arrays.stream(SendingStatus.values())
      .filter(status -> status == sendingStatus)
      .map(Enum::name)
      .findFirst()
      .orElse(SendingStatus.NEW.name());
  }
}
