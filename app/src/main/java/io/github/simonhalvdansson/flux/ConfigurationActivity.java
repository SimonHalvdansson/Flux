package io.github.simonhalvdansson.flux;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButtonToggleGroup;

public class ConfigurationActivity extends AppCompatActivity {

    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private boolean isListWidget;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        View doneButton = findViewById(R.id.done_button);

        chartToggleGroup.setSelectionRequired(true);
        barPoolToggleGroup.setSelectionRequired(true);
        listIncrementToggleGroup.setSelectionRequired(true);
        listPoolToggleGroup.setSelectionRequired(true);

        if (isListWidget) {
            titleView.setText(R.string.list_widget_settings_title);
            mainWidgetSection.setVisibility(View.GONE);
            listWidgetSection.setVisibility(View.VISIBLE);

            int incrementMinutes = WidgetPreferences.getListIncrementMinutes(prefs);
            listIncrementToggleGroup.check(getIncrementButtonId(incrementMinutes));

            int listPoolMode = WidgetPreferences.getListPoolMode(prefs);
            listPoolToggleGroup.check(listPoolMode == WidgetPreferences.POOL_MODE_MIN
                    ? R.id.list_pool_min_button
                    : R.id.list_pool_average_button);
            updateListPoolVisibility(listPoolContainer, incrementMinutes, false);

            listIncrementToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (!isChecked) {
                    return;
                }
                int minutes = getIncrementMinutesForButton(checkedId);
                prefs.edit().putInt(WidgetPreferences.KEY_LIST_INCREMENT_MINUTES, minutes).apply();
                updateListPoolVisibility(listPoolContainer, minutes, true);
            });

            listPoolToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (!isChecked) {
                    return;
                }
                int poolMode = checkedId == R.id.list_pool_min_button
                        ? WidgetPreferences.POOL_MODE_MIN
                        : WidgetPreferences.POOL_MODE_AVERAGE;
                prefs.edit().putInt(WidgetPreferences.KEY_LIST_POOL_MODE, poolMode).apply();
            });
        } else {
            titleView.setText(R.string.main_widget_settings_title);
            mainWidgetSection.setVisibility(View.VISIBLE);
            listWidgetSection.setVisibility(View.GONE);

            int chartMode = WidgetPreferences.getChartMode(prefs);
            chartToggleGroup.check(chartMode == WidgetPreferences.CHART_MODE_GRAPH
                    ? R.id.chart_graph_button
                    : R.id.chart_bars_button);

            int barPoolMode = WidgetPreferences.getMainBarPoolMode(prefs);
            barPoolToggleGroup.check(barPoolMode == WidgetPreferences.POOL_MODE_MIN
                    ? R.id.bar_pool_min_button
                    : R.id.bar_pool_average_button);
            updateBarPoolVisibility(barPoolContainer, chartMode);

            chartToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (!isChecked) {
                    return;
                }
                int mode = checkedId == R.id.chart_graph_button
                        ? WidgetPreferences.CHART_MODE_GRAPH
                        : WidgetPreferences.CHART_MODE_BARS;
                prefs.edit().putInt(PriceUpdateJobService.KEY_CHART_MODE, mode).apply();
                updateBarPoolVisibility(barPoolContainer, mode);
            });

            barPoolToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (!isChecked) {
                    return;
                }
                int poolMode = checkedId == R.id.bar_pool_min_button
                        ? WidgetPreferences.POOL_MODE_MIN
                        : WidgetPreferences.POOL_MODE_AVERAGE;
                prefs.edit().putInt(WidgetPreferences.KEY_MAIN_BAR_POOL_MODE, poolMode).apply();
            });
        }

        doneButton.setOnClickListener(v -> finishWithSuccess());
    }

    private void applyWindowInsets() {
        View root = findViewById(R.id.main_container);
        int padStart = root.getPaddingStart();
        int padTop = root.getPaddingTop();
        int padEnd = root.getPaddingEnd();
        int padBottom = root.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            root.setPaddingRelative(
                    padStart + bars.left,
                    padTop + bars.top,
                    padEnd + bars.right,
                    padBottom + bars.bottom
            );
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void updateBarPoolVisibility(View container, int chartMode) {
        container.setVisibility(chartMode == WidgetPreferences.CHART_MODE_BARS ? View.VISIBLE : View.GONE);
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
        WidgetPresenceUtils.ensureUpdateJobScheduled(this);
        PriceUpdateScheduler.schedulePriceUpdateJob(this);
        MainWidget.updateAllWidgets(this);
        ListWidget.updateAllWidgets(this);

        Intent result = new Intent();
        result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        setResult(RESULT_OK, result);
        finish();
    }
}