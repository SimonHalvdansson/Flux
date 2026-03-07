package io.github.simonhalvdansson.flux;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;
import android.graphics.Bitmap;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Implementation of App Widget functionality.
 */
public class MainWidget extends AppWidgetProvider {

    private static final String TAG = "MainWidget";

    // All bar IDs. Must be exactly 24 for consistent indexing.
    private static final int[] barIds = {
            R.id.bar_0, R.id.bar_1, R.id.bar_2, R.id.bar_3,
            R.id.bar_4, R.id.bar_5, R.id.bar_6, R.id.bar_7,
            R.id.bar_8, R.id.bar_9, R.id.bar_10, R.id.bar_11,
            R.id.bar_12, R.id.bar_13, R.id.bar_14, R.id.bar_15,
            R.id.bar_16, R.id.bar_17, R.id.bar_18, R.id.bar_19,
            R.id.bar_20, R.id.bar_21, R.id.bar_22, R.id.bar_23
    };

    // 12 TextViews for time labels: time0..time11
    private static final int[] timeLabelIds = {
            R.id.time0,  R.id.time1,  R.id.time2,  R.id.time3,
            R.id.time4,  R.id.time5,  R.id.time6,  R.id.time7,
            R.id.time8,  R.id.time9,  R.id.time10, R.id.time11
    };

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        // Ensure the periodic background job keeps running while any widget exists
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
        // Only cancel when no widget instances of any type remain
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

    /**
     * Renders the widget using cached data from SharedPreferences.
     */
    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {

        SharedPreferences prefs = PriceRepository.getPreferences(context);
        String combinedJson = prefs.getString(PriceRepository.KEY_JSON_DATA, null);
        int chartMode = WidgetPreferences.getChartMode(prefs);
        int barPoolMode = WidgetPreferences.getMainBarPoolMode(prefs);
        boolean apiError = prefs.getBoolean(PriceUpdateJobService.KEY_API_ERROR, false);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.main_widget);
        // Reset visibility in case previous update showed API error state.
        views.setViewVisibility(R.id.api_error_container, View.GONE);
        views.setViewVisibility(R.id.current_price_header, View.VISIBLE);
        views.setViewVisibility(R.id.current_price_imageview, View.VISIBLE);
        views.setViewVisibility(R.id.current_price_unit, View.VISIBLE);
        views.setViewVisibility(R.id.max_price_text, View.VISIBLE);
        views.setViewVisibility(R.id.min_price_text, View.VISIBLE);

        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_DISABLE_CHART_ANIMATION, true);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent);

        // padding in px
        int dp10 = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 10, context.getResources().getDisplayMetrics()
        );
        int dp16 = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 16, context.getResources().getDisplayMetrics()
        );
        int dp4 = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 4, context.getResources().getDisplayMetrics()
        );
        int dp1 = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 1, context.getResources().getDisplayMetrics()
        );

        Bundle options = appWidgetManager.getAppWidgetOptions(appWidgetId);
        int maxHeightDp = options != null ? options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) : 0;


        // current height in dp from launcher
        Bundle opts = appWidgetManager.getAppWidgetOptions(appWidgetId);
        int hDp = 0;
        if (opts != null) {
            hDp = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
            if (hDp == 0) hDp = maxHeightDp;
        }

        boolean shortWidget = hDp > 0 && hDp < 120;
        int barMaxHeightDp = 110;
        if (maxHeightDp > 0) {
            if (chartMode == 0) { //bar mode
                barMaxHeightDp = shortWidget ? maxHeightDp - 80 : maxHeightDp - 110;
            } else {
                barMaxHeightDp = shortWidget ? maxHeightDp - 78 : maxHeightDp - 85;
            }
        }

        // set padding to 12dp if < 120dp
        if (shortWidget) {
            views.setViewPadding(R.id.widget_root, dp16, dp10, dp16, dp10);
            views.setViewPadding(R.id.widget_time_container, 0, dp1, 0, 0); // “top margin” ≈ top padding
        } else {
            views.setViewPadding(R.id.widget_root, dp16, dp16, dp16, dp16);
            views.setViewPadding(R.id.widget_time_container, 0, dp4, 0, 0); // “top margin” ≈ top padding

        }

        // If no data, placeholders
        if (apiError || combinedJson == null || combinedJson.trim().isEmpty()) {
            Log.w(TAG, "No cached data found or API error; showing error message");
            showApiErrorState(appWidgetManager, appWidgetId, views);
            return;
        }

        String country = prefs.getString(PriceUpdateJobService.KEY_SELECTED_COUNTRY, "NO");
        String unitText = PriceDisplayUtils.getUnitText(country);
        views.setTextViewText(R.id.current_price_unit, unitText);

        // Parse JSON
        List<PriceFetcher.PriceEntry> allData = CurrentPriceResolver.getAdjustedEntries(prefs);

        if (allData.isEmpty()) {
            showApiErrorState(appWidgetManager, appWidgetId, views);
            return;
        }

        List<PriceFetcher.PriceEntry> hourlyData = PriceFetcher.aggregateToHourly(allData, barPoolMode);
        if (hourlyData.isEmpty()) {
            showApiErrorState(appWidgetManager, appWidgetId, views);
            return;
        }

        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());

        int currentIndex = CurrentPriceResolver.findCurrentIndex(allData);

        PriceFetcher.PriceEntry currentEntry = allData.get(currentIndex);

        OffsetDateTime currentHourStart = currentEntry.startTime.truncatedTo(ChronoUnit.HOURS);
        int currentHourIndex = 0;
        long bestDiff = Long.MAX_VALUE;
        for (int i = 0; i < hourlyData.size(); i++) {
            PriceFetcher.PriceEntry e = hourlyData.get(i);
            long diff = Math.abs(Duration.between(currentHourStart, e.startTime).toMinutes());
            if (diff < bestDiff) {
                bestDiff = diff;
                currentHourIndex = i;
            }
        }

        int desiredCount = Math.min(24, hourlyData.size());
        int firstHourIndex = Math.max(0, currentHourIndex - 3);
        int lastHourIndex = Math.min(hourlyData.size() - 1, firstHourIndex + desiredCount - 1);
        int actualCount = lastHourIndex - firstHourIndex + 1;
        if (actualCount < desiredCount) {
            firstHourIndex = Math.max(0, Math.min(firstHourIndex, hourlyData.size() - desiredCount));
            lastHourIndex = Math.min(hourlyData.size() - 1, firstHourIndex + desiredCount - 1);
            actualCount = lastHourIndex - firstHourIndex + 1;
        }

        List<PriceFetcher.PriceEntry> barDisplayList = hourlyData.subList(firstHourIndex, lastHourIndex + 1);

        OffsetDateTime windowStart = barDisplayList.get(0).startTime;
        OffsetDateTime windowEnd = barDisplayList.get(barDisplayList.size() - 1).endTime;

        List<PriceFetcher.PriceEntry> graphDisplayList = new ArrayList<>();
        for (PriceFetcher.PriceEntry entry : allData) {
            if (!entry.endTime.isBefore(windowStart) && entry.startTime.isBefore(windowEnd)) {
                graphDisplayList.add(entry);
            } else if (entry.startTime.isAfter(windowEnd)) {
                break;
            }
        }
        if (graphDisplayList.isEmpty()) {
            graphDisplayList.add(currentEntry);
        }

        views.setViewVisibility(R.id.widget_time_container, View.VISIBLE);
        int displayedCount = barDisplayList.size();

        double barMaxPrice = -Double.MAX_VALUE;
        for (PriceFetcher.PriceEntry e : barDisplayList) {
            if (e.pricePerKwh > barMaxPrice) {
                barMaxPrice = e.pricePerKwh;
            }
        }
        if (barMaxPrice <= 0) {
            barMaxPrice = 1.0;
        }

        double graphMaxPrice = -Double.MAX_VALUE;
        PriceFetcher.PriceEntry maxEntry = null;
        PriceFetcher.PriceEntry minEntry = null;
        for (PriceFetcher.PriceEntry e : graphDisplayList) {
            if (e.pricePerKwh > graphMaxPrice) {
                graphMaxPrice = e.pricePerKwh;
                maxEntry = e;
            }
            if (e.pricePerKwh < (minEntry != null ? minEntry.pricePerKwh : Double.MAX_VALUE)) {
                minEntry = e;
            }
        }

        String maxText = "\u2191";
        if (maxEntry != null) {
            ZonedDateTime s = maxEntry.startTime.atZoneSameInstant(ZoneId.systemDefault());
            ZonedDateTime eTime = maxEntry.endTime.atZoneSameInstant(ZoneId.systemDefault());
            String maxPriceText = PriceDisplayUtils.formatPrice(maxEntry.pricePerKwh, country);
            maxText = String.format("\u2191 %s %s", maxPriceText, formatTimeRange(s, eTime));
        }

        String minText = "\u2193";
        if (minEntry != null) {
            ZonedDateTime s = minEntry.startTime.atZoneSameInstant(ZoneId.systemDefault());
            ZonedDateTime eTime = minEntry.endTime.atZoneSameInstant(ZoneId.systemDefault());
            String minPriceText = PriceDisplayUtils.formatPrice(minEntry.pricePerKwh, country);
            minText = String.format("\u2193 %s %s", minPriceText, formatTimeRange(s, eTime));
        }

        views.setTextViewText(R.id.max_price_text, maxText);
        views.setTextViewText(R.id.min_price_text, minText);

        if (chartMode == 0) {
            views.setViewVisibility(R.id.bar_graph_container, View.VISIBLE);
            views.setViewVisibility(R.id.graph_image, View.GONE);

            for (int i = 0; i < barIds.length; i++) {
                int barId = barIds[i];
                if (i < displayedCount) {
                    PriceFetcher.PriceEntry e = barDisplayList.get(i);
                    double fraction = e.pricePerKwh / barMaxPrice;
                    double barDp = fraction * barMaxHeightDp;

                    int barPx = (int) TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP,
                            (float) barDp,
                            context.getResources().getDisplayMetrics()
                    );

                    views.setViewVisibility(barId, View.VISIBLE);
                    views.setInt(barId, "setMinimumHeight", barPx);

                    ZonedDateTime start = e.startTime.atZoneSameInstant(ZoneId.systemDefault());
                    ZonedDateTime end = e.endTime.atZoneSameInstant(ZoneId.systemDefault());
                    if ((now.isEqual(start) || now.isAfter(start)) && now.isBefore(end)) {
                        views.setInt(barId, "setBackgroundResource", R.drawable.bar_rounded_current);
                    } else if (now.isAfter(end)) {
                        views.setInt(barId, "setBackgroundResource", R.drawable.bar_rounded_old);
                    } else {
                        views.setInt(barId, "setBackgroundResource", R.drawable.bar_rounded);
                    }
                } else {
                    views.setViewVisibility(barId, View.GONE);
                }
            }
        } else {
            views.setViewVisibility(R.id.bar_graph_container, View.GONE);
            views.setViewVisibility(R.id.graph_image, View.VISIBLE);

            int curWdp = options != null ? options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) : 0;
            int curHdp = options != null ? options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) : 0;
            int contentWdp = curWdp;
            int graphHeight = shortWidget ? barMaxHeightDp - 5 : barMaxHeightDp - 16;

            android.util.DisplayMetrics dm = context.getResources().getDisplayMetrics();
            int graphWidthPx = Math.max(1, Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, contentWdp, dm)));
            int graphHeightPx = Math.max(1, Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, graphHeight, dm)));

            views.setBoolean(R.id.graph_image, "setAdjustViewBounds", true);
            views.setInt(R.id.graph_image, "setMaxWidth", graphWidthPx);
            views.setInt(R.id.graph_image, "setMaxHeight", graphHeightPx);

            double graphScaleMax = Math.max(barMaxPrice, graphMaxPrice);
            if (graphScaleMax <= 0) {
                graphScaleMax = 1.0;
            }

            Bitmap graphBitmap = GraphUtils.createLineGraphBitmapCubic(
                    context.getApplicationContext(), graphDisplayList, graphScaleMax,
                    graphWidthPx, graphHeightPx, now
            );
            views.setImageViewBitmap(R.id.graph_image, graphBitmap);
        }

        double currentPrice = currentEntry.pricePerKwh;
        String priceText = PriceDisplayUtils.formatPrice(currentPrice, country);
        views.setTextViewText(R.id.current_price_imageview, priceText);

        ZonedDateTime currentStart = currentEntry.startTime.atZoneSameInstant(ZoneId.systemDefault());
        ZonedDateTime currentEnd = currentEntry.endTime.atZoneSameInstant(ZoneId.systemDefault());
        String timeText = String.format("%02d:%02d-%02d:%02d:", currentStart.getHour(), currentStart.getMinute(), currentEnd.getHour(), currentEnd.getMinute());
        views.setTextViewText(R.id.current_price_header, timeText);

        int[] timeBarIndices = {0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22};
        for (int t = 0; t < timeLabelIds.length; t++) {
            int tvId = timeLabelIds[t];
            int barIndex = timeBarIndices[t];
            if (barIndex < displayedCount) {
                PriceFetcher.PriceEntry e = barDisplayList.get(barIndex);
                ZonedDateTime start = e.startTime.atZoneSameInstant(ZoneId.systemDefault());
                String label = String.format("%02d", start.getHour());
                views.setTextViewText(tvId, label);
            } else {
                views.setTextViewText(tvId, "");
            }
        }

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    private static String formatTimeRange(ZonedDateTime start, ZonedDateTime end) {
        long minutes = Duration.between(start, end).toMinutes();
        if (minutes == 15) {
            return String.format("%02d:%02d", start.getHour(), start.getMinute());
        }
        return String.format("%02d:%02d-%02d:%02d", start.getHour(), start.getMinute(), end.getHour(), end.getMinute());
    }

    private static void showApiErrorState(AppWidgetManager appWidgetManager, int appWidgetId, RemoteViews views) {
        views.setTextViewText(R.id.api_error_text, "ENTSO-E API error");

        views.setViewVisibility(R.id.current_price_header, View.GONE);
        views.setViewVisibility(R.id.current_price_imageview, View.GONE);
        views.setViewVisibility(R.id.current_price_unit, View.GONE);
        views.setViewVisibility(R.id.max_price_text, View.GONE);
        views.setViewVisibility(R.id.min_price_text, View.GONE);
        views.setViewVisibility(R.id.widget_time_container, View.GONE);
        views.setViewVisibility(R.id.bar_graph_container, View.GONE);
        views.setViewVisibility(R.id.graph_image, View.GONE);
        views.setViewVisibility(R.id.api_error_container, View.VISIBLE);

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    private static void showEmptyState(Context context, AppWidgetManager appWidgetManager, int appWidgetId,
                                       RemoteViews views, boolean shortWidget, int chartMode) {
        views.setTextViewText(R.id.current_price_imageview, "--");

        for (int barId : barIds) {
            views.setInt(barId, "setMinimumHeight", 0);
            views.setViewVisibility(barId, View.GONE);
        }

        if (chartMode == 0) {
            views.setViewVisibility(R.id.bar_graph_container, View.VISIBLE);
            views.setViewVisibility(R.id.graph_image, View.GONE);
        } else {
            views.setViewVisibility(R.id.bar_graph_container, View.GONE);
            views.setViewVisibility(R.id.graph_image, View.VISIBLE);
            views.setImageViewBitmap(R.id.graph_image, Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888));
        }

        views.setViewVisibility(R.id.widget_time_container, View.GONE);
        views.setViewVisibility(R.id.api_error_container, View.GONE);
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }
    /**
     * Force an update on all instances after new data is fetched.
     */
    public static void updateAllWidgets(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, MainWidget.class));
        if (ids != null && ids.length > 0) {
            for (int appWidgetId : ids) {
                updateAppWidget(context, manager, appWidgetId);
            }
        }
    }
}

