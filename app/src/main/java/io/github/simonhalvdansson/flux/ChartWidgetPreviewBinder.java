package io.github.simonhalvdansson.flux;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.ZonedDateTime;

final class ChartWidgetPreviewBinder {

    private static final int[] BAR_IDS = {
            R.id.bar_0, R.id.bar_1, R.id.bar_2, R.id.bar_3,
            R.id.bar_4, R.id.bar_5, R.id.bar_6, R.id.bar_7,
            R.id.bar_8, R.id.bar_9, R.id.bar_10, R.id.bar_11,
            R.id.bar_12, R.id.bar_13, R.id.bar_14, R.id.bar_15,
            R.id.bar_16, R.id.bar_17, R.id.bar_18, R.id.bar_19,
            R.id.bar_20, R.id.bar_21, R.id.bar_22, R.id.bar_23
    };

    private static final int[] TIME_LABEL_IDS = {
            R.id.time0, R.id.time1, R.id.time2, R.id.time3,
            R.id.time4, R.id.time5, R.id.time6, R.id.time7,
            R.id.time8, R.id.time9, R.id.time10, R.id.time11
    };

    private static final int[] TIME_BAR_INDICES = {0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22};
    private static final int MIN_BAR_HEIGHT_DP = 10;

    private ChartWidgetPreviewBinder() {
    }

    static void bind(View previewRoot,
                     SharedPreferences prefs,
                     int chartMode,
                     int barPoolMode) {
        if (previewRoot == null) {
            return;
        }

        View chartArea = previewRoot.findViewById(R.id.chart_area_container);
        if (chartArea == null || chartArea.getWidth() <= 0 || chartArea.getHeight() <= 0) {
            previewRoot.post(() -> bind(previewRoot, prefs, chartMode, barPoolMode));
            return;
        }

        MainWidgetRenderDataResolver.RenderData renderData =
                MainWidgetRenderDataResolver.resolve(previewRoot.getContext(), prefs, barPoolMode, true);

        TextView currentPriceHeader = previewRoot.findViewById(R.id.current_price_header);
        TextView currentPriceValue = previewRoot.findViewById(R.id.current_price_imageview);
        TextView currentPriceUnit = previewRoot.findViewById(R.id.current_price_unit);
        TextView maxPriceText = previewRoot.findViewById(R.id.max_price_text);
        TextView minPriceText = previewRoot.findViewById(R.id.min_price_text);
        View barGraphContainer = previewRoot.findViewById(R.id.bar_graph_container);
        ImageView graphImage = previewRoot.findViewById(R.id.graph_image);
        View timeContainer = previewRoot.findViewById(R.id.widget_time_container);
        View apiErrorContainer = previewRoot.findViewById(R.id.api_error_container);

        currentPriceHeader.setText(renderData.currentTimeText);
        currentPriceValue.setText(renderData.currentPriceText);
        currentPriceUnit.setText(renderData.unitText);
        maxPriceText.setText(renderData.maxText);
        minPriceText.setText(renderData.minText);
        apiErrorContainer.setVisibility(View.GONE);
        timeContainer.setVisibility(View.VISIBLE);

        updateTimeLabels(previewRoot, renderData);

        if (chartMode == WidgetPreferences.CHART_MODE_BARS) {
            bindBars(previewRoot, renderData, chartArea.getHeight());
            barGraphContainer.setVisibility(View.VISIBLE);
            graphImage.setVisibility(View.GONE);
            graphImage.setImageDrawable(null);
            return;
        }

        barGraphContainer.setVisibility(View.GONE);
        graphImage.setVisibility(View.VISIBLE);
        bindGraph(graphImage, renderData, chartMode, chartArea.getWidth(), chartArea.getHeight());
    }

    private static void bindBars(View previewRoot,
                                 MainWidgetRenderDataResolver.RenderData renderData,
                                 int availableHeightPx) {
        int drawableHeightPx = Math.max(0, availableHeightPx - dp(previewRoot, 4));
        ZonedDateTime now = ZonedDateTime.now();

        for (int i = 0; i < BAR_IDS.length; i++) {
            ImageView bar = previewRoot.findViewById(BAR_IDS[i]);
            if (i >= renderData.barDisplayEntries.size()) {
                bar.setVisibility(View.INVISIBLE);
                setBarHeight(bar, 0);
                continue;
            }

            PriceFetcher.PriceEntry entry = renderData.barDisplayEntries.get(i);
            int barHeightPx = Math.round((float) (Math.abs(entry.pricePerKwh) / renderData.barScaleMax) * drawableHeightPx);
            bar.setVisibility(View.VISIBLE);
            setBarHeight(bar, barHeightPx);
            bar.setBackgroundResource(BarChartUtils.resolveBarBackgroundRes(entry, now));
        }
    }

    private static void bindGraph(ImageView graphImage,
                                  MainWidgetRenderDataResolver.RenderData renderData,
                                  int chartMode,
                                  int widthPx,
                                  int heightPx) {
        int safeWidthPx = Math.max(1, widthPx);
        int safeHeightPx = Math.max(1, heightPx);
        Bitmap graphBitmap = chartMode == WidgetPreferences.CHART_MODE_LINES
                ? GraphUtils.createStepLineGraphBitmap(
                        graphImage.getContext(),
                        renderData.graphDisplayEntries,
                        renderData.graphScaleMax,
                        safeWidthPx,
                        safeHeightPx
                )
                : GraphUtils.createLineGraphBitmapCubic(
                        graphImage.getContext(),
                        renderData.graphDisplayEntries,
                        renderData.graphScaleMax,
                        safeWidthPx,
                        safeHeightPx,
                        ZonedDateTime.now()
                );
        graphImage.setImageBitmap(graphBitmap);
    }

    private static void updateTimeLabels(View previewRoot,
                                         MainWidgetRenderDataResolver.RenderData renderData) {
        for (int i = 0; i < TIME_LABEL_IDS.length; i++) {
            TextView label = previewRoot.findViewById(TIME_LABEL_IDS[i]);
            int barIndex = TIME_BAR_INDICES[i];
            if (barIndex < renderData.barDisplayEntries.size()) {
                ZonedDateTime start = renderData.barDisplayEntries.get(barIndex)
                        .startTime
                        .atZoneSameInstant(ZonedDateTime.now().getZone());
                label.setText(String.format("%02d", start.getHour()));
            } else {
                label.setText("");
            }
        }
    }

    private static void setBarHeight(ImageView bar, int heightPx) {
        ViewGroup.LayoutParams params = bar.getLayoutParams();
        if (params.height != heightPx) {
            params.height = heightPx;
            bar.setLayoutParams(params);
        }
    }

    private static int dp(View view, int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                view.getResources().getDisplayMetrics()
        ));
    }
}
