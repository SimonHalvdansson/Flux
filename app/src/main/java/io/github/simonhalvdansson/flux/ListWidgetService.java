package io.github.simonhalvdansson.flux;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.appwidget.AppWidgetManager;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * RemoteViewsService providing data for the ListWidget's ListView.
 */
public class ListWidgetService extends RemoteViewsService {

    public static final String EXTRA_SHOW_BAR = "show_bar";
    public static final int BAR_VISIBILITY_THRESHOLD_DP = 100;

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new PriceRemoteViewsFactory(getApplicationContext(), intent);
    }

    static class PriceRemoteViewsFactory implements RemoteViewsFactory {
        private static final int ITEM_TIME_WIDTH_DP = 39;
        private static final int ITEM_TIME_MARGIN_END_DP = 4;
        private static final int ITEM_PRICE_WIDTH_DP = 36;
        private static final int ITEM_BAR_MARGIN_START_DP = 2;
        private static final int ITEM_HORIZONTAL_PADDING_DP = 32; // 16dp left + 16dp right
        private static final int NON_BAR_CONTENT_WIDTH_DP =
                ITEM_HORIZONTAL_PADDING_DP + ITEM_TIME_WIDTH_DP + ITEM_TIME_MARGIN_END_DP
                        + ITEM_PRICE_WIDTH_DP + ITEM_BAR_MARGIN_START_DP;

        private final Context context;
        private final List<PriceFetcher.PriceEntry> items = new ArrayList<>();
        private final boolean showBar;
        private double maxPrice = 0.0;
        private int maxBarWidthPx;
        private final int appWidgetId;
        private String country = "NO";

        PriceRemoteViewsFactory(Context context, Intent intent) {
            this.context = context;
            this.showBar = intent.getBooleanExtra(EXTRA_SHOW_BAR, false);
            this.appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            this.maxBarWidthPx = 0;
        }

        @Override
        public void onCreate() {
            // no-op
        }

        @Override
        public void onDataSetChanged() {
            items.clear();
            maxPrice = 0.0;
            updateMaxBarWidthPx();
            SharedPreferences prefs = PriceRepository.getPreferences(context);
            String combinedJson = prefs.getString(PriceRepository.KEY_JSON_DATA, null);
            country = prefs.getString(PriceUpdateJobService.KEY_SELECTED_COUNTRY, "NO");

            if (combinedJson == null || combinedJson.trim().isEmpty()) {
                return;
            }

            List<PriceFetcher.PriceEntry> allData = CurrentPriceResolver.getAdjustedEntries(prefs);
            if (allData.isEmpty()) {
                return;
            }

            int currentIndex = CurrentPriceResolver.findCurrentIndex(allData);

            List<PriceFetcher.PriceEntry> futureEntries = new ArrayList<>();
            for (int i = currentIndex + 1; i < allData.size(); i++) {
                futureEntries.add(allData.get(i));
            }

            int incrementMinutes = WidgetPreferences.getListIncrementMinutes(prefs);
            int poolMode = WidgetPreferences.getListPoolMode(prefs);
            List<PriceFetcher.PriceEntry> displayEntries;
            if (incrementMinutes == WidgetPreferences.INCREMENT_15_MINUTES) {
                displayEntries = futureEntries;
            } else {
                displayEntries = PriceFetcher.aggregateConsecutive(futureEntries, incrementMinutes, poolMode);
            }

            for (PriceFetcher.PriceEntry entry : displayEntries) {
                items.add(entry);
                if (entry.pricePerKwh > maxPrice) {
                    maxPrice = entry.pricePerKwh;
                }
            }
        }

        @Override
        public void onDestroy() {
            items.clear();
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public RemoteViews getViewAt(int position) {
            if (position < 0 || position >= items.size()) {
                return null;
            }
            PriceFetcher.PriceEntry e = items.get(position);
            ZonedDateTime s = e.startTime.atZoneSameInstant(ZoneId.systemDefault());
            String tText = String.format("%02d:%02d:", s.getHour(), s.getMinute());
            String pText = PriceDisplayUtils.formatPrice(e.pricePerKwh, country);
            RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.list_item);
            rv.setTextViewText(R.id.item_time, tText);
            rv.setTextViewText(R.id.item_price, pText);
            if (showBar && maxPrice > 0) {
                int barWidth = (int) (e.pricePerKwh / maxPrice * maxBarWidthPx);
                barWidth = Math.max(0, Math.min(maxBarWidthPx, barWidth));
                rv.setViewVisibility(R.id.item_bar, View.VISIBLE);
                rv.setInt(R.id.item_bar, "setMinimumWidth", barWidth);
            } else {
                rv.setViewVisibility(R.id.item_bar, View.GONE);
            }
            rv.setOnClickFillInIntent(R.id.item_root, new Intent());

            Resources res = context.getResources();

            final int baseL = dp(16, res), baseT = dp(1.5f, res), baseR = dp(16, res), baseB = dp(1.5f, res);
            final int extraB = dp(16, res);
            final int extraT = dp(8, res);

            int t = baseT + (position == 0 ? extraT : 0);
            int b = baseB + (position == getCount() - 1 ? extraB : 0);

            rv.setViewPadding(R.id.item_root, baseL, t, baseR, b);

            return rv;
        }

        private int dp(float d, Resources res) {
            return (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, d, res.getDisplayMetrics());
        }

        private void updateMaxBarWidthPx() {
            int widgetWidthDp = 0;
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                Bundle options = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId);
                if (options != null) {
                    int minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
                    widgetWidthDp = minWidthDp;
                }
            }

            if (widgetWidthDp <= 0) {
                widgetWidthDp = NON_BAR_CONTENT_WIDTH_DP;
            }

            int availableBarWidthDp = Math.max(0, widgetWidthDp - NON_BAR_CONTENT_WIDTH_DP);
            maxBarWidthPx = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    availableBarWidthDp,
                    context.getResources().getDisplayMetrics());
        }

        @Override
        public RemoteViews getLoadingView() {
            return null;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }
    }
}

