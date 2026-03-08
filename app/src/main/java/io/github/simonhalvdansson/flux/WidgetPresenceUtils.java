package io.github.simonhalvdansson.flux;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;

/**
 * Helper utilities related to widget instance bookkeeping.
 */
final class WidgetPresenceUtils {

    private static final String TAG = "WidgetPresenceUtils";

    private WidgetPresenceUtils() {
        // Utility class
    }

    static boolean hasAnyWidgetInstances(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        return hasWidgets(context, manager, MainWidget.class)
                || hasWidgets(context, manager, ListWidget.class);
    }

    static void ensureUpdateJobScheduled(Context context) {
        PriceUpdateScheduler.schedulePriceUpdateJob(context);
    }

    private static boolean hasWidgets(Context context, AppWidgetManager manager, Class<?> widgetClass) {
        ComponentName componentName = new ComponentName(context, widgetClass);
        int[] ids;
        try {
            ids = manager.getAppWidgetIds(componentName);
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to query widget instances for " + widgetClass.getSimpleName(), e);
            return false;
        }
        return ids != null && ids.length > 0;
    }
}