package io.github.simonhalvdansson.flux;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.SharedPreferences;
import android.graphics.Outline;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator;

import com.google.android.material.button.MaterialButtonToggleGroup;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

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

    private final List<RegionConfig.Country> countries = RegionConfig.getCountries();

    private TextView currentPriceLabel;
    private TextView currentPriceValue;
    private TextView todayAverageValue;
    private View chartContainer;
    private SharedPreferences sharedPreferences;
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener;
    private AnimatorSet barAnimator;
    private boolean shouldAnimateBars;
    private int currentCountryIndex = 0;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        sharedPreferences = PriceRepository.getPreferences(this);
        ImageView appIconView = findViewById(R.id.app_icon);
        currentPriceLabel = findViewById(R.id.current_price_label);
        currentPriceValue = findViewById(R.id.current_price_value);
        todayAverageValue = findViewById(R.id.today_average_value);
        chartContainer = findViewById(R.id.bar_chart_section);
        shouldAnimateBars = savedInstanceState == null
                && !getIntent().getBooleanExtra(EXTRA_DISABLE_CHART_ANIMATION, false);

        setupAppSettings();
        configureAppIconShadow(appIconView);
        configureBarShadows();
        applyWindowInsets();

        preferenceChangeListener = (prefs, key) -> {
            if (PriceRepository.KEY_JSON_DATA.equals(key)
                    || PriceUpdateJobService.KEY_API_ERROR.equals(key)
                    || PriceUpdateJobService.KEY_SELECTED_COUNTRY.equals(key)
                    || PriceUpdateJobService.KEY_SELECTED_AREA.equals(key)
                    || PriceUpdateJobService.KEY_APPLY_VAT.equals(key)
                    || PriceUpdateJobService.KEY_APPLY_STROMSTOTTE.equals(key)
                    || PriceUpdateJobService.KEY_GRID_FEE.equals(key)
                    || PriceUpdateJobService.KEY_PRICE_DISPLAY_STYLE.equals(key)) {
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
    }

    @Override
    protected void onStop() {
        cancelBarAnimation();
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
        swissPriceUnitToggleGroup.setSelectionRequired(true);

        List<String> countryNames = new ArrayList<>();
        for (RegionConfig.Country country : countries) {
            countryNames.add(country.getDisplayName());
        }
        ArrayAdapter<String> countryAdapter = new ArrayAdapter<>(this, R.layout.spinner_dropdown_item, countryNames);
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

        String savedGridFee = sharedPreferences.getString(PriceUpdateJobService.KEY_GRID_FEE, "");
        if (savedGridFee == null || savedGridFee.trim().isEmpty()) {
            savedGridFee = "0";
            sharedPreferences.edit().putString(PriceUpdateJobService.KEY_GRID_FEE, savedGridFee).apply();
        }
        gridFeeInput.setText(savedGridFee);

        countryDropdown.setOnItemClickListener((parent, view, position, id) -> {
            currentCountryIndex = position;
            RegionConfig.Country selected = countries.get(position);
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
            if (position < 0 || position >= areas.size()) {
                return;
            }
            String area = areas.get(position).getCode();
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
    }

    private void updateWidgets() {
        MainWidget.updateAllWidgets(this);
        ListWidget.updateAllWidgets(this);
    }

    private void applyWindowInsets() {
        View root = findViewById(R.id.main_container);
        int padStart = root.getPaddingStart();
        int padTop = root.getPaddingTop();
        int padEnd = root.getPaddingEnd();
        int padBottom = root.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets safeArea = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
            );
            root.setPaddingRelative(
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
                "%02d:%02d-%02d:%02d",
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
            currentPriceValue.setText(snapshot.getDisplayPrice());
        } else if (snapshot.apiError) {
            currentPriceValue.setText(R.string.current_price_unavailable);
        } else {
            currentPriceValue.setText(R.string.current_price_loading);
        }

        renderBarChart();
    }

    private void renderBarChart() {
        List<PriceFetcher.PriceEntry> allData = CurrentPriceResolver.getAdjustedEntries(sharedPreferences);
        if (allData.isEmpty()) {
            cancelBarAnimation();
            chartContainer.setVisibility(View.GONE);
            todayAverageValue.setText(R.string.today_average_unavailable);
            return;
        }

        List<PriceFetcher.PriceEntry> hourlyData = PriceFetcher.aggregateToHourly(allData);
        if (hourlyData.isEmpty()) {
            cancelBarAnimation();
            chartContainer.setVisibility(View.GONE);
            todayAverageValue.setText(R.string.today_average_unavailable);
            return;
        }

        chartContainer.setVisibility(View.VISIBLE);

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

        List<PriceFetcher.PriceEntry> displayEntries = hourlyData.subList(firstHourIndex, lastHourIndex + 1);
        double maxPrice = 0.0;
        for (PriceFetcher.PriceEntry entry : displayEntries) {
            maxPrice = Math.max(maxPrice, entry.pricePerKwh);
        }
        if (maxPrice <= 0.0) {
            maxPrice = 1.0;
        }

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

        if (shouldAnimateBars) {
            shouldAnimateBars = false;
            chartContainer.post(() -> animateBars(targetHeightsPx, displayEntries.size()));
        } else {
            cancelBarAnimation();
            applyBarHeights(targetHeightsPx);
        }

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

        renderTodayAverage(hourlyData);
    }

    private void renderTodayAverage(List<PriceFetcher.PriceEntry> hourlyData) {
        String country = sharedPreferences.getString(PriceUpdateJobService.KEY_SELECTED_COUNTRY, "NO");
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        double total = 0.0;
        int count = 0;

        for (PriceFetcher.PriceEntry entry : hourlyData) {
            if (entry.startTime != null && today.equals(entry.startTime.atZoneSameInstant(ZoneId.systemDefault()).toLocalDate())) {
                total += entry.pricePerKwh;
                count++;
            }
        }

        if (count == 0) {
            todayAverageValue.setText(R.string.today_average_unavailable);
            return;
        }

        String averageText = getString(
                R.string.today_average_format,
                PriceDisplayUtils.formatPrice(total / count, country, sharedPreferences),
                PriceDisplayUtils.getUnitText(country, sharedPreferences)
        );
        todayAverageValue.setText(averageText);
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
        ArrayAdapter<String> areaAdapter = new ArrayAdapter<>(this, R.layout.spinner_dropdown_item, labels);
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
}
