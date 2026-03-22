package io.github.simonhalvdansson.flux;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
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

    static List<PriceFetcher.PriceEntry> applyCurrentPriceToDisplayedBars(List<PriceFetcher.PriceEntry> entries,
                                                                          PriceFetcher.PriceEntry currentEntry) {
        if (entries == null || entries.isEmpty() || currentEntry == null) {
            return entries;
        }

        List<PriceFetcher.PriceEntry> adjustedEntries = new ArrayList<>(entries.size());
        boolean replaced = false;
        for (PriceFetcher.PriceEntry entry : entries) {
            if (!replaced && overlaps(entry, currentEntry)) {
                adjustedEntries.add(copyEntryWithPrice(entry, currentEntry));
                replaced = true;
            } else {
                adjustedEntries.add(entry);
            }
        }
        return adjustedEntries;
    }

    private static boolean overlaps(PriceFetcher.PriceEntry left, PriceFetcher.PriceEntry right) {
        return left != null
                && right != null
                && left.startTime != null
                && left.endTime != null
                && right.startTime != null
                && right.endTime != null
                && left.endTime.isAfter(right.startTime)
                && left.startTime.isBefore(right.endTime);
    }

    private static PriceFetcher.PriceEntry copyEntryWithPrice(PriceFetcher.PriceEntry timeSource,
                                                              PriceFetcher.PriceEntry priceSource) {
        PriceFetcher.PriceEntry copiedEntry = new PriceFetcher.PriceEntry();
        copiedEntry.startTime = timeSource.startTime;
        copiedEntry.endTime = timeSource.endTime;
        copiedEntry.pricePerKwh = priceSource.pricePerKwh;
        copiedEntry.pricePerKwhEur = priceSource.pricePerKwhEur;
        copiedEntry.exchangeRatePerEur = priceSource.exchangeRatePerEur;
        copiedEntry.currency = priceSource.currency;
        return copiedEntry;
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
