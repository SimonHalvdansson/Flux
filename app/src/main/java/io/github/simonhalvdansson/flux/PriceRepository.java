package io.github.simonhalvdansson.flux;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

public final class PriceRepository {

    private static final String TAG = "PriceRepository";

    public static final String PREFS_NAME = "spot_price_prefs";
    public static final String KEY_JSON_DATA = "combined_json_data";

    private PriceRepository() {
    }

    public static SharedPreferences getPreferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static void refreshCachedPrices(Context context) {
        Context appContext = context.getApplicationContext();
        SharedPreferences prefs = getPreferences(appContext);

        String country = prefs.getString(PriceUpdateJobService.KEY_SELECTED_COUNTRY, "NO");
        List<RegionConfig.Area> availableAreas = RegionConfig.getAreas(country);
        String defaultArea = !availableAreas.isEmpty() ? availableAreas.get(0).getCode() : "NO1";
        String area = prefs.getString(PriceUpdateJobService.KEY_SELECTED_AREA, defaultArea);
        if (RegionConfig.getEicCodeForArea(area) == null) {
            area = defaultArea;
            if (area != null) {
                prefs.edit().putString(PriceUpdateJobService.KEY_SELECTED_AREA, area).apply();
            }
        }

        if (area == null) {
            Log.e(TAG, "No valid area configured; aborting fetch");
            prefs.edit()
                    .putString(KEY_JSON_DATA, "[]")
                    .putBoolean(PriceUpdateJobService.KEY_API_ERROR, true)
                    .apply();
            return;
        }

        PriceFetcher.PriceFetchResult result = PriceFetcher.fetchAvailablePrices(country, area);
        boolean apiError = result.apiError;
        Log.d(TAG, PriceFetcher.describeEntriesForLog(
                "refreshCachedPrices fetched country=" + country + " area=" + area,
                result.entries
        ));

        ZoneId zoneId = RegionConfig.getZoneId(country);
        OffsetDateTime now = OffsetDateTime.now(zoneId);
        boolean hasCurrentOrFutureEntry = false;
        for (PriceFetcher.PriceEntry entry : result.entries) {
            if (entry != null && entry.endTime != null && entry.endTime.isAfter(now)) {
                hasCurrentOrFutureEntry = true;
                break;
            }
        }
        if (!hasCurrentOrFutureEntry) {
            Log.w(TAG, "No current/future entries available; marking API error to avoid stale prices");
            apiError = true;
        }

        JSONArray combined = new JSONArray();
        try {
            for (PriceFetcher.PriceEntry entry : result.entries) {
                JSONObject obj = new JSONObject();
                obj.put("price_per_kWh", entry.pricePerKwh);
                if (!Double.isNaN(entry.pricePerKwhEur)) {
                    obj.put("price_per_kwh_eur", entry.pricePerKwhEur);
                }
                if (!Double.isNaN(entry.exchangeRatePerEur)) {
                    obj.put("exchange_rate_per_eur", entry.exchangeRatePerEur);
                }
                if (entry.currency != null && !entry.currency.isEmpty()) {
                    obj.put("currency", entry.currency);
                }
                obj.put("time_start", entry.startTime.toString());
                obj.put("time_end", entry.endTime.toString());
                combined.put(obj);
            }
        } catch (JSONException e) {
            Log.e(TAG, "JSON building error", e);
            apiError = true;
        }

        if (combined.length() == 0) {
            apiError = true;
        }

        prefs.edit()
                .putString(KEY_JSON_DATA, combined.toString())
                .putBoolean(PriceUpdateJobService.KEY_API_ERROR, apiError)
                .apply();

        Log.d(TAG, "refreshCachedPrices storedEntryCount=" + combined.length() + " apiError=" + apiError);

        MainWidget.updateAllWidgets(appContext);
        ListWidget.updateAllWidgets(appContext);
    }
}
