package fr.maif.features;

import org.apache.commons.codec.digest.MurmurHash3;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;

public final class EvaluationService {
    private EvaluationService() {}

    public static boolean isPeriodActive(FeaturePeriod period) {
        var now = Instant.now();
        return period.begin.map(i -> i.isBefore(now)).orElse(true) &&
                period.end.map(i -> i.isAfter(now)).orElse(true) &&
                (period.hourPeriods.isEmpty() || period.hourPeriods.stream().anyMatch(p -> isHourPeriodActive(p, period.timezone, now))) &&
                period.activationDays.stream().allMatch(d -> d.days.contains(now.atZone(period.timezone).getDayOfWeek()));
    }

    public static boolean isHourPeriodActive(HourPeriod period, ZoneId timezone) {
        return isHourPeriodActive(period, timezone, Instant.now());
    }

    private static boolean isHourPeriodActive(HourPeriod period, ZoneId timezone, Instant now) {
        var date = now.atZone(timezone).toLocalDate();
        var zonedStart = date.atTime(period.startTime)
                .atZone(timezone).toInstant();
        var zonedEnd = date.atTime(period.endTime)
                .atZone(timezone).toInstant();

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
