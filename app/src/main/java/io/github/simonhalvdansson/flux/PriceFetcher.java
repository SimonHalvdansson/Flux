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

        return list;
    }

    public static List<PriceEntry> aggregateToHourly(List<PriceEntry> entries) {
        Map<OffsetDateTime, double[]> aggregates = new LinkedHashMap<>();
        for (PriceEntry entry : entries) {
            if (entry == null || entry.startTime == null || entry.endTime == null) {
                continue;
            }

            OffsetDateTime hourStart = entry.startTime.truncatedTo(ChronoUnit.HOURS);
            long minutes = Duration.between(entry.startTime, entry.endTime).toMinutes();
            if (minutes <= 0) {
                continue;
            }

            double[] agg = aggregates.computeIfAbsent(hourStart, k -> new double[2]);
            agg[0] += entry.pricePerKwh * minutes;
            agg[1] += minutes;
        }

        List<PriceEntry> hourly = new ArrayList<>(aggregates.size());
        for (Map.Entry<OffsetDateTime, double[]> mapEntry : aggregates.entrySet()) {
            double[] agg = mapEntry.getValue();
            if (agg[1] <= 0) {
                continue;
            }

            PriceEntry hourlyEntry = new PriceEntry();
            hourlyEntry.startTime = mapEntry.getKey();
            hourlyEntry.endTime = mapEntry.getKey().plusHours(1);
            hourlyEntry.pricePerKwh = agg[0] / agg[1];
            hourly.add(hourlyEntry);
        }

        hourly.sort(Comparator.comparing(o -> o.startTime));
        return hourly;
    }
}

