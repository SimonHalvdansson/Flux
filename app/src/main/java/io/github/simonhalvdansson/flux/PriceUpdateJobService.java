package io.github.simonhalvdansson.flux;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

public class PriceUpdateJobService extends JobService {

    private static final String TAG = "PriceUpdateJobService";

    private static final String PREFS_NAME = "spot_price_prefs";
    private static final String KEY_JSON_DATA = "combined_json_data";
    public static final String KEY_SELECTED_AREA = "selected_area";
    public static final String KEY_SELECTED_COUNTRY = "selected_country";
    public static final String KEY_APPLY_STROMSTOTTE = "apply_stromstotte";
    public static final String KEY_APPLY_VAT = "apply_vat";
    public static final String KEY_CHART_MODE = "chart_mode";
    public static final String KEY_API_ERROR = "api_error";
    public static final String KEY_GRID_FEE = "grid_fee";

    private Thread workerThread; // or use an Executor

    @Override
    public boolean onStartJob(final JobParameters params) {
        Log.d(TAG, "onStartJob: beginning background fetch");

        // Return true to tell system "we're doing work in another thread"
        workerThread = new Thread(() -> {
            doBackgroundWork();
            // Finished
            jobFinished(params, false);
        });
        workerThread.start();

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        // Called if the job must be stopped prematurely
        if (workerThread != null) {
            workerThread.interrupt();
        }
        return true; // reschedule?
    }

    private void doBackgroundWork() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String country = prefs.getString(KEY_SELECTED_COUNTRY, "NO");
        List<RegionConfig.Area> availableAreas = RegionConfig.getAreas(country);
        String defaultArea = !availableAreas.isEmpty() ? availableAreas.get(0).getCode() : "NO1";
        String area = prefs.getString(KEY_SELECTED_AREA, defaultArea);
        if (RegionConfig.getEicCodeForArea(area) == null) {
            area = defaultArea;
            if (area != null) {
                prefs.edit().putString(KEY_SELECTED_AREA, area).apply();
            }
        }

        if (area == null) {
            Log.e(TAG, "No valid area configured; aborting fetch");
            return;
        }

        // 1) fetch all currently available entries from mirror.
        PriceFetcher.PriceFetchResult result = PriceFetcher.fetchAvailablePrices(country, area);
        boolean apiError = result.apiError;

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
            Log.w(TAG, "No current/future entries available; marking API error to avoid showing stale prices");
            apiError = true;
        }

        // 2) combine
        JSONArray combined = new JSONArray();
        try {
            for (PriceFetcher.PriceEntry e : result.entries) {
                JSONObject obj = new JSONObject();
                obj.put("price_per_kWh", e.pricePerKwh);
                obj.put("time_start", e.startTime.toString());
                obj.put("time_end",   e.endTime.toString());
                combined.put(obj);
            }
        } catch (JSONException ex) {
            Log.e(TAG, "JSON building error", ex);
            apiError = true;
        }

        if (combined.length() == 0) {
            apiError = true;
        }

        // 3) store in SharedPreferences
        prefs.edit()
                .putString(KEY_JSON_DATA, combined.toString())
                .putBoolean(KEY_API_ERROR, apiError)
                .apply();

        Log.d(TAG, "Data fetched & stored. combined.length=" + combined.length());

        // 4) trigger widget update
        MainWidget.updateAllWidgets(getApplicationContext());
        ListWidget.updateAllWidgets(getApplicationContext());
    }
}

