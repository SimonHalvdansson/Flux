package io.github.simonhalvdansson.flux;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButtonToggleGroup;

public class ConfigurationActivity extends AppCompatActivity {
    private static final long SECTION_VISIBILITY_ANIMATION_MS = 180L;

    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private boolean isListWidget;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_configuration);
        applyWindowInsets();

        Intent intent = getIntent();
        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        Intent cancelResult = new Intent();
        cancelResult.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        setResult(RESULT_CANCELED, cancelResult);

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        AppWidgetProviderInfo providerInfo = appWidgetManager.getAppWidgetInfo(appWidgetId);
        if (providerInfo == null) {
            finish();
            return;
        }

        ComponentName listWidgetComponent = new ComponentName(this, ListWidget.class);
        isListWidget = listWidgetComponent.equals(providerInfo.provider);

        SharedPreferences prefs = PriceRepository.getPreferences(this);
        TextView titleView = findViewById(R.id.config_title);
        LinearLayout mainWidgetSection = findViewById(R.id.main_widget_settings_section);
        LinearLayout listWidgetSection = findViewById(R.id.list_widget_settings_section);
        MaterialButtonToggleGroup chartToggleGroup = findViewById(R.id.chart_toggle_group);
        LinearLayout barPoolContainer = findViewById(R.id.bar_pool_container);
        MaterialButtonToggleGroup barPoolToggleGroup = findViewById(R.id.bar_pool_toggle_group);
        MaterialButtonToggleGroup listIncrementToggleGroup = findViewById(R.id.list_increment_toggle_group);
        LinearLayout listPoolContainer = findViewById(R.id.list_pool_container);
        MaterialButtonToggleGroup listPoolToggleGroup = findViewById(R.id.list_pool_toggle_group);
        View chartWidgetPreview = findViewById(R.id.chart_widget_preview);
        View doneButton = findViewById(R.id.done_button);

        chartToggleGroup.setSelectionRequired(true);
        barPoolToggleGroup.setSelectionRequired(true);
        listIncrementToggleGroup.setSelectionRequired(true);
        listPoolToggleGroup.setSelectionRequired(true);

        if (isListWidget) {
            titleView.setText(R.string.list_widget_settings_title);
            mainWidgetSection.setVisibility(View.GONE);
            listWidgetSection.setVisibility(View.VISIBLE);

            int incrementMinutes = WidgetPreferences.getListIncrementMinutes(prefs, appWidgetId);
            listIncrementToggleGroup.check(getIncrementButtonId(incrementMinutes));

            int listPoolMode = WidgetPreferences.getListPoolMode(prefs, appWidgetId);
            listPoolToggleGroup.check(listPoolMode == WidgetPreferences.POOL_MODE_MIN
                    ? R.id.list_pool_min_button
                    : listPoolMode == WidgetPreferences.POOL_MODE_MAX
                    ? R.id.list_pool_max_button
                    : R.id.list_pool_average_button);
            updateListPoolVisibility(listPoolContainer, incrementMinutes, false);

            listIncrementToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (!isChecked) {
                    return;
                }
                int minutes = getIncrementMinutesForButton(checkedId);
                WidgetPreferences.setListIncrementMinutes(prefs, appWidgetId, minutes);
                updateListPoolVisibility(listPoolContainer, minutes, true);
            });

            listPoolToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (!isChecked) {
                    return;
                }
                int poolMode = checkedId == R.id.list_pool_min_button
                        ? WidgetPreferences.POOL_MODE_MIN
                        : checkedId == R.id.list_pool_max_button
                        ? WidgetPreferences.POOL_MODE_MAX
                        : WidgetPreferences.POOL_MODE_AVERAGE;
                WidgetPreferences.setListPoolMode(prefs, appWidgetId, poolMode);
            });
        } else {
            titleView.setText(R.string.main_widget_settings_title);
            mainWidgetSection.setVisibility(View.VISIBLE);
            listWidgetSection.setVisibility(View.GONE);
            chartWidgetPreview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

            int chartMode = WidgetPreferences.getChartMode(prefs, appWidgetId);
            chartToggleGroup.check(getChartButtonId(chartMode));

            int barPoolMode = WidgetPreferences.getMainBarPoolMode(prefs, appWidgetId);
            barPoolToggleGroup.check(barPoolMode == WidgetPreferences.POOL_MODE_MIN
                    ? R.id.bar_pool_min_button
                    : barPoolMode == WidgetPreferences.POOL_MODE_MAX
                    ? R.id.bar_pool_max_button
                    : R.id.bar_pool_average_button);
            updateBarPoolVisibility(barPoolContainer, chartMode, false);
            updateChartWidgetPreview(chartWidgetPreview, prefs, chartMode, barPoolMode);

            chartToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (!isChecked) {
                    return;
                }
                int mode = getChartModeForButton(checkedId);
                WidgetPreferences.setChartMode(prefs, appWidgetId, mode);
                updateBarPoolVisibility(barPoolContainer, mode, true);
                int selectedBarPoolMode = WidgetPreferences.getMainBarPoolMode(prefs, appWidgetId);
                updateChartWidgetPreview(chartWidgetPreview, prefs, mode, selectedBarPoolMode);
            });

            barPoolToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (!isChecked) {
                    return;
                }
                int poolMode = checkedId == R.id.bar_pool_min_button
                        ? WidgetPreferences.POOL_MODE_MIN
                        : checkedId == R.id.bar_pool_max_button
                        ? WidgetPreferences.POOL_MODE_MAX
                        : WidgetPreferences.POOL_MODE_AVERAGE;
                WidgetPreferences.setMainBarPoolMode(prefs, appWidgetId, poolMode);
                int selectedChartMode = WidgetPreferences.getChartMode(prefs, appWidgetId);
                updateChartWidgetPreview(chartWidgetPreview, prefs, selectedChartMode, poolMode);
            });
        }

        doneButton.setOnClickListener(v -> finishWithSuccess());
    }

    private void applyWindowInsets() {
        View scrollView = findViewById(R.id.main_container);
        View doneButtonContainer = findViewById(R.id.done_button_container);
        View scaffold = findViewById(R.id.config_scaffold);
        int scrollPadStart = scrollView.getPaddingStart();
        int scrollPadTop = scrollView.getPaddingTop();
        int scrollPadEnd = scrollView.getPaddingEnd();
        int scrollPadBottom = scrollView.getPaddingBottom();
        int buttonPadStart = doneButtonContainer.getPaddingStart();
        int buttonPadTop = doneButtonContainer.getPaddingTop();
        int buttonPadEnd = doneButtonContainer.getPaddingEnd();
        int buttonPadBottom = doneButtonContainer.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(scaffold, (view, insets) -> {
            Insets safeArea = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
            );
            scrollView.setPaddingRelative(
                    scrollPadStart + safeArea.left,
                    scrollPadTop + safeArea.top,
                    scrollPadEnd + safeArea.right,
                    scrollPadBottom
            );
            doneButtonContainer.setPaddingRelative(
                    buttonPadStart + safeArea.left,
                    buttonPadTop,
                    buttonPadEnd + safeArea.right,
                    buttonPadBottom + safeArea.bottom
            );
            return insets;
        });
        ViewCompat.requestApplyInsets(scaffold);
    }

    private void updateBarPoolVisibility(View container, int chartMode, boolean animate) {
        updateSectionVisibility(container, chartMode == WidgetPreferences.CHART_MODE_BARS, animate);
    }

    private void updateChartWidgetPreview(View previewRoot,
                                          SharedPreferences prefs,
                                          int chartMode,
                                          int barPoolMode) {
        ChartWidgetPreviewBinder.bind(previewRoot, prefs, chartMode, barPoolMode);
    }

    private int getChartButtonId(int chartMode) {
        if (chartMode == WidgetPreferences.CHART_MODE_GRAPH) {
            return R.id.chart_graph_button;
        }
        if (chartMode == WidgetPreferences.CHART_MODE_LINES) {
            return R.id.chart_lines_button;
        }
        return R.id.chart_bars_button;
    }

    private int getChartModeForButton(int checkedId) {
        if (checkedId == R.id.chart_graph_button) {
            return WidgetPreferences.CHART_MODE_GRAPH;
        }
        if (checkedId == R.id.chart_lines_button) {
            return WidgetPreferences.CHART_MODE_LINES;
        }
        return WidgetPreferences.CHART_MODE_BARS;
    }

    private void updateListPoolVisibility(View container, int incrementMinutes, boolean animate) {
        boolean enabled = WidgetPreferences.listPoolingEnabled(incrementMinutes);
        float targetAlpha = enabled ? 1f : 0.38f;
        container.setVisibility(View.VISIBLE);
        container.animate().cancel();
        if (animate) {
            if (enabled) {
                setViewEnabled(container, true);
            }
            container.animate()
                    .alpha(targetAlpha)
                    .setDuration(180L)
                    .withEndAction(() -> {
                        if (!enabled) {
                            setViewEnabled(container, false);
                        }
                    })
                    .start();
        } else {
            container.setAlpha(targetAlpha);
            setViewEnabled(container, enabled);
        }
    }

    private void updateSectionVisibility(View container, boolean visible, boolean animate) {
        container.animate().cancel();
        container.clearAnimation();

        if (!animate) {
            container.setAlpha(visible ? 1f : 0f);
            container.setVisibility(visible ? View.VISIBLE : View.GONE);
            setViewEnabled(container, visible);
            return;
        }

        if (visible) {
            container.setAlpha(0f);
            container.setVisibility(View.VISIBLE);
            setViewEnabled(container, true);
            container.animate()
                    .alpha(1f)
                    .setDuration(SECTION_VISIBILITY_ANIMATION_MS)
                    .withEndAction(null)
                    .start();
            return;
        }

        container.setAlpha(1f);
        container.animate()
                .alpha(0f)
                .setDuration(SECTION_VISIBILITY_ANIMATION_MS)
                .withEndAction(() -> {
                    container.setVisibility(View.GONE);
                    setViewEnabled(container, false);
                })
                .start();
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

    private int getIncrementButtonId(int incrementMinutes) {
        switch (incrementMinutes) {
            case WidgetPreferences.INCREMENT_30_MINUTES:
                return R.id.increment_30_button;
            case WidgetPreferences.INCREMENT_60_MINUTES:
                return R.id.increment_60_button;
            case WidgetPreferences.INCREMENT_120_MINUTES:
                return R.id.increment_120_button;
            case WidgetPreferences.INCREMENT_15_MINUTES:
            default:
                return R.id.increment_15_button;
        }
    }

    private int getIncrementMinutesForButton(int checkedId) {
        if (checkedId == R.id.increment_30_button) {
            return WidgetPreferences.INCREMENT_30_MINUTES;
        }
        if (checkedId == R.id.increment_60_button) {
            return WidgetPreferences.INCREMENT_60_MINUTES;
        }
        if (checkedId == R.id.increment_120_button) {
            return WidgetPreferences.INCREMENT_120_MINUTES;
        }
        return WidgetPreferences.INCREMENT_15_MINUTES;
    }

    private void finishWithSuccess() {
        Context appContext = getApplicationContext();
        WidgetPresenceUtils.ensureUpdateJobScheduled(this);
        PriceUpdateScheduler.schedulePriceUpdateJob(this);

        Intent result = new Intent();
        result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        setResult(RESULT_OK, result);
        finish();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            MainWidget.updateAllWidgets(appContext);
            ListWidget.updateAllWidgets(appContext);
        }, 250L);
    }
}
