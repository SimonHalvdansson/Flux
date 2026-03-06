package io.github.simonhalvdansson.flux;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;

/**
 * Helper utilities related to widget instance bookkeeping.
 */
final class WidgetPresenceUtils {

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
        int[] ids = manager.getAppWidgetIds(componentName);
        return ids != null && ids.length > 0;
    }
}

