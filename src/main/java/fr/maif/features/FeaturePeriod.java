package fr.maif.features;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

public class FeaturePeriod {
    public Optional<Instant> begin  = Optional.empty();
    public Optional<Instant> end  = Optional.empty();
    public Set<HourPeriod> hourPeriods  = Collections.emptySet();
    public Optional<ActivationDayOfWeeks> activationDays  = Optional.empty();
    public ZoneId timezone  = ZoneId.systemDefault();

    public boolean active(String user) {
        var now = LocalDateTime.now().atZone(timezone).toInstant();
        return begin.map(i -> i.isBefore(now)).orElse(true) &&
            end.map(i -> i.isAfter(now)).orElse(true) &&
            (hourPeriods.isEmpty() || hourPeriods.stream().anyMatch(p -> p.active(timezone))) &&
            activationDays.stream().allMatch(d -> d.active(timezone));
    }
}
