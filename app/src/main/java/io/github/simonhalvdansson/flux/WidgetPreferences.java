package io.github.simonhalvdansson.flux;

import android.content.SharedPreferences;

public final class WidgetPreferences {

    public static final String KEY_MAIN_BAR_POOL_MODE = "main_bar_pool_mode";
    public static final String KEY_LIST_INCREMENT_MINUTES = "list_increment_minutes";
    public static final String KEY_LIST_POOL_MODE = "list_pool_mode";

    public static final int CHART_MODE_BARS = 0;
    public static final int CHART_MODE_GRAPH = 1;

    public static final int POOL_MODE_AVERAGE = 0;
    public static final int POOL_MODE_MIN = 1;

    public static final int INCREMENT_15_MINUTES = 15;
    public static final int INCREMENT_30_MINUTES = 30;
    public static final int INCREMENT_60_MINUTES = 60;
    public static final int INCREMENT_120_MINUTES = 120;

    private WidgetPreferences() {
    }

    public static int getChartMode(SharedPreferences prefs) {
        return prefs.getInt(PriceUpdateJobService.KEY_CHART_MODE, CHART_MODE_BARS);
    }

    public static int getMainBarPoolMode(SharedPreferences prefs) {
        return prefs.getInt(KEY_MAIN_BAR_POOL_MODE, POOL_MODE_AVERAGE);
    }

    public static int getListIncrementMinutes(SharedPreferences prefs) {
        return prefs.getInt(KEY_LIST_INCREMENT_MINUTES, INCREMENT_15_MINUTES);
    }

    public static int getListPoolMode(SharedPreferences prefs) {
        return prefs.getInt(KEY_LIST_POOL_MODE, POOL_MODE_AVERAGE);
    }

    public static boolean listPoolingEnabled(int incrementMinutes) {
        return incrementMinutes != INCREMENT_15_MINUTES;
    }
}