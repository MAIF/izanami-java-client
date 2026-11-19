package fr.maif.features;

import org.apache.commons.codec.digest.MurmurHash3;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;

public final class EvaluationService {
    private EvaluationService() {}

    public static boolean isPeriodActive(FeaturePeriod period) {
        var now = LocalDateTime.now().atZone(period.timezone).toInstant();
        return period.begin.map(i -> i.isBefore(now)).orElse(true) &&
                period.end.map(i -> i.isAfter(now)).orElse(true) &&
                (period.hourPeriods.isEmpty() || period.hourPeriods.stream().anyMatch(p -> isHourPeriodActive(p, period.timezone))) &&
                period.activationDays.stream().allMatch(d -> d.days.contains(LocalDateTime.now().atZone(period.timezone).getDayOfWeek()));
    }

    public static boolean isHourPeriodActive(HourPeriod period, ZoneId timezone) {
        var zonedStart = LocalDateTime.of(LocalDate.now(), period.startTime)
                .atZone(timezone).toInstant();
        var zonedEnd = LocalDateTime.of(LocalDate.now(), period.endTime)
                .atZone(timezone).toInstant();
        var now = LocalDateTime.now().atZone(timezone).toInstant();

        return zonedStart.isBefore(now) && zonedEnd.isAfter(now);
    }

    public static boolean isUserOrGroupRuleActive(UserOrGroupList rule, String user, Set<String> groups){
        return rule.users.contains(user) || rule.groups.stream().anyMatch(groups::contains);
    }

    public static boolean isPercentageRuleActive(UserPercentage rule, String feature, String user, int rangeStart) {
        String toHash = feature + "-" + user;
        var bytes = toHash.getBytes();
        long hash = (Math.abs(MurmurHash3.hash32x86(bytes, 0, bytes.length, 42)) % 100) + 1;
        return hash > rangeStart && hash <= (rule.percentage + rangeStart);
    }
}
