package io.github.simonhalvdansson.flux;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.ZonedDateTime;

final class ChartWidgetPreviewBinder {
    private static final long BAR_UPDATE_ANIMATION_DURATION_MS = 160L;

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
    private static final int[] Y_AXIS_LABEL_IDS = {
            R.id.widget_chart_y_axis_top_value,
            R.id.widget_chart_y_axis_upper_mid_value,
            R.id.widget_chart_y_axis_lower_mid_value,
            R.id.widget_chart_y_axis_bottom_value
    };
    private static final int[] Y_AXIS_GUIDE_IDS = {
            R.id.widget_chart_y_axis_top_guide,
            R.id.widget_chart_y_axis_upper_mid_guide,
            R.id.widget_chart_y_axis_lower_mid_guide,
            R.id.widget_chart_y_axis_bottom_guide
    };

    private static final int[] TIME_BAR_INDICES = {0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22};
    private static final int MIN_BAR_HEIGHT_DP = 10;

    private ChartWidgetPreviewBinder() {
    }

    static void bind(View previewRoot,
                     SharedPreferences prefs,
                     int chartMode,
                     int barPoolMode,
                     boolean showYAxis) {
        bind(previewRoot, prefs, chartMode, barPoolMode, showYAxis, false);
    }

    static void bind(View previewRoot,
                     SharedPreferences prefs,
                     int chartMode,
                     int barPoolMode,
                     boolean showYAxis,
                     boolean animateBars) {
        if (previewRoot == null) {
            return;
        }

        View chartArea = previewRoot.findViewById(R.id.chart_area_container);
        if (chartArea == null || chartArea.getWidth() <= 0 || chartArea.getHeight() <= 0) {
            previewRoot.post(() -> bind(
                    previewRoot,
                    prefs,
                    chartMode,
                    barPoolMode,
                    showYAxis,
                    animateBars
            ));
            return;
        }

        MainWidgetRenderDataResolver.RenderData renderData =
                MainWidgetRenderDataResolver.resolve(previewRoot.getContext(), prefs, barPoolMode, true);
        double chartDataScaleMax = Math.max(renderData.barScaleMax, renderData.graphScaleMax);
        double yAxisScaleMax = ChartYAxisUtils.resolveRoundedScaleMax(chartDataScaleMax);
        double displayedChartScaleMax = showYAxis ? yAxisScaleMax : chartDataScaleMax;
        if (displayedChartScaleMax <= 0.0d) {
            displayedChartScaleMax = 1.0d;
        }
        if (yAxisScaleMax <= 0.0d) {
            yAxisScaleMax = 1.0d;
        }

        TextView currentPriceHeader = previewRoot.findViewById(R.id.current_price_header);
        TextView currentPriceValue = previewRoot.findViewById(R.id.current_price_imageview);
        TextView currentPriceUnit = previewRoot.findViewById(R.id.current_price_unit);
        TextView maxPriceText = previewRoot.findViewById(R.id.max_price_text);
        TextView minPriceText = previewRoot.findViewById(R.id.min_price_text);
        View barGraphContainer = previewRoot.findViewById(R.id.bar_graph_container);
        ImageView graphImage = previewRoot.findViewById(R.id.graph_image);
        View timeContainer = previewRoot.findViewById(R.id.widget_time_container);
        View apiErrorContainer = previewRoot.findViewById(R.id.api_error_container);
        View yAxisContainer = previewRoot.findViewById(R.id.widget_chart_y_axis_container);
        View yAxisGuides = previewRoot.findViewById(R.id.widget_chart_y_axis_guides);
        View yAxisSpacer = previewRoot.findViewById(R.id.widget_chart_y_axis_spacer);

        currentPriceHeader.setText(renderData.currentTimeText);
        currentPriceValue.setText(renderData.currentPriceText);
        currentPriceUnit.setText(renderData.unitText);
        maxPriceText.setText(renderData.maxText);
        minPriceText.setText(renderData.minText);
        apiErrorContainer.setVisibility(View.GONE);
        timeContainer.setVisibility(View.VISIBLE);
        updateYAxis(previewRoot, showYAxis, yAxisScaleMax, renderData.country, yAxisContainer, yAxisGuides, yAxisSpacer);

        updateTimeLabels(previewRoot, renderData);

        if (chartMode == WidgetPreferences.CHART_MODE_BARS) {
            bindBars(
                    previewRoot,
                    renderData,
                    chartArea.getHeight(),
                    displayedChartScaleMax,
                    animateBars
            );
            barGraphContainer.setVisibility(View.VISIBLE);
            graphImage.setVisibility(View.GONE);
            graphImage.setImageDrawable(null);
            return;
        }

        getBarHeightAnimator(previewRoot).cancel();
        barGraphContainer.setVisibility(View.GONE);
        graphImage.setVisibility(View.VISIBLE);
        bindGraph(graphImage, renderData, chartMode, chartArea.getWidth(), chartArea.getHeight(), displayedChartScaleMax);
    }

    private static void bindBars(View previewRoot,
                                 MainWidgetRenderDataResolver.RenderData renderData,
                                 int availableHeightPx,
                                 double scaleMax,
                                 boolean animate) {
        int drawableHeightPx = Math.max(0, availableHeightPx - dp(previewRoot, 4));
        ZonedDateTime now = ZonedDateTime.now();
        double safeScaleMax = scaleMax > 0.0d ? scaleMax : 1.0d;
        int[] targetHeightsPx = new int[BAR_IDS.length];
        boolean[] targetVisibilities = new boolean[BAR_IDS.length];

        for (int i = 0; i < BAR_IDS.length; i++) {
            ImageView bar = previewRoot.findViewById(BAR_IDS[i]);
            if (i >= renderData.barDisplayEntries.size()) {
                targetHeightsPx[i] = 0;
                targetVisibilities[i] = false;
                continue;
            }

            PriceFetcher.PriceEntry entry = renderData.barDisplayEntries.get(i);
            targetHeightsPx[i] = Math.round(
                    (float) (Math.abs(entry.pricePerKwh) / safeScaleMax) * drawableHeightPx
            );
            targetVisibilities[i] = true;
            bar.setBackgroundResource(BarChartUtils.resolveBarBackgroundRes(entry, now));
        }

        BarHeightAnimator animator = getBarHeightAnimator(previewRoot);
        if (animate) {
            animator.animateUpdates(
                    targetHeightsPx,
                    targetVisibilities,
                    BAR_UPDATE_ANIMATION_DURATION_MS
            );
        } else {
            animator.cancel();
            animator.applyState(targetHeightsPx, targetVisibilities);
        }
    }

    private static void bindGraph(ImageView graphImage,
                                  MainWidgetRenderDataResolver.RenderData renderData,
                                  int chartMode,
                                  int widthPx,
                                  int heightPx,
                                  double scaleMax) {
        int safeWidthPx = Math.max(1, widthPx);
        int safeHeightPx = Math.max(1, heightPx);
        double safeScaleMax = scaleMax > 0.0d ? scaleMax : 1.0d;
        Bitmap graphBitmap = chartMode == WidgetPreferences.CHART_MODE_LINES
                ? GraphUtils.createStepLineGraphBitmap(
                        graphImage.getContext(),
                        renderData.graphDisplayEntries,
                        safeScaleMax,
                        safeWidthPx,
                        safeHeightPx
                )
                : GraphUtils.createLineGraphBitmapCubic(
                        graphImage.getContext(),
                        renderData.graphDisplayEntries,
                        safeScaleMax,
                        safeWidthPx,
                        safeHeightPx,
                        ZonedDateTime.now()
                );
        graphImage.setImageBitmap(graphBitmap);
    }

    private static void updateYAxis(View previewRoot,
                                    boolean showYAxis,
                                    double scaleMax,
                                    String countryCode,
                                    View yAxisContainer,
                                    View yAxisGuides,
                                    View yAxisSpacer) {
        int visibility = showYAxis ? View.VISIBLE : View.GONE;
        yAxisContainer.setVisibility(visibility);
        yAxisGuides.setVisibility(visibility);
        yAxisSpacer.setVisibility(visibility);
        if (!showYAxis) {
            return;
        }

        double safeScaleMax = scaleMax > 0.0d ? scaleMax : 1.0d;
        for (int i = 0; i < Y_AXIS_LABEL_IDS.length; i++) {
            TextView label = previewRoot.findViewById(Y_AXIS_LABEL_IDS[i]);
            View guide = previewRoot.findViewById(Y_AXIS_GUIDE_IDS[i]);
            double tickValue = ChartYAxisUtils.normalizeTickValue(
                    safeScaleMax * ChartYAxisUtils.TICK_FRACTIONS[i]
            );
            label.setText(ChartYAxisUtils.formatAxisValue(tickValue, countryCode));
            label.setVisibility(View.VISIBLE);
            guide.setVisibility(View.VISIBLE);
        }
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

    private static BarHeightAnimator getBarHeightAnimator(View previewRoot) {
        View barGraphContainer = previewRoot.findViewById(R.id.bar_graph_container);
        Object existingAnimator = barGraphContainer.getTag();
        if (existingAnimator instanceof BarHeightAnimator) {
            return (BarHeightAnimator) existingAnimator;
        }
        BarHeightAnimator animator = new BarHeightAnimator(previewRoot, BAR_IDS);
        barGraphContainer.setTag(animator);
        return animator;
    }

    private static int dp(View view, int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                view.getResources().getDisplayMetrics()
        ));
    }
}
