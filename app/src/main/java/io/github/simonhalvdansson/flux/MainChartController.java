package io.github.simonhalvdansson.flux;

import android.content.SharedPreferences;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

final class MainChartController {
    private static final String TAG = "MainChartController";
    static final String KEY_CHART_MODE = "main_activity_chart_mode";
    static final String KEY_BAR_POOL_MODE = "main_activity_bar_pool_mode";
    private static final String KEY_SHOW_Y_AXIS = "main_activity_show_y_axis";
    private static final int MODE_BARS = 0;
    private static final int MODE_GRAPH = 1;
    private static final int MODE_LINES = 2;
    private static final int TOOLTIP_VERTICAL_OFFSET_DP = 6;
    private static final int TOOLTIP_HORIZONTAL_PADDING_DP = 12;
    private static final int TOOLTIP_VERTICAL_PADDING_DP = 10;
    private static final long CHART_MODE_SCALE_OUT_DURATION_MS = 135L;
    private static final long CHART_MODE_SCALE_IN_DURATION_MS = 180L;
    private static final float CHART_MODE_SCALE_OUT = 0.94f;
    private static final float CHART_MODE_SCALE_IN_START = 1.04f;
    private static final int CHART_MAX_HEIGHT_DP = 160;
    private static final long BAR_ANIMATION_DURATION_MS = 468L;
    private static final long BAR_ANIMATION_STAGGER_MS = 20L;
    private static final long BAR_UPDATE_ANIMATION_DURATION_MS = 160L;
    private static final long GRAPH_FADE_IN_DURATION_MS = 420L;
    private static final int Y_AXIS_EDGE_MARGIN_DP = 6;

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

    private final AppCompatActivity activity;
    private final SharedPreferences sharedPreferences;
    private final Runnable renderCallback;

    private View chartContainer;
    private ViewGroup chartVisualContainer;
    private View chartModeContentContainer;
    private View chartYAxisContainer;
    private View chartYAxisGuides;
    private View chartYAxisSpacer;
    private View chartYAxisTopGuide;
    private View chartYAxisUpperMidGuide;
    private View chartYAxisLowerMidGuide;
    private View chartYAxisBottomGuide;
    private LinearLayout barChartContainer;
    private ImageView graphImageView;
    private View chartTouchOverlay;
    private MaterialButtonToggleGroup chartToggleGroup;
    private MaterialSwitch chartYAxisSwitch;
    private View barPoolContainer;
    private MaterialButtonToggleGroup barPoolToggleGroup;
    private TextView chartYAxisTopValue;
    private TextView chartYAxisUpperMidValue;
    private TextView chartYAxisLowerMidValue;
    private TextView chartYAxisBottomValue;
    private BarHeightAnimator barHeightAnimator;
    private PopupWindow chartTooltipPopup;
    private boolean shouldAnimateInitialChart;
    private boolean suppressNextBarHeightAnimation;
    private boolean suppressNextChartModePreferenceRender;
    private int chartModeTransitionId;
    private List<PriceFetcher.PriceEntry> displayedBarEntries = new ArrayList<>();
    private List<List<PriceFetcher.PriceEntry>> displayedBucketEntries = new ArrayList<>();
    private List<PriceFetcher.PriceEntry> displayedGraphEntries = new ArrayList<>();
    private double displayedGraphMaxPrice = 1.0;
    private double displayedChartScaleMax = 1.0;
    private int selectedChartBucketIndex = -1;
    private float selectedChartFraction = Float.NaN;
    private boolean suppressNextTooltipDismissCallback;

    MainChartController(AppCompatActivity activity,
                        SharedPreferences sharedPreferences,
                        boolean shouldAnimateInitialChart,
                        Runnable renderCallback) {
        this.activity = activity;
        this.sharedPreferences = sharedPreferences;
        this.shouldAnimateInitialChart = shouldAnimateInitialChart;
        this.renderCallback = renderCallback;
    }

    static boolean isRenderPreferenceKey(String key) {
        return KEY_CHART_MODE.equals(key) || KEY_BAR_POOL_MODE.equals(key);
    }

    void setup() {
        chartContainer = activity.findViewById(R.id.price_data_content);
        chartVisualContainer = activity.findViewById(R.id.chart_visual_container);
        chartModeContentContainer = activity.findViewById(R.id.chart_mode_content_container);
        chartYAxisContainer = activity.findViewById(R.id.chart_y_axis_container);
        chartYAxisGuides = activity.findViewById(R.id.chart_y_axis_guides);
        chartYAxisSpacer = activity.findViewById(R.id.chart_y_axis_spacer);
        chartYAxisTopGuide = activity.findViewById(R.id.chart_y_axis_top_guide);
        chartYAxisUpperMidGuide = activity.findViewById(R.id.chart_y_axis_upper_mid_guide);
        chartYAxisLowerMidGuide = activity.findViewById(R.id.chart_y_axis_lower_mid_guide);
        chartYAxisBottomGuide = activity.findViewById(R.id.chart_y_axis_bottom_guide);
        barChartContainer = activity.findViewById(R.id.bar_chart_container);
        graphImageView = activity.findViewById(R.id.graph_image);
        chartTouchOverlay = activity.findViewById(R.id.chart_touch_overlay);
        chartToggleGroup = activity.findViewById(R.id.main_chart_toggle_group);
        chartYAxisSwitch = activity.findViewById(R.id.main_chart_y_axis_switch);
        barPoolContainer = activity.findViewById(R.id.main_bar_pool_container);
        barPoolToggleGroup = activity.findViewById(R.id.main_bar_pool_toggle_group);
        chartYAxisTopValue = activity.findViewById(R.id.chart_y_axis_top_value);
        chartYAxisUpperMidValue = activity.findViewById(R.id.chart_y_axis_upper_mid_value);
        chartYAxisLowerMidValue = activity.findViewById(R.id.chart_y_axis_lower_mid_value);
        chartYAxisBottomValue = activity.findViewById(R.id.chart_y_axis_bottom_value);
        barHeightAnimator = new BarHeightAnimator(chartContainer, BAR_IDS);

        setupYAxisSwitch();
        setupBarPoolToggle();
        setupChartModeToggle();
        setupTouchOverlay();
        configureBarShadows();
    }

    boolean consumePreferenceChange(String key) {
        if (KEY_CHART_MODE.equals(key) && suppressNextChartModePreferenceRender) {
            suppressNextChartModePreferenceRender = false;
            return true;
        }
        if (KEY_CHART_MODE.equals(key) && getChartMode() == MODE_BARS) {
            suppressNextBarHeightAnimation = true;
        }
        return false;
    }

    void onStop() {
        cancelChartModeTransition();
        cancelBarAnimation();
        cancelGraphAnimation();
        clearSelection(false);
    }

    void render(List<PriceFetcher.PriceEntry> allData) {
        clearSelection(false);

        if (allData == null || allData.isEmpty()) {
            clearChartData();
            cancelBarAnimation();
            chartContainer.setVisibility(View.GONE);
            return;
        }

        List<PriceFetcher.PriceEntry> hourlyData =
                PriceFetcher.aggregateToHourly(allData, getBarPoolMode());
        if (hourlyData.isEmpty()) {
            clearChartData();
            cancelBarAnimation();
            chartContainer.setVisibility(View.GONE);
            return;
        }

        chartContainer.setVisibility(View.VISIBLE);
        chartTouchOverlay.setEnabled(true);

        int currentIndex = CurrentPriceResolver.findCurrentIndex(allData);
        PriceFetcher.PriceEntry currentEntry = allData.get(currentIndex);
        OffsetDateTime currentHourStart = currentEntry.startTime.truncatedTo(ChronoUnit.HOURS);

        int currentHourIndex = 0;
        long bestDiff = Long.MAX_VALUE;
        for (int i = 0; i < hourlyData.size(); i++) {
            long diff = Math.abs(Duration.between(currentHourStart, hourlyData.get(i).startTime).toMinutes());
            if (diff < bestDiff) {
                bestDiff = diff;
                currentHourIndex = i;
            }
        }

        int desiredCount = Math.min(BAR_IDS.length, hourlyData.size());
        int firstHourIndex = Math.max(0, currentHourIndex - 3);
        int lastHourIndex = Math.min(hourlyData.size() - 1, firstHourIndex + desiredCount - 1);
        int actualCount = lastHourIndex - firstHourIndex + 1;
        if (actualCount < desiredCount) {
            firstHourIndex = Math.max(0, Math.min(firstHourIndex, hourlyData.size() - desiredCount));
            lastHourIndex = Math.min(hourlyData.size() - 1, firstHourIndex + desiredCount - 1);
        }

        displayedBarEntries = BarChartUtils.applyCurrentPriceToDisplayedBars(
                new ArrayList<>(hourlyData.subList(firstHourIndex, lastHourIndex + 1)),
                currentEntry
        );
        displayedBucketEntries = buildDisplayedBucketEntries(displayedBarEntries, allData);
        displayedGraphEntries = getEntriesInRange(
                allData,
                displayedBarEntries.get(0).startTime,
                displayedBarEntries.get(displayedBarEntries.size() - 1).endTime
        );

        double barScaleMax = BarChartUtils.resolveScaleMax(displayedBarEntries);
        displayedGraphMaxPrice = BarChartUtils.resolveScaleMax(displayedGraphEntries);
        double chartDataScaleMax = Math.max(barScaleMax, displayedGraphMaxPrice);
        double yAxisScaleMax = ChartYAxisUtils.resolveRoundedScaleMax(chartDataScaleMax);
        displayedChartScaleMax = isYAxisEnabled() ? yAxisScaleMax : chartDataScaleMax;
        if (displayedChartScaleMax <= 0.0) {
            displayedChartScaleMax = 1.0;
        }
        if (yAxisScaleMax <= 0.0) {
            yAxisScaleMax = 1.0;
        }

        int chartMode = getChartMode();
        updateYAxis(yAxisScaleMax);
        if (chartMode == MODE_BARS) {
            renderBars(displayedBarEntries, displayedChartScaleMax);
        } else {
            renderGraph(chartMode, displayedGraphEntries, displayedChartScaleMax);
        }

        logChartDiagnostics(allData, hourlyData, displayedGraphEntries, chartMode);
        updateTimeLabels(displayedBarEntries);
    }

    private void setupYAxisSwitch() {
        chartYAxisSwitch.setChecked(isYAxisEnabled());
        updateYAxisVisibility(chartYAxisSwitch.isChecked());
        chartYAxisSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sharedPreferences.edit()
                    .putBoolean(KEY_SHOW_Y_AXIS, isChecked)
                    .apply();
            updateYAxisVisibility(isChecked);
            clearSelection(false);
            chartVisualContainer.post(renderCallback);
        });
    }

    private void setupBarPoolToggle() {
        barPoolToggleGroup.setSelectionRequired(true);
        barPoolToggleGroup.check(getBarPoolButtonId(getBarPoolMode()));
        barPoolToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            sharedPreferences.edit()
                    .putInt(KEY_BAR_POOL_MODE, getBarPoolModeForButton(checkedId))
                    .apply();
            clearSelection(false);
            renderCallback.run();
        });
    }

    private void setupChartModeToggle() {
        syncChartModeButtonIconTints();
        chartToggleGroup.setSelectionRequired(true);
        chartToggleGroup.check(getChartModeButtonId(getChartMode()));
        updateBarPoolVisibility(getChartMode());
        chartToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            int chartMode = getChartModeForButton(checkedId);
            if (chartMode == getChartMode()) {
                return;
            }
            suppressNextChartModePreferenceRender = true;
            suppressNextBarHeightAnimation = chartMode == MODE_BARS;
            sharedPreferences.edit()
                    .putInt(KEY_CHART_MODE, chartMode)
                    .apply();
            updateBarPoolVisibility(chartMode);
            clearSelection(false);
            animateChartModeChange();
        });
    }

    private void syncChartModeButtonIconTints() {
        syncButtonIconTint(R.id.main_chart_bars_button);
        syncButtonIconTint(R.id.main_chart_graph_button);
        syncButtonIconTint(R.id.main_chart_lines_button);
    }

    private void syncButtonIconTint(int buttonId) {
        MaterialButton button = activity.findViewById(buttonId);
        if (button == null) {
            return;
        }
        button.setIconTint(button.getTextColors());
    }

    private void updateBarPoolVisibility(int chartMode) {
        boolean visible = chartMode == MODE_BARS;
        barPoolContainer.setVisibility(visible ? View.VISIBLE : View.GONE);
        setViewEnabled(barPoolContainer, visible);
    }

    private void setViewEnabled(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                setViewEnabled(group.getChildAt(i), enabled);
            }
        }
    }

    private void setupTouchOverlay() {
        chartTouchOverlay.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                v.performClick();
                showTooltip(event);
                return true;
            }
            return event.getAction() == MotionEvent.ACTION_DOWN;
        });
    }

    private void animateChartModeChange() {
        chartModeTransitionId++;
        int transitionId = chartModeTransitionId;
        chartModeContentContainer.animate().cancel();
        chartModeContentContainer.setVisibility(View.VISIBLE);
        chartModeContentContainer.setAlpha(1f);
        chartModeContentContainer.setScaleX(1f);
        chartModeContentContainer.setScaleY(1f);

        if (chartContainer.getVisibility() != View.VISIBLE) {
            chartVisualContainer.setClipChildren(false);
            renderCallback.run();
            return;
        }

        chartVisualContainer.setClipChildren(true);
        chartTouchOverlay.setEnabled(false);
        FastOutSlowInInterpolator interpolator = new FastOutSlowInInterpolator();
        chartModeContentContainer.animate()
                .alpha(0f)
                .scaleX(CHART_MODE_SCALE_OUT)
                .scaleY(CHART_MODE_SCALE_OUT)
                .setDuration(CHART_MODE_SCALE_OUT_DURATION_MS)
                .setInterpolator(interpolator)
                .withEndAction(() -> {
                        if (transitionId != chartModeTransitionId) {
                            return;
                        }

                        chartModeContentContainer.setVisibility(View.INVISIBLE);
                        renderCallback.run();
                        if (chartContainer.getVisibility() != View.VISIBLE) {
                            chartVisualContainer.setClipChildren(false);
                            chartModeContentContainer.setVisibility(View.VISIBLE);
                            chartModeContentContainer.setAlpha(1f);
                            chartModeContentContainer.setScaleX(1f);
                            chartModeContentContainer.setScaleY(1f);
                            return;
                        }

                        chartModeContentContainer.setVisibility(View.VISIBLE);
                        chartModeContentContainer.setAlpha(0f);
                        chartModeContentContainer.setScaleX(CHART_MODE_SCALE_IN_START);
                        chartModeContentContainer.setScaleY(CHART_MODE_SCALE_IN_START);
                        chartModeContentContainer.animate()
                                .alpha(1f)
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(CHART_MODE_SCALE_IN_DURATION_MS)
                                .setInterpolator(interpolator)
                                .withEndAction(() -> {
                                    if (transitionId != chartModeTransitionId) {
                                        return;
                                    }
                                    chartVisualContainer.setClipChildren(false);
                                    chartTouchOverlay.setEnabled(true);
                                })
                                .start();
                })
                .start();
    }

    private void cancelChartModeTransition() {
        chartModeTransitionId++;
        if (chartModeContentContainer != null) {
            chartModeContentContainer.animate().cancel();
            chartModeContentContainer.setVisibility(View.VISIBLE);
            chartModeContentContainer.setAlpha(1f);
            chartModeContentContainer.setScaleX(1f);
            chartModeContentContainer.setScaleY(1f);
        }
        if (chartVisualContainer != null) {
            chartVisualContainer.setClipChildren(false);
        }
    }

    private void configureBarShadows() {
        float elevationPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                2,
                activity.getResources().getDisplayMetrics()
        );
        float cornerRadiusPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                8,
                activity.getResources().getDisplayMetrics()
        );

        for (int barId : BAR_IDS) {
            ImageView bar = activity.findViewById(barId);
            bar.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadiusPx);
                }
            });
            bar.setClipToOutline(false);
            bar.setElevation(elevationPx);
        }
    }

    private void clearChartData() {
        displayedBarEntries = new ArrayList<>();
        displayedBucketEntries = new ArrayList<>();
        displayedGraphEntries = new ArrayList<>();
        displayedGraphMaxPrice = 1.0;
        displayedChartScaleMax = 1.0;
        updateYAxis(1.0);
        chartTouchOverlay.setEnabled(false);
        cancelGraphAnimation();
        graphImageView.setImageDrawable(null);
        clearSelection(false);
    }

    private void renderBars(List<PriceFetcher.PriceEntry> displayEntries, double scaleMax) {
        cancelGraphAnimation();
        barChartContainer.setVisibility(View.VISIBLE);
        graphImageView.setVisibility(View.GONE);
        boolean suppressBarHeightAnimation = suppressNextBarHeightAnimation;
        suppressNextBarHeightAnimation = false;

        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        int maxBarHeightPx = resolveMaxBarHeightPx();
        int[] targetHeightsPx = new int[BAR_IDS.length];
        boolean[] targetVisibilities = new boolean[BAR_IDS.length];
        for (int i = 0; i < BAR_IDS.length; i++) {
            ImageView bar = activity.findViewById(BAR_IDS[i]);
            if (i < displayEntries.size()) {
                PriceFetcher.PriceEntry entry = displayEntries.get(i);
                targetHeightsPx[i] = Math.round(
                        (float) ((Math.abs(entry.pricePerKwh) / scaleMax) * maxBarHeightPx)
                );
                targetVisibilities[i] = true;
                bar.setVisibility(View.VISIBLE);
                bar.setBackgroundResource(BarChartUtils.resolveBarBackgroundRes(
                        entry,
                        now,
                        i == selectedChartBucketIndex
                ));
            } else {
                targetHeightsPx[i] = 0;
                targetVisibilities[i] = false;
            }
        }

        if (suppressBarHeightAnimation) {
            shouldAnimateInitialChart = false;
            cancelBarAnimation();
            barHeightAnimator.applyState(targetHeightsPx, targetVisibilities);
        } else if (shouldAnimateInitialChart) {
            shouldAnimateInitialChart = false;
            barHeightAnimator.applyState(new int[BAR_IDS.length], targetVisibilities);
            chartContainer.post(() -> barHeightAnimator.animate(
                    new int[BAR_IDS.length],
                    targetHeightsPx,
                    targetVisibilities,
                    BAR_ANIMATION_DURATION_MS,
                    BAR_ANIMATION_STAGGER_MS
            ));
        } else {
            chartContainer.post(() -> barHeightAnimator.animateUpdates(
                    targetHeightsPx,
                    targetVisibilities,
                    BAR_UPDATE_ANIMATION_DURATION_MS
            ));
        }
    }

    private int resolveMaxBarHeightPx() {
        int fallbackHeightPx = Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                CHART_MAX_HEIGHT_DP,
                activity.getResources().getDisplayMetrics()
        ));
        int containerHeightPx = chartVisualContainer != null ? chartVisualContainer.getHeight() : 0;
        if (containerHeightPx <= 0) {
            containerHeightPx = fallbackHeightPx;
        }
        int availableHeightPx = containerHeightPx
                - barChartContainer.getPaddingTop()
                - barChartContainer.getPaddingBottom();
        return Math.max(0, availableHeightPx);
    }

    private void renderGraph(int chartMode,
                             List<PriceFetcher.PriceEntry> graphDisplayEntries,
                             double graphMaxPrice) {
        cancelBarAnimation();
        cancelGraphAnimation();
        barChartContainer.setVisibility(View.GONE);
        if (shouldAnimateInitialChart) {
            graphImageView.setAlpha(0f);
        }
        graphImageView.setVisibility(View.VISIBLE);

        int width = graphImageView.getWidth();
        int height = graphImageView.getHeight();
        if (width <= 0 || height <= 0) {
            graphImageView.post(renderCallback);
            return;
        }

        if (chartMode == MODE_LINES) {
            graphImageView.setImageBitmap(GraphUtils.createStepLineGraphBitmap(
                    activity,
                    graphDisplayEntries,
                    graphMaxPrice,
                    width,
                    height,
                    selectedChartFraction
            ));
            animateGraphIfNeeded();
            return;
        }

        graphImageView.setImageBitmap(GraphUtils.createLineGraphBitmapCubic(
                activity,
                graphDisplayEntries,
                graphMaxPrice,
                width,
                height,
                ZonedDateTime.now(ZoneId.systemDefault()),
                selectedChartFraction
        ));
        animateGraphIfNeeded();
    }

    private void updateTimeLabels(List<PriceFetcher.PriceEntry> displayEntries) {
        for (int i = 0; i < TIME_LABEL_IDS.length; i++) {
            TextView label = activity.findViewById(TIME_LABEL_IDS[i]);
            int barIndex = TIME_BAR_INDICES[i];
            if (barIndex < displayEntries.size()) {
                ZonedDateTime start = displayEntries.get(barIndex)
                        .startTime
                        .atZoneSameInstant(ZoneId.systemDefault());
                label.setText(String.format("%02d", start.getHour()));
            } else {
                label.setText("");
            }
        }
    }

    private List<PriceFetcher.PriceEntry> getEntriesInRange(List<PriceFetcher.PriceEntry> allEntries,
                                                            OffsetDateTime start,
                                                            OffsetDateTime end) {
        List<PriceFetcher.PriceEntry> entriesInRange = new ArrayList<>();
        for (PriceFetcher.PriceEntry entry : allEntries) {
            if (entry.endTime.isAfter(start) && entry.startTime.isBefore(end)) {
                entriesInRange.add(entry);
            } else if (entry.startTime.isAfter(end)) {
                break;
            }
        }
        if (entriesInRange.isEmpty() && !allEntries.isEmpty()) {
            entriesInRange.add(allEntries.get(Math.max(0, CurrentPriceResolver.findCurrentIndex(allEntries))));
        }
        return entriesInRange;
    }

    private List<List<PriceFetcher.PriceEntry>> buildDisplayedBucketEntries(
            List<PriceFetcher.PriceEntry> buckets,
            List<PriceFetcher.PriceEntry> allEntries) {
        List<List<PriceFetcher.PriceEntry>> bucketEntries = new ArrayList<>();
        for (PriceFetcher.PriceEntry bucket : buckets) {
            List<PriceFetcher.PriceEntry> entries = new ArrayList<>();
            for (PriceFetcher.PriceEntry entry : allEntries) {
                if (entry.endTime.isAfter(bucket.startTime) && entry.startTime.isBefore(bucket.endTime)) {
                    entries.add(entry);
                } else if (entry.startTime.isAfter(bucket.endTime)) {
                    break;
                }
            }
            bucketEntries.add(entries);
        }
        return bucketEntries;
    }

    private void showTooltip(MotionEvent event) {
        if (displayedBarEntries.isEmpty()) {
            return;
        }

        int width = chartTouchOverlay.getWidth();
        if (width <= 0) {
            return;
        }

        float clampedX = Math.max(0f, Math.min(event.getX(), width - 1f));
        float selectedFraction = clampedX / (float) Math.max(1, width - 1);
        int bucketIndex = Math.min(
                displayedBarEntries.size() - 1,
                Math.max(0, (int) ((clampedX / (float) width) * displayedBarEntries.size()))
        );
        updateSelection(bucketIndex, selectedFraction);
        showTooltipPopup(
                buildTooltipText(bucketIndex),
                event.getRawX(),
                event.getRawY()
        );
    }

    private String buildTooltipText(int bucketIndex) {
        if (bucketIndex < 0 || bucketIndex >= displayedBucketEntries.size()) {
            return "";
        }

        String country = getSelectedCountryCode();
        List<PriceFetcher.PriceEntry> bucketEntries = displayedBucketEntries.get(bucketIndex);
        if (bucketEntries.isEmpty()) {
            PriceFetcher.PriceEntry bucket = displayedBarEntries.get(bucketIndex);
            return String.format(
                    "%s: %s",
                    formatTimeRangeForTooltip(bucket.startTime, bucket.endTime),
                    PriceDisplayUtils.formatPrice(bucket.pricePerKwh, country, sharedPreferences)
            );
        }

        StringBuilder text = new StringBuilder();
        for (int i = 0; i < bucketEntries.size(); i++) {
            PriceFetcher.PriceEntry entry = bucketEntries.get(i);
            if (i > 0) {
                text.append('\n');
            }
            text.append(formatTimeRangeForTooltip(entry.startTime, entry.endTime))
                    .append(": ")
                    .append(PriceDisplayUtils.formatPrice(entry.pricePerKwh, country, sharedPreferences));
        }
        return text.toString();
    }

    private void logChartDiagnostics(List<PriceFetcher.PriceEntry> allData,
                                     List<PriceFetcher.PriceEntry> hourlyData,
                                     List<PriceFetcher.PriceEntry> graphDisplayEntries,
                                     int chartMode) {
        Log.d(TAG, "renderChart mode=" + chartMode
                + " displayedBuckets=" + displayedBarEntries.size()
                + " graphEntries=" + graphDisplayEntries.size());
        Log.d(TAG, PriceFetcher.describeEntriesForLog("renderChart allData", allData));
        Log.d(TAG, PriceFetcher.describeEntriesForLog("renderChart hourlyData", hourlyData));
        Log.d(TAG, "renderChart graphWindow="
                + PriceFetcher.describeEntryTimesForLog(graphDisplayEntries));
        for (int i = 0; i < displayedBarEntries.size() && i < displayedBucketEntries.size(); i++) {
            PriceFetcher.PriceEntry bucket = displayedBarEntries.get(i);
            List<PriceFetcher.PriceEntry> bucketEntries = displayedBucketEntries.get(i);
            Log.d(TAG, "bucket[" + i + "] "
                    + formatTimeRangeForTooltip(bucket.startTime, bucket.endTime)
                    + " -> "
                    + PriceFetcher.describeEntryTimesForLog(bucketEntries));
        }
    }

    private String formatTimeRangeForTooltip(OffsetDateTime startTime, OffsetDateTime endTime) {
        ZonedDateTime start = startTime.atZoneSameInstant(ZoneId.systemDefault());
        ZonedDateTime end = endTime.atZoneSameInstant(ZoneId.systemDefault());
        long minutes = Duration.between(start, end).toMinutes();
        if (minutes == 15) {
            return String.format("%02d:%02d", start.getHour(), start.getMinute());
        }
        return String.format(
                "%02d:%02d-%02d:%02d",
                start.getHour(),
                start.getMinute(),
                end.getHour(),
                end.getMinute()
        );
    }

    private void showTooltipPopup(String text, float rawX, float rawY) {
        if (text == null || text.isEmpty()) {
            clearSelection(true);
            return;
        }

        dismissTooltipPopup(true);

        TextView tooltipView = new TextView(activity);
        tooltipView.setText(text);
        tooltipView.setTextColor(MaterialColors.getColor(
                tooltipView,
                com.google.android.material.R.attr.colorOnSurface
        ));
        tooltipView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        tooltipView.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        int horizontalPadding = dpToPx(TOOLTIP_HORIZONTAL_PADDING_DP);
        int verticalPadding = dpToPx(TOOLTIP_VERTICAL_PADDING_DP);
        tooltipView.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);

        GradientDrawable background = new GradientDrawable();
        background.setColor(MaterialColors.getColor(
                tooltipView,
                com.google.android.material.R.attr.colorSurfaceContainerHighest
        ));
        background.setCornerRadius(dpToPx(14));
        tooltipView.setBackground(background);
        tooltipView.setElevation(dpToPx(12));

        int shadowPadding = dpToPx(24);
        FrameLayout tooltipContainer = new FrameLayout(activity);
        tooltipContainer.setClipChildren(false);
        tooltipContainer.setClipToPadding(false);
        tooltipContainer.setPadding(shadowPadding, shadowPadding, shadowPadding, shadowPadding);
        tooltipContainer.addView(tooltipView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        ));

        tooltipContainer.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );

        chartTooltipPopup = new PopupWindow(tooltipContainer, -2, -2, false);
        chartTooltipPopup.setAnimationStyle(R.style.ChartTooltipAnimation);
        chartTooltipPopup.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        chartTooltipPopup.setOutsideTouchable(true);
        chartTooltipPopup.setClippingEnabled(false);
        chartTooltipPopup.setOnDismissListener(() -> {
            chartTooltipPopup = null;
            if (suppressNextTooltipDismissCallback) {
                suppressNextTooltipDismissCallback = false;
                return;
            }
            clearSelection(true);
        });

        View root = activity.getWindow().getDecorView();
        int popupWidth = tooltipContainer.getMeasuredWidth();
        int popupHeight = tooltipContainer.getMeasuredHeight();
        int margin = dpToPx(4);
        int x = Math.round(rawX - (popupWidth / 2f));
        x = Math.max(margin, Math.min(x, root.getWidth() - popupWidth - margin));
        int y = Math.round(rawY - popupHeight - dpToPx(TOOLTIP_VERTICAL_OFFSET_DP));
        y = Math.max(margin, y);

        chartTooltipPopup.showAtLocation(root, Gravity.NO_GRAVITY, x, y);
    }

    private void dismissTooltipPopup(boolean suppressDismissCallback) {
        if (chartTooltipPopup == null) {
            return;
        }
        suppressNextTooltipDismissCallback = suppressDismissCallback;
        PopupWindow popup = chartTooltipPopup;
        chartTooltipPopup = null;
        popup.dismiss();
    }

    private void clearSelection(boolean rerenderChart) {
        dismissTooltipPopup(true);
        boolean hadSelection = selectedChartBucketIndex >= 0 || !Float.isNaN(selectedChartFraction);
        selectedChartBucketIndex = -1;
        selectedChartFraction = Float.NaN;
        if (hadSelection && rerenderChart) {
            rerenderSelectionState();
        }
    }

    private void updateSelection(int bucketIndex, float selectionFraction) {
        if (selectedChartBucketIndex == bucketIndex
                && Math.abs(selectedChartFraction - selectionFraction) < 0.0001f) {
            return;
        }
        selectedChartBucketIndex = bucketIndex;
        selectedChartFraction = selectionFraction;
        rerenderSelectionState();
    }

    private void rerenderSelectionState() {
        if (chartContainer.getVisibility() != View.VISIBLE) {
            return;
        }
        int chartMode = getChartMode();
        if (chartMode == MODE_BARS) {
            updateBarSelectionState();
            return;
        }
        if (!displayedGraphEntries.isEmpty()) {
            renderGraph(chartMode, displayedGraphEntries, displayedChartScaleMax);
        }
    }

    private void updateBarSelectionState() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        for (int i = 0; i < BAR_IDS.length; i++) {
            ImageView bar = activity.findViewById(BAR_IDS[i]);
            if (i >= displayedBarEntries.size()) {
                continue;
            }
            bar.setBackgroundResource(BarChartUtils.resolveBarBackgroundRes(
                    displayedBarEntries.get(i),
                    now,
                    i == selectedChartBucketIndex
            ));
        }
    }

    private int getChartMode() {
        int storedMode = sharedPreferences.getInt(KEY_CHART_MODE, MODE_BARS);
        if (storedMode == MODE_GRAPH || storedMode == MODE_LINES) {
            return storedMode;
        }
        return MODE_BARS;
    }

    private int getChartModeButtonId(int chartMode) {
        if (chartMode == MODE_GRAPH) {
            return R.id.main_chart_graph_button;
        }
        if (chartMode == MODE_LINES) {
            return R.id.main_chart_lines_button;
        }
        return R.id.main_chart_bars_button;
    }

    private int getChartModeForButton(int buttonId) {
        if (buttonId == R.id.main_chart_graph_button) {
            return MODE_GRAPH;
        }
        if (buttonId == R.id.main_chart_lines_button) {
            return MODE_LINES;
        }
        return MODE_BARS;
    }

    private int getBarPoolMode() {
        int storedMode = sharedPreferences.getInt(KEY_BAR_POOL_MODE, WidgetPreferences.POOL_MODE_AVERAGE);
        if (storedMode == WidgetPreferences.POOL_MODE_MIN || storedMode == WidgetPreferences.POOL_MODE_MAX) {
            return storedMode;
        }
        return WidgetPreferences.POOL_MODE_AVERAGE;
    }

    private int getBarPoolButtonId(int poolMode) {
        if (poolMode == WidgetPreferences.POOL_MODE_MIN) {
            return R.id.main_bar_pool_min_button;
        }
        if (poolMode == WidgetPreferences.POOL_MODE_MAX) {
            return R.id.main_bar_pool_max_button;
        }
        return R.id.main_bar_pool_average_button;
    }

    private int getBarPoolModeForButton(int buttonId) {
        if (buttonId == R.id.main_bar_pool_min_button) {
            return WidgetPreferences.POOL_MODE_MIN;
        }
        if (buttonId == R.id.main_bar_pool_max_button) {
            return WidgetPreferences.POOL_MODE_MAX;
        }
        return WidgetPreferences.POOL_MODE_AVERAGE;
    }

    private void cancelBarAnimation() {
        if (barHeightAnimator != null) {
            barHeightAnimator.cancel();
        }
    }

    private void animateGraphIfNeeded() {
        if (!shouldAnimateInitialChart) {
            graphImageView.setAlpha(1f);
            return;
        }

        shouldAnimateInitialChart = false;
        graphImageView.post(() -> graphImageView.animate()
                .alpha(1f)
                .setDuration(GRAPH_FADE_IN_DURATION_MS)
                .setInterpolator(new LinearOutSlowInInterpolator())
                .start());
    }

    private void cancelGraphAnimation() {
        graphImageView.animate().cancel();
        graphImageView.setAlpha(1f);
    }

    private boolean isYAxisEnabled() {
        return sharedPreferences.getBoolean(KEY_SHOW_Y_AXIS, true);
    }

    private void updateYAxisVisibility(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        chartYAxisContainer.setVisibility(visibility);
        chartYAxisGuides.setVisibility(visibility);
        chartYAxisSpacer.setVisibility(visibility);
        if (!visible) {
            setYAxisTicksVisible(false);
        }
    }

    private void setYAxisTicksVisible(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.INVISIBLE;
        chartYAxisTopValue.setVisibility(visibility);
        chartYAxisUpperMidValue.setVisibility(visibility);
        chartYAxisLowerMidValue.setVisibility(visibility);
        chartYAxisBottomValue.setVisibility(visibility);
        chartYAxisTopGuide.setVisibility(visibility);
        chartYAxisUpperMidGuide.setVisibility(visibility);
        chartYAxisLowerMidGuide.setVisibility(visibility);
        chartYAxisBottomGuide.setVisibility(visibility);
    }

    private void updateYAxis(double maxPricePerKwh) {
        boolean showYAxis = isYAxisEnabled();
        updateYAxisVisibility(showYAxis);
        if (!showYAxis) {
            return;
        }

        double safeMaxPrice = maxPricePerKwh > 0.0 ? maxPricePerKwh : 1.0;
        if (chartYAxisContainer.getHeight() <= 0 || chartYAxisGuides.getHeight() <= 0) {
            setYAxisTicksVisible(false);
            chartYAxisContainer.post(() -> updateYAxis(safeMaxPrice));
            return;
        }

        TextView[] tickLabels = {
                chartYAxisTopValue,
                chartYAxisUpperMidValue,
                chartYAxisLowerMidValue,
                chartYAxisBottomValue
        };
        View[] tickGuides = {
                chartYAxisTopGuide,
                chartYAxisUpperMidGuide,
                chartYAxisLowerMidGuide,
                chartYAxisBottomGuide
        };
        String countryCode = getSelectedCountryCode();

        for (int i = 0; i < ChartYAxisUtils.TICK_FRACTIONS.length; i++) {
            double tickValue = ChartYAxisUtils.normalizeTickValue(
                    safeMaxPrice * ChartYAxisUtils.TICK_FRACTIONS[i]
            );
            bindYAxisTick(
                    tickLabels[i],
                    tickGuides[i],
                    tickValue,
                    ChartYAxisUtils.TICK_FRACTIONS[i],
                    countryCode
            );
        }
        setYAxisTicksVisible(true);
    }

    private void bindYAxisTick(TextView label,
                               View guide,
                               double tickValue,
                               float tickFraction,
                               String countryCode) {
        label.setText(ChartYAxisUtils.formatAxisValue(tickValue, countryCode));
        if (label.getHeight() <= 0) {
            int availableWidth = Math.max(
                    0,
                    chartYAxisContainer.getWidth()
                            - chartYAxisContainer.getPaddingLeft()
                            - chartYAxisContainer.getPaddingRight()
            );
            label.measure(
                    View.MeasureSpec.makeMeasureSpec(availableWidth, View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            );
        }

        float fraction = Math.max(0f, Math.min(1f, tickFraction));
        int axisHeight = chartYAxisContainer.getHeight();
        int guideHeight = chartYAxisGuides.getHeight();
        float edgeMarginPx = dpToPx(Y_AXIS_EDGE_MARGIN_DP);
        float axisTop = chartYAxisContainer.getPaddingTop();
        float axisBottom = axisHeight - chartYAxisContainer.getPaddingBottom();
        float usableAxisHeight = Math.max(1f, axisBottom - axisTop);
        float guideTop = chartYAxisGuides.getPaddingTop();
        float guideBottom = guideHeight - chartYAxisGuides.getPaddingBottom();
        float usableGuideHeight = Math.max(1f, guideBottom - guideTop);
        float centerAxisY = axisBottom - (usableAxisHeight * fraction);
        float centerGuideY = guideBottom - (usableGuideHeight * fraction);

        label.setVisibility(View.VISIBLE);
        float minLabelY = axisTop + edgeMarginPx;
        float maxLabelY = Math.max(minLabelY, axisBottom - edgeMarginPx - label.getMeasuredHeight());
        label.setY(clamp(
                centerAxisY - (label.getMeasuredHeight() / 2f),
                minLabelY,
                maxLabelY
        ));

        guide.setVisibility(View.VISIBLE);
        float minGuideY = guideTop + edgeMarginPx;
        float maxGuideY = Math.max(minGuideY, guideBottom - edgeMarginPx - guide.getHeight());
        guide.setY(clamp(
                centerGuideY - (guide.getHeight() / 2f),
                minGuideY,
                maxGuideY
        ));
    }

    private String getSelectedCountryCode() {
        return PriceRepository.getSelectedCountryCode(activity, sharedPreferences);
    }

    private int dpToPx(int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                activity.getResources().getDisplayMetrics()
        ));
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
