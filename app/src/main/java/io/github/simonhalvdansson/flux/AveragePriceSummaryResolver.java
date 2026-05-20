package io.github.simonhalvdansson.flux;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

final class AveragePriceSummaryResolver {
    static final int DAY_YESTERDAY = -1;
    static final int DAY_TODAY = 0;
    static final int DAY_TOMORROW = 1;

    private AveragePriceSummaryResolver() {
    }

    static List<DaySummary> resolveSurroundingDays(List<PriceFetcher.PriceEntry> entries,
                                                   String countryCode) {
        List<DaySummary> summaries = new ArrayList<>(3);
        summaries.add(resolveDay(entries, countryCode, DAY_YESTERDAY));
        summaries.add(resolveDay(entries, countryCode, DAY_TODAY));
        summaries.add(resolveDay(entries, countryCode, DAY_TOMORROW));
        return summaries;
    }

    static DaySummary resolveDay(List<PriceFetcher.PriceEntry> entries,
                                 String countryCode,
                                 int dayOffset) {
        ZoneId zoneId = RegionConfig.getZoneId(countryCode);
        if (zoneId == null) {
            zoneId = ZoneId.systemDefault();
        }

        LocalDate date = LocalDate.now(zoneId).plusDays(dayOffset);
        List<PriceFetcher.PriceEntry> dayEntries = getEntriesForLocalDate(entries, date, zoneId);
        return new DaySummary(
                getTitleResId(dayOffset),
                date,
                zoneId,
                countryCode,
                dayEntries,
                summarize(dayEntries),
                findExtremeEntry(dayEntries, true),
                findExtremeEntry(dayEntries, false)
        );
    }

    private static List<PriceFetcher.PriceEntry> getEntriesForLocalDate(
            List<PriceFetcher.PriceEntry> entries,
            LocalDate date,
            ZoneId zoneId) {
        List<PriceFetcher.PriceEntry> dayEntries = new ArrayList<>();
        if (entries == null) {
            return dayEntries;
        }
        for (PriceFetcher.PriceEntry entry : entries) {
            if (entry == null || entry.startTime == null || entry.endTime == null) {
                continue;
            }
            LocalDate entryDate = entry.startTime.atZoneSameInstant(zoneId).toLocalDate();
            if (date.equals(entryDate)) {
                dayEntries.add(entry);
            }
        }
        return dayEntries;
    }

    private static Summary summarize(List<PriceFetcher.PriceEntry> entries) {
        Summary summary = new Summary();
        for (PriceFetcher.PriceEntry entry : entries) {
            if (entry == null || entry.startTime == null || entry.endTime == null) {
                continue;
            }
            long minutes = Duration.between(entry.startTime, entry.endTime).toMinutes();
            if (minutes > 0L) {
                summary.add(entry.pricePerKwh, minutes);
            }
        }
        return summary;
    }

    private static PriceFetcher.PriceEntry findExtremeEntry(List<PriceFetcher.PriceEntry> entries,
                                                            boolean findMinimum) {
        PriceFetcher.PriceEntry extremeEntry = null;
        for (PriceFetcher.PriceEntry entry : entries) {
            if (entry == null || entry.startTime == null || entry.endTime == null) {
                continue;
            }
            long minutes = Duration.between(entry.startTime, entry.endTime).toMinutes();
            if (minutes <= 0L) {
                continue;
            }
            if (extremeEntry == null
                    || (findMinimum && entry.pricePerKwh < extremeEntry.pricePerKwh)
                    || (!findMinimum && entry.pricePerKwh > extremeEntry.pricePerKwh)) {
                extremeEntry = entry;
            }
        }
        return extremeEntry;
    }

    private static int getTitleResId(int dayOffset) {
        if (dayOffset == DAY_YESTERDAY) {
            return R.string.average_yesterday_label;
        }
        if (dayOffset == DAY_TOMORROW) {
            return R.string.average_tomorrow_label;
        }
        return R.string.average_today_label;
    }

    static final class Summary {
        private double weightedTotal = 0.0;
        private long totalMinutes = 0L;
        private double minPrice = Double.POSITIVE_INFINITY;
        private double maxPrice = Double.NEGATIVE_INFINITY;

        void add(double price, long minutes) {
            weightedTotal += price * minutes;
            totalMinutes += minutes;
            minPrice = Math.min(minPrice, price);
            maxPrice = Math.max(maxPrice, price);
        }

        boolean hasData() {
            return totalMinutes > 0L;
        }

        double average() {
            return weightedTotal / totalMinutes;
        }

        double minPrice() {
            return minPrice;
        }

        double maxPrice() {
            return maxPrice;
        }
    }

    static final class DaySummary {
        final int titleResId;
        final LocalDate date;
        final ZoneId zoneId;
        final String countryCode;
        final List<PriceFetcher.PriceEntry> entries;
        final Summary summary;
        final PriceFetcher.PriceEntry minEntry;
        final PriceFetcher.PriceEntry maxEntry;

        DaySummary(int titleResId,
                   LocalDate date,
                   ZoneId zoneId,
                   String countryCode,
                   List<PriceFetcher.PriceEntry> entries,
                   Summary summary,
                   PriceFetcher.PriceEntry minEntry,
                   PriceFetcher.PriceEntry maxEntry) {
            this.titleResId = titleResId;
            this.date = date;
            this.zoneId = zoneId;
            this.countryCode = countryCode;
            this.entries = entries;
            this.summary = summary;
            this.minEntry = minEntry;
            this.maxEntry = maxEntry;
        }
    }
}
