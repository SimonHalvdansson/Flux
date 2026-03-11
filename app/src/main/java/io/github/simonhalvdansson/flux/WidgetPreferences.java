package io.github.simonhalvdansson.flux;

import android.appwidget.AppWidgetManager;
import android.content.SharedPreferences;

public final class WidgetPreferences {

    public static final String KEY_MAIN_BAR_POOL_MODE = "main_bar_pool_mode";
    public static final String KEY_LIST_INCREMENT_MINUTES = "list_increment_minutes";
    public static final String KEY_LIST_POOL_MODE = "list_pool_mode";
    private static final String KEY_WIDGET_CHART_MODE = "widget_chart_mode";
    private static final String KEY_WIDGET_MAIN_BAR_POOL_MODE = "widget_main_bar_pool_mode";
    private static final String KEY_WIDGET_LIST_INCREMENT_MINUTES = "widget_list_increment_minutes";
    private static final String KEY_WIDGET_LIST_POOL_MODE = "widget_list_pool_mode";

    public static final int CHART_MODE_BARS = 0;
    public static final int CHART_MODE_GRAPH = 1;
    public static final int CHART_MODE_LINES = 2;

    public static final int POOL_MODE_AVERAGE = 0;
    public static final int POOL_MODE_MIN = 1;
    public static final int POOL_MODE_MAX = 2;

    public static final int INCREMENT_15_MINUTES = 15;
    public static final int INCREMENT_30_MINUTES = 30;
    public static final int INCREMENT_60_MINUTES = 60;
    public static final int INCREMENT_120_MINUTES = 120;

    private WidgetPreferences() {
    }

    public static int getChartMode(SharedPreferences prefs) {
        int mode = prefs.getInt(PriceUpdateJobService.KEY_CHART_MODE, CHART_MODE_BARS);
        return sanitizeChartMode(mode);
    }

    public static int getChartMode(SharedPreferences prefs, int appWidgetId) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            return getChartMode(prefs);
        }
        int mode = prefs.getInt(widgetKey(KEY_WIDGET_CHART_MODE, appWidgetId), getChartMode(prefs));
        return sanitizeChartMode(mode);
    }

    public static void setChartMode(SharedPreferences prefs, int appWidgetId, int chartMode) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            return;
        }
        prefs.edit()
                .putInt(widgetKey(KEY_WIDGET_CHART_MODE, appWidgetId), sanitizeChartMode(chartMode))
                .apply();
    }

    public static int getMainBarPoolMode(SharedPreferences prefs) {
        int mode = prefs.getInt(KEY_MAIN_BAR_POOL_MODE, POOL_MODE_AVERAGE);
        return sanitizePoolMode(mode);
    }

    public static int getMainBarPoolMode(SharedPreferences prefs, int appWidgetId) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            return getMainBarPoolMode(prefs);
        }
        int mode = prefs.getInt(
                widgetKey(KEY_WIDGET_MAIN_BAR_POOL_MODE, appWidgetId),
                getMainBarPoolMode(prefs)
        );
        return sanitizePoolMode(mode);
    }

    public static void setMainBarPoolMode(SharedPreferences prefs, int appWidgetId, int poolMode) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            return;
        }
        prefs.edit()
                .putInt(widgetKey(KEY_WIDGET_MAIN_BAR_POOL_MODE, appWidgetId), sanitizePoolMode(poolMode))
                .apply();
    }

    public static int getListIncrementMinutes(SharedPreferences prefs) {
        int minutes = prefs.getInt(KEY_LIST_INCREMENT_MINUTES, INCREMENT_15_MINUTES);
        return sanitizeIncrementMinutes(minutes);
    }

    public static int getListIncrementMinutes(SharedPreferences prefs, int appWidgetId) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            return getListIncrementMinutes(prefs);
        }
        int minutes = prefs.getInt(
                widgetKey(KEY_WIDGET_LIST_INCREMENT_MINUTES, appWidgetId),
                getListIncrementMinutes(prefs)
        );
        return sanitizeIncrementMinutes(minutes);
    }

    public static void setListIncrementMinutes(SharedPreferences prefs, int appWidgetId, int incrementMinutes) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            return;
        }
        prefs.edit()
                .putInt(
                        widgetKey(KEY_WIDGET_LIST_INCREMENT_MINUTES, appWidgetId),
                        sanitizeIncrementMinutes(incrementMinutes)
                )
                .apply();
    }

    public static int getListPoolMode(SharedPreferences prefs) {
        int mode = prefs.getInt(KEY_LIST_POOL_MODE, POOL_MODE_AVERAGE);
        return sanitizePoolMode(mode);
    }

    public static int getListPoolMode(SharedPreferences prefs, int appWidgetId) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            return getListPoolMode(prefs);
        }
        int mode = prefs.getInt(
                widgetKey(KEY_WIDGET_LIST_POOL_MODE, appWidgetId),
                getListPoolMode(prefs)
        );
        return sanitizePoolMode(mode);
    }

    public static void setListPoolMode(SharedPreferences prefs, int appWidgetId, int poolMode) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            return;
        }
        prefs.edit()
                .putInt(widgetKey(KEY_WIDGET_LIST_POOL_MODE, appWidgetId), sanitizePoolMode(poolMode))
                .apply();
    }

    public static void clearWidgetPreferences(SharedPreferences prefs, int appWidgetId) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            return;
        }
        prefs.edit()
                .remove(widgetKey(KEY_WIDGET_CHART_MODE, appWidgetId))
                .remove(widgetKey(KEY_WIDGET_MAIN_BAR_POOL_MODE, appWidgetId))
                .remove(widgetKey(KEY_WIDGET_LIST_INCREMENT_MINUTES, appWidgetId))
                .remove(widgetKey(KEY_WIDGET_LIST_POOL_MODE, appWidgetId))
                .apply();
    }

    private static int sanitizeChartMode(int mode) {
        if (mode == CHART_MODE_GRAPH || mode == CHART_MODE_LINES) {
            return mode;
        }
        return CHART_MODE_BARS;
    }

    private static int sanitizePoolMode(int mode) {
        if (mode == POOL_MODE_MIN || mode == POOL_MODE_MAX) {
            return mode;
        }
        return POOL_MODE_AVERAGE;
    }

    private static int sanitizeIncrementMinutes(int minutes) {
        if (minutes == INCREMENT_30_MINUTES
                || minutes == INCREMENT_60_MINUTES
                || minutes == INCREMENT_120_MINUTES) {
            return minutes;
        }
        return INCREMENT_15_MINUTES;
    }

    private static String widgetKey(String baseKey, int appWidgetId) {
        return baseKey + "_" + appWidgetId;
    }

    public static boolean listPoolingEnabled(int incrementMinutes) {
        return incrementMinutes != INCREMENT_15_MINUTES;
    }
}
