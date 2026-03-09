package io.github.simonhalvdansson.flux;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Helper class to fetch and parse prices from the ENTSOE-Mirror repository.
 */
public class PriceFetcher {

    private static final String TAG = "PriceFetcher";
    private static final DateTimeFormatter LOG_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    public static final double VAT_RATE = 1.25;
    public static final double STROMSTOTTE_THRESHOLD = 0.77;
    public static final double STROMSTOTTE_PERCENT = 0.9;
    private static final DateTimeFormatter ISO_OFFSET_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final String MIRROR_BASE_URL =
            "https://raw.githubusercontent.com/SimonHalvdansson/ENTSOE-Mirror/refs/heads/main/data/";

    private static final Map<String, String> COUNTRY_CODE_TO_SLUG = new HashMap<>();

    static {
        COUNTRY_CODE_TO_SLUG.put("AT", "austria");
        COUNTRY_CODE_TO_SLUG.put("BE", "belgium");
        COUNTRY_CODE_TO_SLUG.put("BG", "bulgaria");
        COUNTRY_CODE_TO_SLUG.put("CH", "switzerland");
        COUNTRY_CODE_TO_SLUG.put("CZ", "czech-republic");
        COUNTRY_CODE_TO_SLUG.put("DE", "germany");
        COUNTRY_CODE_TO_SLUG.put("DK", "denmark");
        COUNTRY_CODE_TO_SLUG.put("EE", "estonia");
        COUNTRY_CODE_TO_SLUG.put("ES", "spain");
        COUNTRY_CODE_TO_SLUG.put("FI", "finland");
        COUNTRY_CODE_TO_SLUG.put("FR", "france");
        COUNTRY_CODE_TO_SLUG.put("GR", "greece");
        COUNTRY_CODE_TO_SLUG.put("HR", "croatia");
        COUNTRY_CODE_TO_SLUG.put("HU", "hungary");
        COUNTRY_CODE_TO_SLUG.put("IT", "italy");
        COUNTRY_CODE_TO_SLUG.put("LT", "lithuania");
        COUNTRY_CODE_TO_SLUG.put("LU", "luxembourg");
        COUNTRY_CODE_TO_SLUG.put("LV", "latvia");
        COUNTRY_CODE_TO_SLUG.put("NL", "netherlands");
        COUNTRY_CODE_TO_SLUG.put("NO", "norway");
        COUNTRY_CODE_TO_SLUG.put("PL", "poland");
        COUNTRY_CODE_TO_SLUG.put("PT", "portugal");
        COUNTRY_CODE_TO_SLUG.put("RO", "romania");
        COUNTRY_CODE_TO_SLUG.put("RS", "serbia");
        COUNTRY_CODE_TO_SLUG.put("SE", "sweden");
        COUNTRY_CODE_TO_SLUG.put("SI", "slovenia");
        COUNTRY_CODE_TO_SLUG.put("SK", "slovakia");
    }

    // Data container
    public static class PriceEntry {
        public double pricePerKwh;
        public OffsetDateTime startTime;
        public OffsetDateTime endTime;
    }

    public static class PriceFetchResult {
        public final List<PriceEntry> entries;
        public final boolean apiError;

        public PriceFetchResult(List<PriceEntry> entries, boolean apiError) {
            this.entries = entries;
            this.apiError = apiError;
        }
    }

    private static class MirrorParseResult {
        final List<PriceEntry> entries;
        final boolean apiError;

        MirrorParseResult(List<PriceEntry> entries, boolean apiError) {
            this.entries = entries;
            this.apiError = apiError;
        }
    }

    public static double getVatRate(String country) {
        return RegionConfig.getVatMultiplier(country);
    }

    /**
     * Fetches prices for one local date, country and area from ENTSOE-Mirror.
     */
    public static PriceFetchResult fetchPrices(int year, int month, int day, String country, String area) {
        PriceFetchResult all = fetchAvailablePrices(country, area);
        if (all.entries.isEmpty()) {
            return all;
        }

        LocalDate targetDate = LocalDate.of(year, month, day);
        List<PriceEntry> list = new ArrayList<>();
        for (PriceEntry entry : all.entries) {
            if (entry.startTime != null && targetDate.equals(entry.startTime.toLocalDate())) {
                list.add(entry);
            }
        }
        boolean apiError = all.apiError || list.isEmpty();
        return new PriceFetchResult(list, apiError);
    }

    /**
     * Fetches all available prices for the selected country/area from ENTSOE-Mirror.
     */
    public static PriceFetchResult fetchAvailablePrices(String country, String area) {
        List<PriceEntry> list = new ArrayList<>();
        boolean apiError = false;

        String countrySlug = COUNTRY_CODE_TO_SLUG.get(country);
        if (countrySlug == null) {
            Log.e(TAG, "Unsupported country code: " + country);
            return new PriceFetchResult(list, true);
        }

        ZoneId zoneId = RegionConfig.getZoneId(country);
        if (zoneId == null) {
            Log.e(TAG, "Unknown country: " + country);
            return new PriceFetchResult(list, true);
        }

        String url = MIRROR_BASE_URL + countrySlug + ".json";
        Log.d(TAG, "Fetching mirror data: " + url + " (area=" + area + ")");

        try {
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "Flux-Android")
                    .build();

            try (Response response = HttpClientHolder.CLIENT.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    Log.w(TAG, "HTTP code: " + response.code() + " for " + url);
                    return new PriceFetchResult(list, true);
                }

                ResponseBody body = response.body();
                if (body == null) {
                    Log.w(TAG, "Empty response body for " + url);
                    return new PriceFetchResult(list, true);
                }

                MirrorParseResult parsed = parseMirrorResponse(body.string(), zoneId, area);
                list.addAll(parsed.entries);
                apiError = parsed.apiError;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fetching prices from mirror", e);
            apiError = true;
        }

        if (list.isEmpty()) {
            apiError = true;
        }

        return new PriceFetchResult(list, apiError);
    }

    private static class HttpClientHolder {
        private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build();
    }

    private static MirrorParseResult parseMirrorResponse(String responseString,
                                                         ZoneId zoneId,
                                                         String areaCode) {
        List<PriceEntry> entries = new ArrayList<>();
        boolean apiError = false;

        if (responseString == null || responseString.isEmpty()) {
            return new MirrorParseResult(entries, true);
        }

        try {
            JSONObject root = new JSONObject(responseString);
            if (!root.isNull("error") && root.optString("error", "").length() > 0) {
                apiError = true;
            }

            JSONObject selectedArea = selectAreaObject(root, areaCode);
            if (selectedArea == null) {
                Log.w(TAG, "Area not found in mirror payload: " + areaCode);
                return new MirrorParseResult(entries, true);
            }

            if (!selectedArea.isNull("error") && selectedArea.optString("error", "").length() > 0) {
                apiError = true;
            }

            JSONArray prices = selectedArea.optJSONArray("prices");
            if (prices == null) {
                Log.w(TAG, "Missing prices array for area: " + areaCode);
                return new MirrorParseResult(entries, true);
            }

            for (int i = 0; i < prices.length(); i++) {
                JSONObject obj = prices.optJSONObject(i);
                if (obj == null) {
                    continue;
                }

                double pricePerKwh = obj.optDouble("price_per_kwh", Double.NaN);
                if (Double.isNaN(pricePerKwh)) {
                    pricePerKwh = obj.optDouble("price_per_kwh_eur", Double.NaN);
                }
                if (Double.isNaN(pricePerKwh)) {
                    continue;
                }

                OffsetDateTime start = parseMirrorTime(obj.optString("start_local", null),
                        obj.optString("start_utc", null), zoneId);
                OffsetDateTime end = parseMirrorTime(obj.optString("end_local", null),
                        obj.optString("end_utc", null), zoneId);
                if (start == null || end == null) {
                    continue;
                }

                PriceEntry entry = new PriceEntry();
                entry.pricePerKwh = pricePerKwh;
                entry.startTime = start;
                entry.endTime = end;
                entries.add(entry);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse mirror response", e);
            return new MirrorParseResult(entries, true);
        }

        entries.sort(Comparator.comparing(o -> o.startTime));
        Log.d(TAG, describeEntriesForLog("parseMirrorResponse area=" + areaCode, entries));
        return new MirrorParseResult(entries, apiError);
    }

    private static JSONObject selectAreaObject(JSONObject root, String areaCode) {
        JSONArray areas = root.optJSONArray("areas");
        if (areas != null) {
            for (int i = 0; i < areas.length(); i++) {
                JSONObject area = areas.optJSONObject(i);
                if (area == null) {
                    continue;
                }
                if (areaCode != null && areaCode.equals(area.optString("area_code", ""))) {
                    return area;
                }
            }
        }

        String rootAreaCode = root.optString("area_code", null);
        if (rootAreaCode != null && rootAreaCode.equals(areaCode)) {
            return root;
        }

        if (areas != null) {
            for (int i = 0; i < areas.length(); i++) {
                JSONObject area = areas.optJSONObject(i);
                if (area != null && area.optJSONArray("prices") != null && area.optJSONArray("prices").length() > 0) {
                    return area;
                }
            }
            if (areas.length() > 0) {
                return areas.optJSONObject(0);
            }
        }

        return root;
    }

    private static OffsetDateTime parseMirrorTime(String localIso, String utcIso, ZoneId zoneId) {
        try {
            if (localIso != null && localIso.length() > 0) {
                return OffsetDateTime.parse(localIso, ISO_OFFSET_FORMATTER);
            }
            if (utcIso != null && utcIso.length() > 0) {
                return OffsetDateTime.parse(utcIso, ISO_OFFSET_FORMATTER)
                        .atZoneSameInstant(zoneId)
                        .toOffsetDateTime();
            }
        } catch (Exception ignore) {
            return null;
        }
        return null;
    }

    /**
     * Parses the stored JSON array string into PriceEntry objects.
     */
    public static List<PriceEntry> parseCombinedJson(String combinedJson) {
        List<PriceEntry> list = new ArrayList<>();
        if (combinedJson == null || combinedJson.trim().length() == 0) {
            return list;
        }

        try {
            JSONArray arr = new JSONArray(combinedJson);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                PriceEntry e = new PriceEntry();
                e.pricePerKwh = obj.optDouble("price_per_kWh", 0.0);
                String startTimeStr = obj.optString("time_start");
                String endTimeStr = obj.optString("time_end");
                e.startTime = OffsetDateTime.parse(startTimeStr, ISO_OFFSET_FORMATTER);
                e.endTime = OffsetDateTime.parse(endTimeStr, ISO_OFFSET_FORMATTER);
                list.add(e);
            }
        } catch (JSONException e) {
            Log.e(TAG, "JSON parse error in parseCombinedJson()", e);
        }

        Log.d(TAG, describeEntriesForLog("parseCombinedJson", list));
        return list;
    }

    static String describeEntriesForLog(String label, List<PriceEntry> entries) {
        StringBuilder sb = new StringBuilder(label);
        sb.append(" count=").append(entries == null ? 0 : entries.size());
        if (entries == null || entries.isEmpty()) {
            return sb.toString();
        }

        PriceEntry first = entries.get(0);
        PriceEntry last = entries.get(entries.size() - 1);
        sb.append(" range=")
                .append(formatLogTime(first.startTime))
                .append(" -> ")
                .append(formatLogTime(last.endTime));

        int anomalyCount = 0;
        StringBuilder anomalies = new StringBuilder();
        for (int i = 1; i < entries.size(); i++) {
            PriceEntry previous = entries.get(i - 1);
            PriceEntry current = entries.get(i);
            long expectedMinutes = Duration.between(previous.startTime, previous.endTime).toMinutes();
            long actualMinutes = Duration.between(previous.startTime, current.startTime).toMinutes();
            if (expectedMinutes > 0 && actualMinutes != expectedMinutes) {
                anomalyCount++;
                if (anomalyCount <= 4) {
                    if (anomalies.length() == 0) {
                        anomalies.append(" anomalies=");
                    } else {
                        anomalies.append(", ");
                    }
                    anomalies.append(formatLogTime(previous.startTime))
                            .append("->")
                            .append(formatLogTime(current.startTime))
                            .append(" actual=")
                            .append(actualMinutes)
                            .append("m expected=")
                            .append(expectedMinutes)
                            .append("m");
                }
            }
        }
        if (anomalyCount == 0) {
            sb.append(" anomalies=none");
        } else {
            sb.append(" anomalyCount=").append(anomalyCount).append(anomalies);
        }
        return sb.toString();
    }

    static String describeEntryTimesForLog(List<PriceEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return "[]";
        }

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            PriceEntry entry = entries.get(i);
            sb.append(formatLogTime(entry.startTime))
                    .append("=")
                    .append(String.format(java.util.Locale.US, "%.4f", entry.pricePerKwh));
        }
        sb.append("]");
        return sb.toString();
    }

    private static String formatLogTime(OffsetDateTime time) {
        if (time == null) {
            return "null";
        }
        return time.atZoneSameInstant(ZoneId.systemDefault()).format(LOG_TIME_FORMATTER);
    }

    public static List<PriceEntry> aggregateToHourly(List<PriceEntry> entries) {
        return aggregateToHourly(entries, WidgetPreferences.POOL_MODE_AVERAGE);
    }

    public static List<PriceEntry> aggregateToHourly(List<PriceEntry> entries, int poolMode) {
        return aggregateAligned(entries, 60, poolMode);
    }

    public static List<PriceEntry> aggregateConsecutive(List<PriceEntry> entries, int intervalMinutes, int poolMode) {
        List<PriceEntry> sorted = new ArrayList<>();
        for (PriceEntry entry : entries) {
            if (entry != null && entry.startTime != null && entry.endTime != null) {
                sorted.add(entry);
            }
        }
        sorted.sort(Comparator.comparing(o -> o.startTime));

        if (intervalMinutes <= WidgetPreferences.INCREMENT_15_MINUTES) {
            return sorted;
        }

        List<PriceEntry> aggregated = new ArrayList<>();
        OffsetDateTime bucketStart = null;
        OffsetDateTime bucketEnd = null;
        OffsetDateTime lastEnd = null;
        double weightedTotal = 0.0;
        long totalMinutes = 0L;
        double minPrice = Double.POSITIVE_INFINITY;
        double maxPrice = Double.NEGATIVE_INFINITY;

        for (PriceEntry entry : sorted) {
            long minutes = Duration.between(entry.startTime, entry.endTime).toMinutes();
            if (minutes <= 0) {
                continue;
            }

            if (bucketStart == null) {
                bucketStart = entry.startTime;
                bucketEnd = bucketStart.plusMinutes(intervalMinutes);
            } else if (!entry.startTime.isBefore(bucketEnd)) {
                addAggregate(aggregated, bucketStart, lastEnd, weightedTotal, totalMinutes, minPrice, maxPrice, poolMode);
                bucketStart = entry.startTime;
                bucketEnd = bucketStart.plusMinutes(intervalMinutes);
                weightedTotal = 0.0;
                totalMinutes = 0L;
                minPrice = Double.POSITIVE_INFINITY;
                maxPrice = Double.NEGATIVE_INFINITY;
            }

            weightedTotal += entry.pricePerKwh * minutes;
            totalMinutes += minutes;
            minPrice = Math.min(minPrice, entry.pricePerKwh);
            maxPrice = Math.max(maxPrice, entry.pricePerKwh);
            lastEnd = entry.endTime;
        }

        addAggregate(aggregated, bucketStart, lastEnd, weightedTotal, totalMinutes, minPrice, maxPrice, poolMode);
        return aggregated;
    }

    private static List<PriceEntry> aggregateAligned(List<PriceEntry> entries, int intervalMinutes, int poolMode) {
        Map<OffsetDateTime, double[]> aggregates = new LinkedHashMap<>();
        for (PriceEntry entry : entries) {
            if (entry == null || entry.startTime == null || entry.endTime == null) {
                continue;
            }

            long minutes = Duration.between(entry.startTime, entry.endTime).toMinutes();
            if (minutes <= 0) {
                continue;
            }

            OffsetDateTime bucketStart = getAlignedBucketStart(entry.startTime, intervalMinutes);
            double[] agg = aggregates.computeIfAbsent(
                    bucketStart,
                    k -> new double[] {0.0, 0.0, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}
            );
            agg[0] += entry.pricePerKwh * minutes;
            agg[1] += minutes;
            agg[2] = Math.min(agg[2], entry.pricePerKwh);
            agg[3] = Math.max(agg[3], entry.pricePerKwh);
        }

        List<PriceEntry> aggregated = new ArrayList<>(aggregates.size());
        for (Map.Entry<OffsetDateTime, double[]> mapEntry : aggregates.entrySet()) {
            double[] agg = mapEntry.getValue();
            if (agg[1] <= 0) {
                continue;
            }

            PriceEntry aggregatedEntry = new PriceEntry();
            aggregatedEntry.startTime = mapEntry.getKey();
            aggregatedEntry.endTime = mapEntry.getKey().plusMinutes(intervalMinutes);
            aggregatedEntry.pricePerKwh = resolveAggregatedPrice(agg[0], agg[1], agg[2], agg[3], poolMode);
            aggregated.add(aggregatedEntry);
        }

        aggregated.sort(Comparator.comparing(o -> o.startTime));
        return aggregated;
    }

    private static OffsetDateTime getAlignedBucketStart(OffsetDateTime startTime, int intervalMinutes) {
        OffsetDateTime localStart = startTime.atZoneSameInstant(ZoneId.systemDefault()).toOffsetDateTime();
        OffsetDateTime dayStart = localStart.toLocalDate().atStartOfDay().atOffset(localStart.getOffset());
        int minuteOfDay = (localStart.getHour() * 60) + localStart.getMinute();
        int bucketMinuteOfDay = (minuteOfDay / intervalMinutes) * intervalMinutes;
        return dayStart.plusMinutes(bucketMinuteOfDay);
    }

    private static void addAggregate(List<PriceEntry> output,
                                     OffsetDateTime start,
                                     OffsetDateTime end,
                                     double weightedTotal,
                                     long totalMinutes,
                                     double minPrice,
                                     double maxPrice,
                                     int poolMode) {
        if (start == null || end == null || totalMinutes <= 0) {
            return;
        }

        PriceEntry aggregate = new PriceEntry();
        aggregate.startTime = start;
        aggregate.endTime = end;
        aggregate.pricePerKwh = resolveAggregatedPrice(weightedTotal, totalMinutes, minPrice, maxPrice, poolMode);
        output.add(aggregate);
    }

    private static double resolveAggregatedPrice(double weightedTotal,
                                                 double totalMinutes,
                                                 double minPrice,
                                                 double maxPrice,
                                                 int poolMode) {
        if (poolMode == WidgetPreferences.POOL_MODE_MIN) {
            return minPrice;
        }
        if (poolMode == WidgetPreferences.POOL_MODE_MAX) {
            return maxPrice;
        }
        return weightedTotal / totalMinutes;
    }
}
