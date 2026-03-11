package io.github.simonhalvdansson.flux;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.SharedPreferences;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Filter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String KEY_MAIN_ACTIVITY_CHART_MODE = "main_activity_chart_mode";
    private static final String KEY_MAIN_ACTIVITY_BAR_POOL_MODE = "main_activity_bar_pool_mode";
    private static final int MAIN_CHART_MODE_BARS = 0;
    private static final int MAIN_CHART_MODE_GRAPH = 1;
    private static final int MAIN_CHART_MODE_LINES = 2;
    private static final int TOOLTIP_VERTICAL_OFFSET_DP = 16;
    private static final int TOOLTIP_HORIZONTAL_PADDING_DP = 12;
    private static final int TOOLTIP_VERTICAL_PADDING_DP = 10;

    public static final String EXTRA_DISABLE_CHART_ANIMATION =
            "io.github.simonhalvdansson.flux.extra.DISABLE_CHART_ANIMATION";

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
    private static final int CHART_MAX_HEIGHT_DP = 160;
    private static final int MIN_BAR_HEIGHT_DP = 8;
    private static final long BAR_ANIMATION_DURATION_MS = 468L;
    private static final long BAR_ANIMATION_STAGGER_MS = 20L;
    private static final long GRAPH_FADE_IN_DURATION_MS = 420L;
    private static final long QUARTER_REFRESH_SLOP_MS = 250L;

    private final List<RegionConfig.Country> countries = RegionConfig.getCountries();

    private TextView currentPriceLabel;
    private TextView currentPriceValue;
    private TextView currentPriceUnit;
    private TextView todayAverageValue;
    private TextView tomorrowAverageValue;
    private View chartContainer;
    private LinearLayout barChartContainer;
    private ImageView graphImageView;
    private View chartTouchOverlay;
    private MaterialButtonToggleGroup mainChartToggleGroup;
    private SharedPreferences sharedPreferences;
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener;
    private AnimatorSet barAnimator;
    private PopupWindow chartTooltipPopup;
    private final Handler quarterRefreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable quarterRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            renderCurrentPrice();
            scheduleQuarterBoundaryRefresh();
        }
    };
    private boolean shouldAnimateInitialChart;
    private int currentCountryIndex = 0;
    private List<PriceFetcher.PriceEntry> displayedBarEntries = new ArrayList<>();
    private List<List<PriceFetcher.PriceEntry>> displayedBucketEntries = new ArrayList<>();

    private AutoCompleteTextView countryDropdown;
    private AutoCompleteTextView areaDropdown;
    private LinearLayout regionContainer;
    private LinearLayout priceDisplayContainer;
    private LinearLayout stromstotteContainer;
    private MaterialSwitch stromstotteSwitch;
    private MaterialSwitch vatSwitch;
    private TextView vatLabel;
    private TextInputLayout gridFeeContainer;
    private TextInputEditText gridFeeInput;
    private MaterialButtonToggleGroup swissPriceUnitToggleGroup;
    private View mainBarPoolContainer;
    private MaterialButtonToggleGroup mainBarPoolToggleGroup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        sharedPreferences = PriceRepository.getPreferences(this);
        ImageView appIconView = findViewById(R.id.app_icon);
        currentPriceLabel = findViewById(R.id.current_price_label);
        currentPriceValue = findViewById(R.id.current_price_value);
        currentPriceUnit = findViewById(R.id.current_price_unit);
        todayAverageValue = findViewById(R.id.today_average_value);
        tomorrowAverageValue = findViewById(R.id.tomorrow_average_value);
        chartContainer = findViewById(R.id.bar_chart_section);
        barChartContainer = findViewById(R.id.bar_chart_container);
        graphImageView = findViewById(R.id.graph_image);
        chartTouchOverlay = findViewById(R.id.chart_touch_overlay);
        mainChartToggleGroup = findViewById(R.id.main_chart_toggle_group);
        shouldAnimateInitialChart = savedInstanceState == null
                && !getIntent().getBooleanExtra(EXTRA_DISABLE_CHART_ANIMATION, false);

        setupAppSettings();
        setupMainChartModeToggle();
        setupChartTouchOverlay();
        configureAppIconShadow(appIconView);
        configureBarShadows();
        applyWindowInsets();
        setupAboutButton();

        preferenceChangeListener = (prefs, key) -> {
            if (PriceRepository.KEY_JSON_DATA.equals(key)
                    || PriceUpdateJobService.KEY_API_ERROR.equals(key)
                    || PriceUpdateJobService.KEY_SELECTED_COUNTRY.equals(key)
                    || PriceUpdateJobService.KEY_SELECTED_AREA.equals(key)
                    || PriceUpdateJobService.KEY_APPLY_VAT.equals(key)
                    || PriceUpdateJobService.KEY_APPLY_STROMSTOTTE.equals(key)
                    || PriceUpdateJobService.KEY_GRID_FEE.equals(key)
                    || PriceUpdateJobService.KEY_PRICE_DISPLAY_STYLE.equals(key)
                    || KEY_MAIN_ACTIVITY_CHART_MODE.equals(key)
                    || KEY_MAIN_ACTIVITY_BAR_POOL_MODE.equals(key)) {
                runOnUiThread(this::renderCurrentPrice);
            }
        };

        PriceUpdateScheduler.schedulePriceUpdateJob(this);
        refreshPrices();
    }

    @Override
    protected void onStart() {
        super.onStart();
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener);
        renderCurrentPrice();
        scheduleQuarterBoundaryRefresh();
    }

    @Override
    protected void onStop() {
        cancelBarAnimation();
        cancelGraphAnimation();
        dismissChartTooltip();
        cancelQuarterBoundaryRefresh();
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
        super.onStop();
    }

    private void setupAppSettings() {
        countryDropdown = findViewById(R.id.country_dropdown);
        areaDropdown = findViewById(R.id.area_dropdown);
        regionContainer = findViewById(R.id.region_container);
        priceDisplayContainer = findViewById(R.id.price_display_container);
        stromstotteContainer = findViewById(R.id.stromstotte_container);
        stromstotteSwitch = findViewById(R.id.stromstotte_switch);
        vatSwitch = findViewById(R.id.vat_switch);
        vatLabel = findViewById(R.id.vat_label);
        gridFeeContainer = findViewById(R.id.grid_fee_container);
        gridFeeInput = findViewById(R.id.grid_fee_input);
        swissPriceUnitToggleGroup = findViewById(R.id.swiss_price_unit_toggle_group);
        mainBarPoolContainer = findViewById(R.id.main_bar_pool_container);
        mainBarPoolToggleGroup = findViewById(R.id.main_bar_pool_toggle_group);
        swissPriceUnitToggleGroup.setSelectionRequired(true);
        mainBarPoolToggleGroup.setSelectionRequired(true);

        List<String> countryNames = new ArrayList<>();
        for (RegionConfig.Country country : countries) {
            countryNames.add(country.getDisplayName());
        }
        ArrayAdapter<String> countryAdapter = createDropdownAdapter(countryNames);
        countryDropdown.setAdapter(countryAdapter);

        String selectedCountry = sharedPreferences.getString(PriceUpdateJobService.KEY_SELECTED_COUNTRY, "NO");
        currentCountryIndex = RegionConfig.indexOfCountryCode(selectedCountry);
        if (currentCountryIndex < 0) {
            currentCountryIndex = RegionConfig.indexOfCountryCode("NO");
        }
        if (currentCountryIndex < 0) {
            currentCountryIndex = 0;
        }

        RegionConfig.Country currentCountry = countries.get(currentCountryIndex);
        countryDropdown.setText(currentCountry.getDisplayName(), false);
        updateAreaDropdown(areaDropdown, sharedPreferences, currentCountry);

        boolean applyStromstotte = sharedPreferences.getBoolean(PriceUpdateJobService.KEY_APPLY_STROMSTOTTE, false);
        boolean isNorway = "NO".equals(currentCountry.getCode());
        stromstotteContainer.setVisibility(isNorway ? View.VISIBLE : View.GONE);
        stromstotteSwitch.setChecked(isNorway && applyStromstotte);
        if (!isNorway && applyStromstotte) {
            sharedPreferences.edit().putBoolean(PriceUpdateJobService.KEY_APPLY_STROMSTOTTE, false).apply();
        }

        updateRegionVisibility(regionContainer, areaDropdown, currentCountry);
        updatePriceDisplayVisibility(currentCountry.getCode());
        vatSwitch.setChecked(sharedPreferences.getBoolean(PriceUpdateJobService.KEY_APPLY_VAT, true));
        updateVatLabel(vatLabel);
        updateGridFeeUnit(gridFeeContainer, currentCountry.getCode());
        mainBarPoolToggleGroup.check(getMainBarPoolButtonId(getMainBarPoolMode()));
        setupInfoDialogs();

        String savedGridFee = sharedPreferences.getString(PriceUpdateJobService.KEY_GRID_FEE, "");
        if (savedGridFee == null || savedGridFee.trim().isEmpty()) {
            savedGridFee = "0";
            sharedPreferences.edit().putString(PriceUpdateJobService.KEY_GRID_FEE, savedGridFee).apply();
        }
        gridFeeInput.setText(savedGridFee);

        countryDropdown.setOnItemClickListener((parent, view, position, id) -> {
            String selectedLabel = (String) parent.getItemAtPosition(position);
            RegionConfig.Country selected = findCountryByLabel(selectedLabel);
            if (selected == null) {
                return;
            }
            currentCountryIndex = countries.indexOf(selected);
            String countryCode = selected.getCode();
            sharedPreferences.edit().putString(PriceUpdateJobService.KEY_SELECTED_COUNTRY, countryCode).apply();

            updateAreaDropdown(areaDropdown, sharedPreferences, selected);

            boolean norway = "NO".equals(countryCode);
            stromstotteContainer.setVisibility(norway ? View.VISIBLE : View.GONE);
            if (!norway) {
                stromstotteSwitch.setChecked(false);
                sharedPreferences.edit().putBoolean(PriceUpdateJobService.KEY_APPLY_STROMSTOTTE, false).apply();
            }
            updateRegionVisibility(regionContainer, areaDropdown, selected);
            updatePriceDisplayVisibility(countryCode);
            updateVatLabel(vatLabel);
            updateGridFeeUnit(gridFeeContainer, countryCode);

            PriceUpdateScheduler.schedulePriceUpdateJob(MainActivity.this);
            refreshPrices();
        });

        countryDropdown.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                countryDropdown.showDropDown();
            }
        });
        countryDropdown.setOnClickListener(v -> countryDropdown.showDropDown());

        areaDropdown.setOnItemClickListener((parent, view, position, id) -> {
            List<RegionConfig.Area> areas = countries.get(currentCountryIndex).getAreas();
            String selectedLabel = (String) parent.getItemAtPosition(position);
            RegionConfig.Area selectedArea = findAreaByLabel(areas, selectedLabel);
            if (selectedArea == null) {
                return;
            }
            String area = selectedArea.getCode();
            sharedPreferences.edit().putString(PriceUpdateJobService.KEY_SELECTED_AREA, area).apply();
            PriceUpdateScheduler.schedulePriceUpdateJob(MainActivity.this);
            refreshPrices();
        });

        areaDropdown.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && countries.get(currentCountryIndex).hasMultipleAreas()) {
                areaDropdown.showDropDown();
            }
        });
        areaDropdown.setOnClickListener(v -> {
            if (countries.get(currentCountryIndex).hasMultipleAreas()) {
                areaDropdown.showDropDown();
            }
        });

        swissPriceUnitToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }

            String style = checkedId == R.id.swiss_unit_ct_button
                    ? PriceDisplayUtils.DISPLAY_STYLE_SWISS_CENTIMES
                    : PriceDisplayUtils.DISPLAY_STYLE_SWISS_RAPPEN;
            sharedPreferences.edit()
                    .putString(PriceUpdateJobService.KEY_PRICE_DISPLAY_STYLE, style)
                    .apply();
            updateGridFeeUnit(
                    gridFeeContainer,
                    sharedPreferences.getString(PriceUpdateJobService.KEY_SELECTED_COUNTRY, "NO")
            );
            updateWidgets();
        });

        stromstotteSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sharedPreferences.edit().putBoolean(PriceUpdateJobService.KEY_APPLY_STROMSTOTTE, isChecked).apply();
            updateWidgets();
        });

        vatSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sharedPreferences.edit().putBoolean(PriceUpdateJobService.KEY_APPLY_VAT, isChecked).apply();
            updateWidgets();
        });

        gridFeeInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                sharedPreferences.edit().putString(
                        PriceUpdateJobService.KEY_GRID_FEE,
                        s == null ? "" : s.toString()
                ).apply();
                updateWidgets();
            }
        });

        gridFeeInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                return;
            }
            CharSequence value = gridFeeInput.getText();
            if (value == null || value.toString().trim().isEmpty()) {
                gridFeeInput.setText("0");
            }
        });

        mainBarPoolToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            sharedPreferences.edit()
                    .putInt(KEY_MAIN_ACTIVITY_BAR_POOL_MODE, getMainBarPoolModeForButton(checkedId))
                    .apply();
            dismissChartTooltip();
            renderCurrentPrice();
        });
    }

    private void setupMainChartModeToggle() {
        mainChartToggleGroup.setSelectionRequired(true);
        mainChartToggleGroup.check(getMainChartModeButtonId(getMainChartMode()));
        updateMainBarPoolVisibility(getMainChartMode());
        mainChartToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            int chartMode = getMainChartModeForButton(checkedId);
            sharedPreferences.edit()
                    .putInt(KEY_MAIN_ACTIVITY_CHART_MODE, chartMode)
                    .apply();
            updateMainBarPoolVisibility(chartMode);
            dismissChartTooltip();
            renderCurrentPrice();
        });
    }

    private void updateMainBarPoolVisibility(int chartMode) {
        mainBarPoolContainer.setVisibility(chartMode == MAIN_CHART_MODE_BARS ? View.VISIBLE : View.GONE);
    }

    private void setupChartTouchOverlay() {
        chartTouchOverlay.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                v.performClick();
                showChartTooltip(event);
                return true;
            }
            return event.getAction() == MotionEvent.ACTION_DOWN;
        });
    }

    private void updateWidgets() {
        MainWidget.updateAllWidgets(this);
        ListWidget.updateAllWidgets(this);
    }

    private void applyWindowInsets() {
        View root = findViewById(R.id.main_container);
        View content = findViewById(R.id.main_content);
        int padStart = content.getPaddingStart();
        int padTop = content.getPaddingTop();
        int padEnd = content.getPaddingEnd();
        int padBottom = content.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets safeArea = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
            );
            content.setPaddingRelative(
                    padStart + safeArea.left,
                    padTop + safeArea.top,
                    padEnd + safeArea.right,
                    padBottom + safeArea.bottom
            );
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void configureAppIconShadow(ImageView appIconView) {
        float elevationPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                18,
                getResources().getDisplayMetrics()
        );
        appIconView.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        appIconView.setClipToOutline(false);
        appIconView.setElevation(elevationPx);
    }

    private void configureBarShadows() {
        float elevationPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                2,
                getResources().getDisplayMetrics()
        );
        float cornerRadiusPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                8,
                getResources().getDisplayMetrics()
        );

        for (int barId : BAR_IDS) {
            ImageView bar = findViewById(barId);
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

    private void refreshPrices() {
        Thread refreshThread = new Thread(() -> PriceRepository.refreshCachedPrices(getApplicationContext()));
        refreshThread.start();
    }

    private void scheduleQuarterBoundaryRefresh() {
        cancelQuarterBoundaryRefresh();
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        ZonedDateTime nextQuarter = now.truncatedTo(ChronoUnit.MINUTES)
                .plusMinutes(15 - (now.getMinute() % 15 == 0 ? 0 : now.getMinute() % 15));
        if (!nextQuarter.isAfter(now)) {
            nextQuarter = nextQuarter.plusMinutes(15);
        }
        long delayMs = Math.max(
                QUARTER_REFRESH_SLOP_MS,
                Duration.between(now, nextQuarter).toMillis() + QUARTER_REFRESH_SLOP_MS
        );
        quarterRefreshHandler.postDelayed(quarterRefreshRunnable, delayMs);
    }

    private void cancelQuarterBoundaryRefresh() {
        quarterRefreshHandler.removeCallbacks(quarterRefreshRunnable);
    }

    private void updateCurrentPriceLabel() {
        List<PriceFetcher.PriceEntry> allData = CurrentPriceResolver.getAdjustedEntries(sharedPreferences);
        if (allData.isEmpty()) {
            currentPriceLabel.setText(R.string.current_price_label);
            return;
        }

        int currentIndex = CurrentPriceResolver.findCurrentIndex(allData);
        PriceFetcher.PriceEntry currentEntry = allData.get(currentIndex);
        ZonedDateTime start = currentEntry.startTime.atZoneSameInstant(ZoneId.systemDefault());
        ZonedDateTime end = currentEntry.endTime.atZoneSameInstant(ZoneId.systemDefault());
        currentPriceLabel.setText(formatCurrentTimeRange(start, end));
    }

    private String formatCurrentTimeRange(ZonedDateTime start, ZonedDateTime end) {
        return String.format(
                "%02d:%02d-%02d:%02d:",
                start.getHour(),
                start.getMinute(),
                end.getHour(),
                end.getMinute()
        );
    }
    private void renderCurrentPrice() {
        updateCurrentPriceLabel();
        CurrentPriceResolver.Snapshot snapshot = CurrentPriceResolver.resolve(this);
        if (snapshot.hasData) {
            currentPriceValue.setText(snapshot.formattedPrice);
            if (snapshot.unitText != null && !snapshot.unitText.isEmpty()) {
                currentPriceUnit.setText(snapshot.unitText);
                currentPriceUnit.setVisibility(View.VISIBLE);
            } else {
                currentPriceUnit.setText("");
                currentPriceUnit.setVisibility(View.GONE);
            }
        } else if (snapshot.apiError) {
            currentPriceValue.setText(R.string.current_price_unavailable);
            currentPriceUnit.setText("");
            currentPriceUnit.setVisibility(View.GONE);
        } else {
            currentPriceValue.setText(R.string.current_price_loading);
            currentPriceUnit.setText("");
            currentPriceUnit.setVisibility(View.GONE);
        }

        renderBarChart();
    }

    private void renderBarChart() {
        dismissChartTooltip();

        List<PriceFetcher.PriceEntry> allData = CurrentPriceResolver.getAdjustedEntries(sharedPreferences);
        if (allData.isEmpty()) {
            clearChartData();
            cancelBarAnimation();
            chartContainer.setVisibility(View.GONE);
            renderAverageSummaries(new ArrayList<>());
            return;
        }

        List<PriceFetcher.PriceEntry> hourlyData = PriceFetcher.aggregateToHourly(allData, getMainBarPoolMode());
        if (hourlyData.isEmpty()) {
            clearChartData();
            cancelBarAnimation();
            chartContainer.setVisibility(View.GONE);
            renderAverageSummaries(hourlyData);
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

        displayedBarEntries = new ArrayList<>(hourlyData.subList(firstHourIndex, lastHourIndex + 1));
        displayedBucketEntries = buildDisplayedBucketEntries(displayedBarEntries, allData);

        double barMaxPrice = 0.0;
        for (PriceFetcher.PriceEntry entry : displayedBarEntries) {
            barMaxPrice = Math.max(barMaxPrice, entry.pricePerKwh);
        }
        if (barMaxPrice <= 0.0) {
            barMaxPrice = 1.0;
        }

        List<PriceFetcher.PriceEntry> graphDisplayEntries = getEntriesInRange(
                allData,
                displayedBarEntries.get(0).startTime,
                displayedBarEntries.get(displayedBarEntries.size() - 1).endTime
        );
        double graphMaxPrice = barMaxPrice;
        for (PriceFetcher.PriceEntry entry : graphDisplayEntries) {
            graphMaxPrice = Math.max(graphMaxPrice, entry.pricePerKwh);
        }
        if (graphMaxPrice <= 0.0) {
            graphMaxPrice = 1.0;
        }

        int chartMode = getMainChartMode();
        if (chartMode == MAIN_CHART_MODE_BARS) {
            renderBars(displayedBarEntries, barMaxPrice);
        } else {
            renderGraph(chartMode, graphDisplayEntries, graphMaxPrice);
        }

        logChartDiagnostics(allData, hourlyData, graphDisplayEntries, chartMode);
        updateTimeLabels(displayedBarEntries);
        renderAverageSummaries(hourlyData);
    }

    private void clearChartData() {
        displayedBarEntries = new ArrayList<>();
        displayedBucketEntries = new ArrayList<>();
        chartTouchOverlay.setEnabled(false);
        cancelGraphAnimation();
        graphImageView.setImageDrawable(null);
    }

    private void renderBars(List<PriceFetcher.PriceEntry> displayEntries, double maxPrice) {
        cancelGraphAnimation();
        barChartContainer.setVisibility(View.VISIBLE);
        graphImageView.setVisibility(View.GONE);

        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        int[] targetHeightsPx = new int[BAR_IDS.length];
        for (int i = 0; i < BAR_IDS.length; i++) {
            ImageView bar = findViewById(BAR_IDS[i]);
            if (i < displayEntries.size()) {
                PriceFetcher.PriceEntry entry = displayEntries.get(i);
                float barHeightDp = (float) Math.max(
                        MIN_BAR_HEIGHT_DP,
                        (entry.pricePerKwh / maxPrice) * CHART_MAX_HEIGHT_DP
                );
                targetHeightsPx[i] = Math.round(TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        barHeightDp,
                        getResources().getDisplayMetrics()
                ));
                bar.setVisibility(View.VISIBLE);

                ZonedDateTime start = entry.startTime.atZoneSameInstant(ZoneId.systemDefault());
                ZonedDateTime end = entry.endTime.atZoneSameInstant(ZoneId.systemDefault());
                if ((now.isEqual(start) || now.isAfter(start)) && now.isBefore(end)) {
                    bar.setBackgroundResource(R.drawable.bar_rounded_current);
                } else if (now.isAfter(end)) {
                    bar.setBackgroundResource(R.drawable.bar_rounded_old);
                } else {
                    bar.setBackgroundResource(R.drawable.bar_rounded);
                }
            } else {
                bar.setVisibility(View.INVISIBLE);
                targetHeightsPx[i] = 0;
            }
        }

        if (shouldAnimateInitialChart) {
            applyBarHeights(new int[BAR_IDS.length]);
            shouldAnimateInitialChart = false;
            chartContainer.post(() -> animateBars(targetHeightsPx, displayEntries.size()));
        } else {
            cancelBarAnimation();
            applyBarHeights(targetHeightsPx);
        }
    }

    private void renderGraph(int chartMode, List<PriceFetcher.PriceEntry> graphDisplayEntries, double graphMaxPrice) {
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
            graphImageView.post(this::renderCurrentPrice);
            return;
        }

        if (chartMode == MAIN_CHART_MODE_LINES) {
            graphImageView.setImageBitmap(GraphUtils.createStepLineGraphBitmap(
                    this,
                    graphDisplayEntries,
                    graphMaxPrice,
                    width,
                    height
            ));
            animateGraphIfNeeded();
            return;
        }

        graphImageView.setImageBitmap(GraphUtils.createLineGraphBitmapCubic(
                this,
                graphDisplayEntries,
                graphMaxPrice,
                width,
                height,
                ZonedDateTime.now(ZoneId.systemDefault())
        ));
        animateGraphIfNeeded();
    }

    private void updateTimeLabels(List<PriceFetcher.PriceEntry> displayEntries) {
        for (int i = 0; i < TIME_LABEL_IDS.length; i++) {
            TextView label = findViewById(TIME_LABEL_IDS[i]);
            int barIndex = TIME_BAR_INDICES[i];
            if (barIndex < displayEntries.size()) {
                ZonedDateTime start = displayEntries.get(barIndex).startTime.atZoneSameInstant(ZoneId.systemDefault());
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

    private List<List<PriceFetcher.PriceEntry>> buildDisplayedBucketEntries(List<PriceFetcher.PriceEntry> buckets,
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

    private void showChartTooltip(MotionEvent event) {
        if (displayedBarEntries.isEmpty()) {
            return;
        }

        int width = chartTouchOverlay.getWidth();
        if (width <= 0) {
            return;
        }

        float clampedX = Math.max(0f, Math.min(event.getX(), width - 1f));
        int bucketIndex = Math.min(
                displayedBarEntries.size() - 1,
                Math.max(0, (int) ((clampedX / (float) width) * displayedBarEntries.size()))
        );
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
        Log.d(TAG, "renderBarChart mode=" + chartMode
                + " displayedBuckets=" + displayedBarEntries.size()
                + " graphEntries=" + graphDisplayEntries.size());
        Log.d(TAG, PriceFetcher.describeEntriesForLog("renderBarChart allData", allData));
        Log.d(TAG, PriceFetcher.describeEntriesForLog("renderBarChart hourlyData", hourlyData));
        Log.d(TAG, "renderBarChart graphWindow="
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
        return String.format("%02d:%02d-%02d:%02d", start.getHour(), start.getMinute(), end.getHour(), end.getMinute());
    }

    private void showTooltipPopup(String text, float rawX, float rawY) {
        if (text == null || text.isEmpty()) {
            dismissChartTooltip();
            return;
        }

        dismissChartTooltip();

        TextView tooltipView = new TextView(this);
        tooltipView.setText(text);
        tooltipView.setTextColor(MaterialColors.getColor(tooltipView, com.google.android.material.R.attr.colorOnSurface));
        tooltipView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
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
        FrameLayout tooltipContainer = new FrameLayout(this);
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

        View root = getWindow().getDecorView();
        int popupWidth = tooltipContainer.getMeasuredWidth();
        int popupHeight = tooltipContainer.getMeasuredHeight();
        int margin = dpToPx(4);
        int x = Math.round(rawX - (popupWidth / 2f));
        x = Math.max(margin, Math.min(x, root.getWidth() - popupWidth - margin));
        int y = Math.round(rawY - popupHeight - dpToPx(TOOLTIP_VERTICAL_OFFSET_DP));
        y = Math.max(margin, y);

        chartTooltipPopup.showAtLocation(root, Gravity.NO_GRAVITY, x, y);
    }

    private void dismissChartTooltip() {
        if (chartTooltipPopup != null) {
            chartTooltipPopup.dismiss();
            chartTooltipPopup = null;
        }
    }

    private int dpToPx(int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                getResources().getDisplayMetrics()
        ));
    }

    private int getMainChartMode() {
        int storedMode = sharedPreferences.getInt(KEY_MAIN_ACTIVITY_CHART_MODE, MAIN_CHART_MODE_BARS);
        if (storedMode == MAIN_CHART_MODE_GRAPH || storedMode == MAIN_CHART_MODE_LINES) {
            return storedMode;
        }
        return MAIN_CHART_MODE_BARS;
    }

    private int getMainChartModeButtonId(int chartMode) {
        if (chartMode == MAIN_CHART_MODE_GRAPH) {
            return R.id.main_chart_graph_button;
        }
        if (chartMode == MAIN_CHART_MODE_LINES) {
            return R.id.main_chart_lines_button;
        }
        return R.id.main_chart_bars_button;
    }

    private int getMainChartModeForButton(int buttonId) {
        if (buttonId == R.id.main_chart_graph_button) {
            return MAIN_CHART_MODE_GRAPH;
        }
        if (buttonId == R.id.main_chart_lines_button) {
            return MAIN_CHART_MODE_LINES;
        }
        return MAIN_CHART_MODE_BARS;
    }

    private int getMainBarPoolMode() {
        int storedMode = sharedPreferences.getInt(KEY_MAIN_ACTIVITY_BAR_POOL_MODE, WidgetPreferences.POOL_MODE_AVERAGE);
        if (storedMode == WidgetPreferences.POOL_MODE_MIN || storedMode == WidgetPreferences.POOL_MODE_MAX) {
            return storedMode;
        }
        return WidgetPreferences.POOL_MODE_AVERAGE;
    }

    private int getMainBarPoolButtonId(int poolMode) {
        if (poolMode == WidgetPreferences.POOL_MODE_MIN) {
            return R.id.main_bar_pool_min_button;
        }
        if (poolMode == WidgetPreferences.POOL_MODE_MAX) {
            return R.id.main_bar_pool_max_button;
        }
        return R.id.main_bar_pool_average_button;
    }

    private int getMainBarPoolModeForButton(int buttonId) {
        if (buttonId == R.id.main_bar_pool_min_button) {
            return WidgetPreferences.POOL_MODE_MIN;
        }
        if (buttonId == R.id.main_bar_pool_max_button) {
            return WidgetPreferences.POOL_MODE_MAX;
        }
        return WidgetPreferences.POOL_MODE_AVERAGE;
    }

    private void renderAverageSummaries(List<PriceFetcher.PriceEntry> hourlyData) {
        String country = sharedPreferences.getString(PriceUpdateJobService.KEY_SELECTED_COUNTRY, "NO");
        ZoneId zoneId = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zoneId);
        LocalDate tomorrow = today.plusDays(1);

        double todayTotal = 0.0;
        int todayCount = 0;
        double tomorrowTotal = 0.0;
        int tomorrowCount = 0;

        for (PriceFetcher.PriceEntry entry : hourlyData) {
            if (entry.startTime == null) {
                continue;
            }
            LocalDate entryDate = entry.startTime.atZoneSameInstant(zoneId).toLocalDate();
            if (today.equals(entryDate)) {
                todayTotal += entry.pricePerKwh;
                todayCount++;
            } else if (tomorrow.equals(entryDate)) {
                tomorrowTotal += entry.pricePerKwh;
                tomorrowCount++;
            }
        }

        if (todayCount == 0) {
            todayAverageValue.setText(R.string.today_average_unavailable);
        } else {
            String averageText = getString(
                    R.string.today_average_format,
                    PriceDisplayUtils.formatPrice(todayTotal / todayCount, country, sharedPreferences),
                    PriceDisplayUtils.getUnitText(country, sharedPreferences)
            );
            todayAverageValue.setText(averageText);
        }

        if (tomorrowCount == 0) {
            tomorrowAverageValue.setText(R.string.tomorrow_average_pending);
        } else {
            String tomorrowText = getString(
                    R.string.tomorrow_average_format,
                    PriceDisplayUtils.formatPrice(tomorrowTotal / tomorrowCount, country, sharedPreferences),
                    PriceDisplayUtils.getUnitText(country, sharedPreferences)
            );
            tomorrowAverageValue.setText(tomorrowText);
        }
    }

    private void animateBars(int[] targetHeightsPx, int visibleBarCount) {
        cancelBarAnimation();

        List<Animator> animators = new ArrayList<>();
        LinearOutSlowInInterpolator interpolator = new LinearOutSlowInInterpolator();

        for (int i = 0; i < BAR_IDS.length; i++) {
            ImageView bar = findViewById(BAR_IDS[i]);
            if (i >= visibleBarCount) {
                setBarHeight(bar, 0);
                continue;
            }

            setBarHeight(bar, 0);
            ValueAnimator animator = ValueAnimator.ofInt(0, targetHeightsPx[i]);
            animator.setDuration(BAR_ANIMATION_DURATION_MS);
            animator.setStartDelay(i * BAR_ANIMATION_STAGGER_MS);
            animator.setInterpolator(interpolator);
            animator.addUpdateListener(valueAnimator -> setBarHeight(bar, (int) valueAnimator.getAnimatedValue()));
            animators.add(animator);
        }

        barAnimator = new AnimatorSet();
        barAnimator.playTogether(animators);
        barAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationCancel(Animator animation) {
                applyBarHeights(targetHeightsPx);
                barAnimator = null;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                applyBarHeights(targetHeightsPx);
                barAnimator = null;
            }
        });
        barAnimator.start();
    }

    private void applyBarHeights(int[] targetHeightsPx) {
        for (int i = 0; i < BAR_IDS.length; i++) {
            ImageView bar = findViewById(BAR_IDS[i]);
            setBarHeight(bar, targetHeightsPx[i]);
        }
    }

    private void setBarHeight(ImageView bar, int heightPx) {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) bar.getLayoutParams();
        if (params.height != heightPx) {
            params.height = heightPx;
            bar.setLayoutParams(params);
        }
    }

    private void cancelBarAnimation() {
        if (barAnimator != null) {
            barAnimator.cancel();
            barAnimator = null;
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

    private void setupInfoDialogs() {
        findViewById(R.id.stromstotte_info_trigger).setOnClickListener(v -> showInfoDialog(
                R.string.stromstotte_info_title,
                getString(R.string.stromstotte_info_message)
        ));
        findViewById(R.id.vat_info_trigger).setOnClickListener(v -> {
            String countryCode = getSelectedCountryCode();
            showInfoDialog(
                    R.string.vat_info_title,
                    getString(R.string.vat_info_message, formatVatPercent(RegionConfig.getVatPercent(countryCode)))
            );
        });
        findViewById(R.id.grid_fee_info_trigger).setOnClickListener(v -> {
            String countryCode = getSelectedCountryCode();
            showInfoDialog(
                    R.string.grid_fee_info_title,
                    getString(
                            R.string.grid_fee_info_message,
                            PriceDisplayUtils.getUnitText(countryCode, sharedPreferences)
                    )
            );
        });
    }

    private void setupAboutButton() {
        findViewById(R.id.about_button).setOnClickListener(v ->
                new AboutDialogFragment().show(getSupportFragmentManager(), "about_dialog"));
    }

    private void showInfoDialog(int titleResId, String message) {
        InfoDialogFragment.newInstance(getString(titleResId), message)
                .show(getSupportFragmentManager(), "info_dialog");
    }

    private String getSelectedCountryCode() {
        return sharedPreferences.getString(PriceUpdateJobService.KEY_SELECTED_COUNTRY, "NO");
    }

    private String formatVatPercent(double vatPercent) {
        NumberFormat numberFormat = NumberFormat.getNumberInstance();
        numberFormat.setGroupingUsed(false);
        numberFormat.setMinimumFractionDigits(0);
        numberFormat.setMaximumFractionDigits(1);
        return numberFormat.format(vatPercent);
    }

    private void updateVatLabel(TextView label) {
        label.setText(R.string.vat_label);
    }

    private void updateGridFeeUnit(TextInputLayout layout, String countryCode) {
        layout.setSuffixText(PriceDisplayUtils.getUnitText(countryCode, sharedPreferences));
    }


    private void updatePriceDisplayVisibility(String countryCode) {
        boolean showPriceDisplaySelector = PriceDisplayUtils.supportsDisplayStyleSelection(countryCode);
        priceDisplayContainer.setVisibility(showPriceDisplaySelector ? View.VISIBLE : View.GONE);
        if (showPriceDisplaySelector) {
            swissPriceUnitToggleGroup.check(getSwissPriceUnitButtonId());
        }
    }

    private int getSwissPriceUnitButtonId() {
        return PriceDisplayUtils.DISPLAY_STYLE_SWISS_CENTIMES.equals(
                PriceDisplayUtils.getSwissDisplayStyle(sharedPreferences)
        ) ? R.id.swiss_unit_ct_button : R.id.swiss_unit_rp_button;
    }
    private void updateAreaDropdown(AutoCompleteTextView targetAreaDropdown,
                                    SharedPreferences prefs,
                                    RegionConfig.Country country) {
        List<RegionConfig.Area> areas = country.getAreas();
        List<String> labels = new ArrayList<>(areas.size());
        for (RegionConfig.Area area : areas) {
            labels.add(area.getLabel());
        }
        ArrayAdapter<String> areaAdapter = createDropdownAdapter(labels);
        targetAreaDropdown.setAdapter(areaAdapter);

        String defaultAreaCode = areas.isEmpty() ? null : areas.get(0).getCode();
        String selectedArea = prefs.getString(PriceUpdateJobService.KEY_SELECTED_AREA, defaultAreaCode);
        RegionConfig.Area areaToDisplay = null;
        for (RegionConfig.Area area : areas) {
            if (area.getCode().equals(selectedArea)) {
                areaToDisplay = area;
                break;
            }
        }
        if (areaToDisplay == null && !areas.isEmpty()) {
            areaToDisplay = areas.get(0);
            prefs.edit().putString(PriceUpdateJobService.KEY_SELECTED_AREA, areaToDisplay.getCode()).apply();
        }
        if (areaToDisplay != null) {
            targetAreaDropdown.setText(areaToDisplay.getLabel(), false);
        } else {
            targetAreaDropdown.setText("", false);
        }
    }

    private void updateRegionVisibility(LinearLayout targetRegionContainer,
                                        AutoCompleteTextView targetAreaDropdown,
                                        RegionConfig.Country country) {
        boolean showRegion = country.hasMultipleAreas();
        targetRegionContainer.setVisibility(showRegion ? View.VISIBLE : View.GONE);
        targetAreaDropdown.setEnabled(showRegion);
        targetAreaDropdown.setFocusable(showRegion);
        targetAreaDropdown.setFocusableInTouchMode(showRegion);
    }

    private ArrayAdapter<String> createDropdownAdapter(List<String> items) {
        return new ArrayAdapter<String>(this, R.layout.spinner_dropdown_item, new ArrayList<>(items)) {
            private final List<String> allItems = new ArrayList<>(items);
            private final Filter unfilteredResults = new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    FilterResults results = new FilterResults();
                    results.values = new ArrayList<>(allItems);
                    results.count = allItems.size();
                    return results;
                }

                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    clear();
                    if (results.values instanceof List<?>) {
                        for (Object value : (List<?>) results.values) {
                            if (value instanceof String) {
                                add((String) value);
                            }
                        }
                    }
                    notifyDataSetChanged();
                }

                @Override
                public CharSequence convertResultToString(Object resultValue) {
                    return resultValue instanceof CharSequence ? (CharSequence) resultValue : super.convertResultToString(resultValue);
                }
            };

            @Override
            public Filter getFilter() {
                return unfilteredResults;
            }
        };
    }

    private RegionConfig.Country findCountryByLabel(String label) {
        for (RegionConfig.Country country : countries) {
            if (country.getDisplayName().equals(label)) {
                return country;
            }
        }
        return null;
    }

    private RegionConfig.Area findAreaByLabel(List<RegionConfig.Area> areas, String label) {
        for (RegionConfig.Area area : areas) {
            if (area.getLabel().equals(label)) {
                return area;
            }
        }
        return null;
    }
}
