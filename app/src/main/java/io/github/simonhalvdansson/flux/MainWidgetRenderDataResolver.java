package io.github.simonhalvdansson.flux;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class MainWidgetRenderDataResolver {

    static final class RenderData {
        final boolean hasData;
        final boolean apiError;
        final String country;
        final String unitText;
        final String currentPriceText;
        final String currentTimeText;
        final String maxText;
        final String minText;
        final List<PriceFetcher.PriceEntry> barDisplayEntries;
        final List<PriceFetcher.PriceEntry> graphDisplayEntries;
        final double barScaleMax;
        final double graphScaleMax;

        RenderData(boolean hasData,
                   boolean apiError,
                   String country,
                   String unitText,
                   String currentPriceText,
                   String currentTimeText,
                   String maxText,
                   String minText,
                   List<PriceFetcher.PriceEntry> barDisplayEntries,
                   List<PriceFetcher.PriceEntry> graphDisplayEntries,
                   double barScaleMax,
                   double graphScaleMax) {
            this.hasData = hasData;
            this.apiError = apiError;
            this.country = country;
            this.unitText = unitText;
            this.currentPriceText = currentPriceText;
            this.currentTimeText = currentTimeText;
            this.maxText = maxText;
            this.minText = minText;
            this.barDisplayEntries = barDisplayEntries;
            this.graphDisplayEntries = graphDisplayEntries;
            this.barScaleMax = barScaleMax;
            this.graphScaleMax = graphScaleMax;
        }
    }

    private MainWidgetRenderDataResolver() {
    }

    static RenderData resolve(Context context,
                              SharedPreferences prefs,
                              int barPoolMode,
                              boolean fallbackToSampleData) {
        String country = prefs.getString(PriceUpdateJobService.KEY_SELECTED_COUNTRY, "NO");
        String unitText = PriceDisplayUtils.getUnitText(country, prefs);
        boolean apiError = prefs.getBoolean(PriceUpdateJobService.KEY_API_ERROR, false);
        String combinedJson = prefs.getString(PriceRepository.KEY_JSON_DATA, null);

        if (!apiError && combinedJson != null && !combinedJson.trim().isEmpty()) {
            RenderData renderData = buildRenderData(
                    prefs,
                    country,
                    unitText,
                    false,
                    CurrentPriceResolver.getAdjustedEntries(prefs),
                    barPoolMode
            );
            if (renderData != null) {
                return renderData;
            }
        }

        if (fallbackToSampleData) {
            return buildRenderData(
                    prefs,
                    country,
                    unitText,
                    false,
                    buildSampleEntries(),
                    barPoolMode
            );
        }

        return new RenderData(
                false,
                apiError,
                country,
                unitText,
                null,
                null,
                null,
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                1.0,
                1.0
        );
    }

    private static RenderData buildRenderData(SharedPreferences prefs,
                                              String country,
                                              String unitText,
                                              boolean apiError,
                                              List<PriceFetcher.PriceEntry> allData,
                                              int barPoolMode) {
        if (allData == null || allData.isEmpty()) {
            return null;
        }

        List<PriceFetcher.PriceEntry> hourlyData = PriceFetcher.aggregateToHourly(allData, barPoolMode);
        if (hourlyData.isEmpty()) {
            return null;
        }

        int currentIndex = CurrentPriceResolver.findCurrentIndex(allData);
        if (currentIndex < 0 || currentIndex >= allData.size()) {
            return null;
        }

        PriceFetcher.PriceEntry currentEntry = allData.get(currentIndex);
        OffsetDateTime currentHourStart = currentEntry.startTime.truncatedTo(ChronoUnit.HOURS);
        int currentHourIndex = findClosestHourIndex(hourlyData, currentHourStart);
        List<PriceFetcher.PriceEntry> barDisplayEntries = selectBarWindow(hourlyData, currentHourIndex, 24);
        if (barDisplayEntries.isEmpty()) {
            return null;
        }

        List<PriceFetcher.PriceEntry> graphDisplayEntries = getEntriesInRange(
                allData,
                barDisplayEntries.get(0).startTime,
                barDisplayEntries.get(barDisplayEntries.size() - 1).endTime
        );
        if (graphDisplayEntries.isEmpty()) {
            graphDisplayEntries = new ArrayList<>();
            graphDisplayEntries.add(currentEntry);
        }

        double barScaleMax = BarChartUtils.resolveScaleMax(barDisplayEntries);

        double graphMaxPrice = BarChartUtils.resolveScaleMax(graphDisplayEntries);
        PriceFetcher.PriceEntry maxEntry = null;
        PriceFetcher.PriceEntry minEntry = null;
        for (PriceFetcher.PriceEntry entry : graphDisplayEntries) {
            if (maxEntry == null || entry.pricePerKwh > maxEntry.pricePerKwh) {
                maxEntry = entry;
            }
            if (minEntry == null || entry.pricePerKwh < minEntry.pricePerKwh) {
                minEntry = entry;
            }
        }

        String maxText = "\u2191";
        if (maxEntry != null) {
            ZonedDateTime start = maxEntry.startTime.atZoneSameInstant(ZoneId.systemDefault());
            ZonedDateTime end = maxEntry.endTime.atZoneSameInstant(ZoneId.systemDefault());
            maxText = String.format(
                    "\u2191 %s %s",
                    PriceDisplayUtils.formatPrice(maxEntry.pricePerKwh, country, prefs),
                    formatTimeRange(start, end)
            );
        }

        String minText = "\u2193";
        if (minEntry != null) {
            ZonedDateTime start = minEntry.startTime.atZoneSameInstant(ZoneId.systemDefault());
            ZonedDateTime end = minEntry.endTime.atZoneSameInstant(ZoneId.systemDefault());
            minText = String.format(
                    "\u2193 %s %s",
                    PriceDisplayUtils.formatPrice(minEntry.pricePerKwh, country, prefs),
                    formatTimeRange(start, end)
            );
        }

        ZonedDateTime currentStart = currentEntry.startTime.atZoneSameInstant(ZoneId.systemDefault());
        ZonedDateTime currentEnd = currentEntry.endTime.atZoneSameInstant(ZoneId.systemDefault());
        String currentTimeText = String.format(
                "%02d:%02d-%02d:%02d:",
                currentStart.getHour(),
                currentStart.getMinute(),
                currentEnd.getHour(),
                currentEnd.getMinute()
        );

        return new RenderData(
                true,
                apiError,
                country,
                unitText,
                PriceDisplayUtils.formatPrice(currentEntry.pricePerKwh, country, prefs),
                currentTimeText,
                maxText,
                minText,
                new ArrayList<>(barDisplayEntries),
                new ArrayList<>(graphDisplayEntries),
                barScaleMax,
                graphMaxPrice
        );
    }

    static String formatTimeRange(ZonedDateTime start, ZonedDateTime end) {
        long minutes = Duration.between(start, end).toMinutes();
        if (minutes == 15) {
            return String.format("%02d:%02d", start.getHour(), start.getMinute());
        }
        return String.format(
                "%02d:%02d-%02d:%02d",
                start.getHour(),
                start.getMinute(),
                end.getHour(),
                end.getMinute()
        );
    }

    private static int findClosestHourIndex(List<PriceFetcher.PriceEntry> hourlyData, OffsetDateTime targetHourStart) {
        int currentHourIndex = 0;
        long bestDiff = Long.MAX_VALUE;
        for (int i = 0; i < hourlyData.size(); i++) {
            long diff = Math.abs(Duration.between(targetHourStart, hourlyData.get(i).startTime).toMinutes());
            if (diff < bestDiff) {
                bestDiff = diff;
                currentHourIndex = i;
            }
        }
        return currentHourIndex;
    }

    private static List<PriceFetcher.PriceEntry> selectBarWindow(List<PriceFetcher.PriceEntry> hourlyData,
                                                                 int currentHourIndex,
                                                                 int desiredCount) {
        if (hourlyData.isEmpty()) {
            return Collections.emptyList();
        }

        int safeDesiredCount = Math.min(desiredCount, hourlyData.size());
        int firstHourIndex = Math.max(0, currentHourIndex - 3);
        int lastHourIndex = Math.min(hourlyData.size() - 1, firstHourIndex + safeDesiredCount - 1);
        int actualCount = lastHourIndex - firstHourIndex + 1;
        if (actualCount < safeDesiredCount) {
            firstHourIndex = Math.max(0, Math.min(firstHourIndex, hourlyData.size() - safeDesiredCount));
            lastHourIndex = Math.min(hourlyData.size() - 1, firstHourIndex + safeDesiredCount - 1);
        }

        return new ArrayList<>(hourlyData.subList(firstHourIndex, lastHourIndex + 1));
    }

    private static List<PriceFetcher.PriceEntry> getEntriesInRange(List<PriceFetcher.PriceEntry> allEntries,
                                                                   OffsetDateTime start,
                                                                   OffsetDateTime end) {
        List<PriceFetcher.PriceEntry> entriesInRange = new ArrayList<>();
        for (PriceFetcher.PriceEntry entry : allEntries) {
            if (entry.endTime.isAfter(start) && entry.startTime.isBefore(end)) {
                entriesInRange.add(entry);
            } else if (entry.startTime.isAfter(end)) {
                break;
            }
        }
        return entriesInRange;
    }

    private static List<PriceFetcher.PriceEntry> buildSampleEntries() {
        List<PriceFetcher.PriceEntry> entries = new ArrayList<>();
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault())
                .truncatedTo(ChronoUnit.MINUTES);
        int alignedMinute = (now.getMinute() / 15) * 15;
        ZonedDateTime start = now.withMinute(alignedMinute).withSecond(0).withNano(0).minusHours(6);

        for (int i = 0; i < 96; i++) {
            double wave = 0.34 * Math.sin(((i - 8) / 96.0) * Math.PI * 4.0);
            double ripple = 0.17 * Math.cos((i / 96.0) * Math.PI * 14.0);
            double trend = (i / 95.0) * 0.22;

            PriceFetcher.PriceEntry entry = new PriceFetcher.PriceEntry();
            entry.startTime = start.plusMinutes((long) i * 15L).toOffsetDateTime();
            entry.endTime = entry.startTime.plusMinutes(15);
            entry.pricePerKwh = Math.max(0.48, 1.08 + wave + ripple + trend);
            entries.add(entry);
        }

        return entries;
    }
}
