package io.dataguardians.sso.core.config;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.util.Set;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.dataguardians.sso.core.data.TimeConfig;
import io.dataguardians.sso.core.model.hostgroup.TimeConfigJson;

public class TimeChecker {

  private static Integer convertDayOfWeek(String dow) {
    switch (dow) {
      case "SUNDAY":
        return 0;
      case "MONDAY":
        return 1;
      case "TUEDAY":
        return 2;
      case "WEDNESDAY":
        return 3;
      case "THURSDAY":
        return 4;
      case "FRIDAY":
        return 5;
      case "SATURDAY":
        return 6;
      default:
        return -1;
    }
  }

  public static boolean isTimeWithinRange(LocalDateTime currentTime, TimeConfigJson config)
      throws JsonProcessingException {
    return isTimeWithinRange(currentTime, TimeConfig.convertFromHttp(config));

  }
  public static boolean isTimeWithinRange(LocalDateTime currentTime, TimeConfig config) {
    // Check if current day is within the allowed days
    var dow = currentTime.getDayOfWeek();

    if (null != config.getDaysOfWeek() &&
        !config.getDaysOfWeek().isEmpty() && !config.getDaysOfWeek().contains(convertDayOfWeek(dow.toString()))) {
      return false;
    }

    // Create LocalTime objects for comparison
    OffsetTime startTime = OffsetTime.of(
        config.getBeginRangeHour(), config.getBeginRangeMinute(), config.getBeginRangeSecond(),
        0,  // nanosecond
        ZoneOffset.UTC ); // Zulu time (UTC)
    OffsetTime endTime =
        OffsetTime.of(
            config.getEndRangeHour(), config.getEndRangeMinute(), config.getEndRangeSecond(),0,  // nanosecond
            ZoneOffset.UTC ); // Zulu time (UTC)
    LocalTime now = currentTime.toLocalTime();

    // Check if the current time is between the start and end time
    return (startTime.equals(endTime) || (!now.isBefore(startTime.toLocalTime()) && !now.isAfter(endTime.toLocalTime())));
  }

  public static void main(String[] args) {
    // Example usage
    TimeConfig config =
        TimeConfig.builder()
            .daysOfWeek(
                Set.of(1,3))
            .beginRangeHour(9)
            .beginRangeMinute(0)
            .beginRangeSecond(0)
            .endRangeHour(17)
            .endRangeMinute(0)
            .endRangeSecond(0)
            .build();

    LocalDateTime currentTime = LocalDateTime.now(); // Get current time and date
    boolean withinRange = isTimeWithinRange(currentTime, config);
  }
}
