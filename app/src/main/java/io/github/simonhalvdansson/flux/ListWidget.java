package io.github.simonhalvdansson.flux;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Widget showing a scrollable list of upcoming prices.
 */
public class ListWidget extends AppWidgetProvider {

    private static final String TAG = "ListWidget";

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        WidgetPresenceUtils.ensureUpdateJobScheduled(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (Intent.ACTION_CONFIGURATION_CHANGED.equals(intent.getAction())) {
            updateAllWidgets(context);
        }
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
        if (!WidgetPresenceUtils.hasAnyWidgetInstances(context)) {
            PriceUpdateScheduler.cancelPriceUpdateJob(context);
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, Bundle newOptions) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions);
        updateAppWidget(context, appWidgetManager, appWidgetId);
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        SharedPreferences prefs = PriceRepository.getPreferences(context);
        String combinedJson = prefs.getString(PriceRepository.KEY_JSON_DATA, null);
        String country = prefs.getString(PriceUpdateJobService.KEY_SELECTED_COUNTRY, "NO");
        boolean apiError = prefs.getBoolean(PriceUpdateJobService.KEY_API_ERROR, false);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.list_widget);

        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_DISABLE_CHART_ANIMATION, true);
        // Use a mutable PendingIntent so list clicks can launch the activity
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_MUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root_list, pendingIntent);
        views.setPendingIntentTemplate(R.id.list_view, pendingIntent);

        views.setViewVisibility(R.id.api_error_container, View.GONE);
        views.setViewVisibility(R.id.list_view, View.VISIBLE);
        views.setViewVisibility(R.id.current_price_header_list, View.VISIBLE);
        views.setViewVisibility(R.id.current_price_imageview, View.VISIBLE);
        views.setViewVisibility(R.id.current_price_unit, View.VISIBLE);

        String unitText = PriceDisplayUtils.getUnitText(country);
        views.setTextViewText(R.id.current_price_unit, unitText);

        if (apiError || combinedJson == null || combinedJson.trim().isEmpty()) {
            showApiErrorState(appWidgetManager, appWidgetId, views);
            return;
        }

        List<PriceFetcher.PriceEntry> allData = CurrentPriceResolver.getAdjustedEntries(prefs);

        if (allData.isEmpty()) {
            Log.w(TAG, "No price data available; showing API error state.");
            showApiErrorState(appWidgetManager, appWidgetId, views);
            return;
        }

        int currentIndex = CurrentPriceResolver.findCurrentIndex(allData);

        PriceFetcher.PriceEntry currentEntry = allData.get(currentIndex);
        double currentPrice = currentEntry.pricePerKwh;
        String priceText = PriceDisplayUtils.formatPrice(currentPrice, country);
        views.setTextViewText(R.id.current_price_imageview, priceText);

        ZonedDateTime start = currentEntry.startTime.atZoneSameInstant(ZoneId.systemDefault());
        ZonedDateTime end = currentEntry.endTime.atZoneSameInstant(ZoneId.systemDefault());
        String timeText = String.format("%02d:%02d-%02d:%02d:", start.getHour(), start.getMinute(), end.getHour(), end.getMinute());
        views.setTextViewText(R.id.current_price_header_list, timeText);

        Bundle options = appWidgetManager.getAppWidgetOptions(appWidgetId);
        int minWidth = options != null ? options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) : 0;
        boolean showBar = minWidth >= ListWidgetService.BAR_VISIBILITY_THRESHOLD_DP;

        Intent svcIntent = new Intent(context, ListWidgetService.class);
        svcIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        svcIntent.putExtra(ListWidgetService.EXTRA_SHOW_BAR, showBar);
        svcIntent.setData(Uri.parse(svcIntent.toUri(Intent.URI_INTENT_SCHEME)));
        views.setRemoteAdapter(R.id.list_view, svcIntent);

        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.list_view);
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    private static void showApiErrorState(AppWidgetManager appWidgetManager, int appWidgetId, RemoteViews views) {
        views.setTextViewText(R.id.api_error_text, "ENTSO-E API error");

        views.setViewVisibility(R.id.current_price_header_list, View.GONE);
        views.setViewVisibility(R.id.current_price_imageview, View.GONE);
        views.setViewVisibility(R.id.current_price_unit, View.GONE);
        views.setViewVisibility(R.id.list_view, View.GONE);
        views.setViewVisibility(R.id.api_error_container, View.VISIBLE);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    public static void updateAllWidgets(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, ListWidget.class));
        if (ids != null && ids.length > 0) {
            manager.notifyAppWidgetViewDataChanged(ids, R.id.list_view);
            for (int id : ids) {
                updateAppWidget(context, manager, id);
            }
        }
    }
}

