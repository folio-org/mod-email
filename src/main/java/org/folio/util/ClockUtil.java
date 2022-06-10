package org.folio.util;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import lombok.NonNull;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ClockUtil {

  private Clock clock = Clock.systemUTC();

  public Clock getClock() {
    return clock;
  }

  public static void setClock(@NonNull Clock clock) {
    ClockUtil.clock = clock;
  }

  public static void setDefaultClock() {
    clock = Clock.systemUTC();
  }

  public ZonedDateTime getZonedDateTime() {
    return ZonedDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
  }

}
