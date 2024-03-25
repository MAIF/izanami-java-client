package fr.maif.features;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;

public class ActivationDayOfWeeks {
    public Set<DayOfWeek> days;

    public boolean active(ZoneId timezone) {
        return days.contains(LocalDateTime.now().atZone(timezone).getDayOfWeek());
    }
}
