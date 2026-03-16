package io.github.simonhalvdansson.flux;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

final class BarChartUtils {

    private BarChartUtils() {
    }

    static double resolveScaleMax(List<PriceFetcher.PriceEntry> entries) {
        double scaleMax = 0.0;
        if (entries == null) {
            return 1.0;
        }
        for (PriceFetcher.PriceEntry entry : entries) {
            scaleMax = Math.max(scaleMax, Math.abs(entry.pricePerKwh));
        }
        return scaleMax > 0.0 ? scaleMax : 1.0;
    }

    static int resolveBarBackgroundRes(PriceFetcher.PriceEntry entry, ZonedDateTime now) {
        return resolveBarBackgroundRes(entry, now, false);
    }

    static int resolveBarBackgroundRes(PriceFetcher.PriceEntry entry,
                                       ZonedDateTime now,
                                       boolean isSelected) {
        ZonedDateTime start = entry.startTime.atZoneSameInstant(ZoneId.systemDefault());
        ZonedDateTime end = entry.endTime.atZoneSameInstant(ZoneId.systemDefault());
        boolean isNegative = entry.pricePerKwh < 0;
        if ((now.isEqual(start) || now.isAfter(start)) && now.isBefore(end)) {
            return isNegative
                    ? (isSelected
                    ? R.drawable.bar_rounded_negative_current_selected
                    : R.drawable.bar_rounded_negative_current)
                    : (isSelected
                    ? R.drawable.bar_rounded_current_selected
                    : R.drawable.bar_rounded_current);
        }
        if (now.isAfter(end)) {
            return isNegative
                    ? (isSelected
                    ? R.drawable.bar_rounded_negative_old_selected
                    : R.drawable.bar_rounded_negative_old)
                    : (isSelected
                    ? R.drawable.bar_rounded_old_selected
                    : R.drawable.bar_rounded_old);
        }
        return isNegative
                ? (isSelected ? R.drawable.bar_rounded_negative_selected : R.drawable.bar_rounded_negative)
                : (isSelected ? R.drawable.bar_rounded_selected : R.drawable.bar_rounded);
    }
}
