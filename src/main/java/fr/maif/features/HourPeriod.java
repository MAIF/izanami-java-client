package fr.maif.features;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

public class HourPeriod {
    public LocalTime startTime;
    public LocalTime endTime;

    public boolean active(ZoneId timezone) {
        var zonedStart = LocalDateTime.of(LocalDate.now(), startTime)
                .atZone(timezone).toInstant();
        var zonedEnd = LocalDateTime.of(LocalDate.now(), endTime)
                .atZone(timezone).toInstant();
        var now = LocalDateTime.now().atZone(timezone).toInstant();

        return zonedStart.isBefore(now) && zonedEnd.isAfter(now);
    }
}
