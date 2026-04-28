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

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Implementation of App Widget functionality.
 */
public class MainWidget extends AppWidgetProvider {

    private static final String TAG = "MainWidget";
    private static final int ROOT_HORIZONTAL_PADDING_DP = 16;
    private static final int Y_AXIS_WIDTH_DP = 28;

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
    private static final int[] yAxisLabelIds = {
            R.id.widget_chart_y_axis_top_value,
            R.id.widget_chart_y_axis_upper_mid_value,
            R.id.widget_chart_y_axis_lower_mid_value,
            R.id.widget_chart_y_axis_bottom_value
    };
    private static final int[] yAxisGuideIds = {
            R.id.widget_chart_y_axis_top_guide,
            R.id.widget_chart_y_axis_upper_mid_guide,
            R.id.widget_chart_y_axis_lower_mid_guide,
            R.id.widget_chart_y_axis_bottom_guide
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
    public void onDeleted(Context context, int[] appWidgetIds) {
        super.onDeleted(context, appWidgetIds);
        SharedPreferences prefs = PriceRepository.getPreferences(context);
        for (int appWidgetId : appWidgetIds) {
            WidgetPreferences.clearWidgetPreferences(prefs, appWidgetId);
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
        int chartMode = WidgetPreferences.getChartMode(prefs, appWidgetId);
        boolean showYAxis = WidgetPreferences.getMainChartShowYAxis(prefs, appWidgetId);
        int barPoolMode = WidgetPreferences.getMainBarPoolMode(prefs, appWidgetId);
        MainWidgetRenderDataResolver.RenderData renderData =
                MainWidgetRenderDataResolver.resolve(context, prefs, barPoolMode, false);
        double chartDataScaleMax = Math.max(renderData.barScaleMax, renderData.graphScaleMax);
        double yAxisScaleMax = ChartYAxisUtils.resolveRoundedScaleMax(chartDataScaleMax);
        double displayedChartScaleMax = showYAxis ? yAxisScaleMax : chartDataScaleMax;
        if (displayedChartScaleMax <= 0.0d) {
            displayedChartScaleMax = 1.0d;
        }
        if (yAxisScaleMax <= 0.0d) {
            yAxisScaleMax = 1.0d;
        }

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.main_widget);
        // Reset visibility in case previous update showed API error state.
        views.setViewVisibility(R.id.api_error_container, View.GONE);
        views.setViewVisibility(R.id.current_price_header, View.VISIBLE);
        views.setViewVisibility(R.id.current_price_imageview, View.VISIBLE);
        views.setViewVisibility(R.id.current_price_unit, View.VISIBLE);
        views.setViewVisibility(R.id.max_price_text, View.VISIBLE);
        views.setViewVisibility(R.id.min_price_text, View.VISIBLE);
        updateYAxisViews(views, showYAxis, yAxisScaleMax, renderData.country);

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
            if (chartMode == WidgetPreferences.CHART_MODE_BARS) {
                barMaxHeightDp = shortWidget ? maxHeightDp - 80 : maxHeightDp - 110;
            } else {
                barMaxHeightDp = shortWidget ? maxHeightDp - 78 : maxHeightDp - 85;
            }
        }

        // set padding to 12dp if < 120dp
        if (shortWidget) {
            views.setViewPadding(R.id.widget_root, dp16, dp10, dp16, dp10);
            views.setViewPadding(R.id.widget_time_container, 0, dp1, 0, 0); // ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã¢â‚¬Å“top marginÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â°Ãƒâ€¹Ã¢â‚¬Â  top padding
        } else {
            views.setViewPadding(R.id.widget_root, dp16, dp16, dp16, dp16);
            views.setViewPadding(R.id.widget_time_container, 0, dp4, 0, 0); // ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã¢â‚¬Å“top marginÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã‚Â°Ãƒâ€¹Ã¢â‚¬Â  top padding

        }

        if (!renderData.hasData) {
            Log.w(TAG, "No cached data found or API error; showing error message");
            showApiErrorState(appWidgetManager, appWidgetId, views);
            return;
        }

        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        views.setTextViewText(R.id.current_price_unit, renderData.unitText);

        views.setViewVisibility(R.id.widget_time_container, View.VISIBLE);
        int displayedCount = renderData.barDisplayEntries.size();
        views.setTextViewText(R.id.max_price_text, renderData.maxText);
        views.setTextViewText(R.id.min_price_text, renderData.minText);

        if (chartMode == WidgetPreferences.CHART_MODE_BARS) {
            views.setViewVisibility(R.id.bar_graph_container, View.VISIBLE);
            views.setViewVisibility(R.id.graph_image, View.GONE);

            for (int i = 0; i < barIds.length; i++) {
                int barId = barIds[i];
                if (i < displayedCount) {
                    PriceFetcher.PriceEntry e = renderData.barDisplayEntries.get(i);
                    double fraction = Math.abs(e.pricePerKwh) / displayedChartScaleMax;
                    double barDp = fraction * barMaxHeightDp;

                    int barPx = (int) TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP,
                            (float) barDp,
                            context.getResources().getDisplayMetrics()
                    );

                    views.setViewVisibility(barId, View.VISIBLE);
                    views.setInt(barId, "setMinimumHeight", barPx);
                    views.setInt(barId, "setBackgroundResource", BarChartUtils.resolveBarBackgroundRes(e, now));
                } else {
                    views.setViewVisibility(barId, View.GONE);
                }
            }
        } else {
            views.setViewVisibility(R.id.bar_graph_container, View.GONE);
            views.setViewVisibility(R.id.graph_image, View.VISIBLE);

            int curWdp = options != null ? options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) : 0;
            int contentWdp = curWdp - (ROOT_HORIZONTAL_PADDING_DP * 2);
            if (showYAxis) {
                contentWdp -= Y_AXIS_WIDTH_DP;
            }
            contentWdp = Math.max(1, contentWdp);
            int graphHeight = shortWidget ? barMaxHeightDp - 5 : barMaxHeightDp - 16;

            android.util.DisplayMetrics dm = context.getResources().getDisplayMetrics();
            int graphWidthPx = Math.max(1, Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, contentWdp, dm)));
            int graphHeightPx = Math.max(1, Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, graphHeight, dm)));

            views.setBoolean(R.id.graph_image, "setAdjustViewBounds", true);
            views.setInt(R.id.graph_image, "setMaxWidth", graphWidthPx);
            views.setInt(R.id.graph_image, "setMaxHeight", graphHeightPx);

            Bitmap graphBitmap = chartMode == WidgetPreferences.CHART_MODE_LINES
                    ? GraphUtils.createStepLineGraphBitmap(
                            context.getApplicationContext(),
                            renderData.graphDisplayEntries,
                            displayedChartScaleMax,
                            graphWidthPx,
                            graphHeightPx
                    )
                    : GraphUtils.createLineGraphBitmapCubic(
                            context.getApplicationContext(),
                            renderData.graphDisplayEntries,
                            displayedChartScaleMax,
                            graphWidthPx,
                            graphHeightPx,
                            now
                    );
            views.setImageViewBitmap(R.id.graph_image, graphBitmap);
        }

        views.setTextViewText(R.id.current_price_imageview, renderData.currentPriceText);
        views.setTextViewText(R.id.current_price_header, renderData.currentTimeText);

        int[] timeBarIndices = {0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22};
        for (int t = 0; t < timeLabelIds.length; t++) {
            int tvId = timeLabelIds[t];
            int barIndex = timeBarIndices[t];
            if (barIndex < displayedCount) {
                PriceFetcher.PriceEntry e = renderData.barDisplayEntries.get(barIndex);
                ZonedDateTime start = e.startTime.atZoneSameInstant(ZoneId.systemDefault());
                String label = String.format("%02d", start.getHour());
                views.setTextViewText(tvId, label);
            } else {
                views.setTextViewText(tvId, "");
            }
        }

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    private static void updateYAxisViews(RemoteViews views,
                                         boolean showYAxis,
                                         double scaleMax,
                                         String countryCode) {
        int visibility = showYAxis ? View.VISIBLE : View.GONE;
        views.setViewVisibility(R.id.widget_chart_y_axis_container, visibility);
        views.setViewVisibility(R.id.widget_chart_y_axis_guides, visibility);
        views.setViewVisibility(R.id.widget_chart_y_axis_spacer, visibility);
        if (!showYAxis) {
            return;
        }

        double safeScaleMax = scaleMax > 0.0d ? scaleMax : 1.0d;
        for (int i = 0; i < yAxisLabelIds.length; i++) {
            double tickValue = ChartYAxisUtils.normalizeTickValue(
                    safeScaleMax * ChartYAxisUtils.TICK_FRACTIONS[i]
            );
            views.setTextViewText(
                    yAxisLabelIds[i],
                    ChartYAxisUtils.formatAxisValue(tickValue, countryCode)
            );
            views.setViewVisibility(yAxisLabelIds[i], View.VISIBLE);
            views.setViewVisibility(yAxisGuideIds[i], View.VISIBLE);
        }
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
        views.setViewVisibility(R.id.widget_chart_y_axis_container, View.GONE);
        views.setViewVisibility(R.id.widget_chart_y_axis_guides, View.GONE);
        views.setViewVisibility(R.id.widget_chart_y_axis_spacer, View.GONE);
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

        if (chartMode == WidgetPreferences.CHART_MODE_BARS) {
            views.setViewVisibility(R.id.bar_graph_container, View.VISIBLE);
            views.setViewVisibility(R.id.graph_image, View.GONE);
        } else {
            views.setViewVisibility(R.id.bar_graph_container, View.GONE);
            views.setViewVisibility(R.id.graph_image, View.VISIBLE);
            views.setImageViewBitmap(R.id.graph_image, Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888));
        }

        views.setViewVisibility(R.id.widget_time_container, View.GONE);
        views.setViewVisibility(R.id.widget_chart_y_axis_container, View.GONE);
        views.setViewVisibility(R.id.widget_chart_y_axis_guides, View.GONE);
        views.setViewVisibility(R.id.widget_chart_y_axis_spacer, View.GONE);
        views.setViewVisibility(R.id.api_error_container, View.GONE);
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }
    /**
     * Force an update on all instances after new data is fetched.
     */
    public static void updateAllWidgets(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids;
        try {
            ids = manager.getAppWidgetIds(new ComponentName(context, MainWidget.class));
        } catch (RuntimeException e) {
            Log.w(TAG, "Skipping widget refresh because AppWidgetManager is unavailable", e);
            return;
        }
        if (ids != null && ids.length > 0) {
            for (int appWidgetId : ids) {
                try {
                    updateAppWidget(context, manager, appWidgetId);
                } catch (RuntimeException e) {
                    Log.w(TAG, "Failed to refresh widget " + appWidgetId, e);
                }
            }
        }
    }
}
