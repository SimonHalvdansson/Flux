package io.github.simonhalvdansson.flux;

import android.content.SharedPreferences;
import android.graphics.Rect;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Filter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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

import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String STATE_SETTINGS_EXPANDED = "state_settings_expanded";

    public static final String EXTRA_DISABLE_CHART_ANIMATION =
            "io.github.simonhalvdansson.flux.extra.DISABLE_CHART_ANIMATION";

    private static final long QUARTER_REFRESH_SLOP_MS = 250L;

    private final List<RegionConfig.Country> countries = RegionConfig.getCountries();

    private TextView currentPriceLabel;
    private TextView currentPriceValue;
    private TextView currentPriceUnit;
    private View currentPriceInfoTrigger;
    private FrameLayout activityRoot;
    private View yesterdayAverageCard;
    private TextView yesterdayAverageValue;
    private TextView yesterdayAverageUnit;
    private TextView yesterdayAverageDate;
    private TextView yesterdayAverageMin;
    private TextView yesterdayAverageMax;
    private View todayAverageCard;
    private TextView todayAverageValue;
    private TextView todayAverageUnit;
    private TextView todayAverageDate;
    private TextView todayAverageMin;
    private TextView todayAverageMax;
    private View tomorrowAverageCard;
    private TextView tomorrowAverageValue;
    private TextView tomorrowAverageUnit;
    private TextView tomorrowAverageDate;
    private TextView tomorrowAverageMin;
    private TextView tomorrowAverageMax;
    private View settingsToggleRow;
    private ImageView settingsToggleCaret;
    private View settingsExpandableContainer;
    private ScrollView mainScrollView;
    private SharedPreferences sharedPreferences;
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener;
    private MainChartController mainChartController;
    private AverageDetailsController averageDetailsController;
    private final Handler quarterRefreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable quarterRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            renderCurrentPrice();
            scheduleQuarterBoundaryRefresh();
        }
    };
    private int currentCountryIndex = 0;
    private int currentImeInsetBottom = 0;

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
        PriceRepository.getSelectedCountryCode(this, sharedPreferences);
        activityRoot = findViewById(R.id.activity_root);
        ImageView appIconView = findViewById(R.id.app_icon);
        currentPriceLabel = findViewById(R.id.current_price_label);
        currentPriceValue = findViewById(R.id.current_price_value);
        currentPriceUnit = findViewById(R.id.current_price_unit);
        currentPriceInfoTrigger = findViewById(R.id.current_price_info_trigger);
        yesterdayAverageCard = findViewById(R.id.yesterday_average_card);
        yesterdayAverageValue = findViewById(R.id.yesterday_average_value);
        yesterdayAverageUnit = findViewById(R.id.yesterday_average_unit);
        yesterdayAverageDate = findViewById(R.id.yesterday_average_date);
        yesterdayAverageMin = findViewById(R.id.yesterday_average_min);
        yesterdayAverageMax = findViewById(R.id.yesterday_average_max);
        todayAverageCard = findViewById(R.id.today_average_card);
        todayAverageValue = findViewById(R.id.today_average_value);
        todayAverageUnit = findViewById(R.id.today_average_unit);
        todayAverageDate = findViewById(R.id.today_average_date);
        todayAverageMin = findViewById(R.id.today_average_min);
        todayAverageMax = findViewById(R.id.today_average_max);
        tomorrowAverageCard = findViewById(R.id.tomorrow_average_card);
        tomorrowAverageValue = findViewById(R.id.tomorrow_average_value);
        tomorrowAverageUnit = findViewById(R.id.tomorrow_average_unit);
        tomorrowAverageDate = findViewById(R.id.tomorrow_average_date);
        tomorrowAverageMin = findViewById(R.id.tomorrow_average_min);
        tomorrowAverageMax = findViewById(R.id.tomorrow_average_max);
        configureAveragePreviewIcons();
        mainScrollView = findViewById(R.id.main_container);
        settingsToggleRow = findViewById(R.id.settings_toggle_row);
        settingsToggleCaret = findViewById(R.id.settings_toggle_caret);
        settingsExpandableContainer = findViewById(R.id.settings_expandable_container);
        boolean shouldAnimateInitialChart = savedInstanceState == null
                && !getIntent().getBooleanExtra(EXTRA_DISABLE_CHART_ANIMATION, false);
        boolean restoreSettingsExpanded = savedInstanceState != null
                && savedInstanceState.getBoolean(STATE_SETTINGS_EXPANDED, false);
        mainChartController = new MainChartController(
                this,
                sharedPreferences,
                shouldAnimateInitialChart,
                this::renderCurrentPrice
        );
        averageDetailsController = new AverageDetailsController(
                this,
                activityRoot,
                sharedPreferences,
                yesterdayAverageCard,
                todayAverageCard,
                tomorrowAverageCard,
                savedInstanceState
        );

        setupAppSettings();
        mainChartController.setup();
        averageDetailsController.setupCardDialogs();
        setupSettingsToggle(restoreSettingsExpanded);
        setupCurrentPriceInfoTrigger();
        configureAppIconShadow(appIconView);
        applyWindowInsets();
        setupAboutDialogTrigger();
        getOnBackPressedDispatcher().addCallback(this, averageDetailsController.getBackCallback());

        preferenceChangeListener = (prefs, key) -> {
            if (mainChartController.consumePreferenceChange(key)) {
                return;
            }
            if (PriceRepository.KEY_JSON_DATA.equals(key)
                    || PriceUpdateJobService.KEY_API_ERROR.equals(key)
                    || PriceUpdateJobService.KEY_SELECTED_COUNTRY.equals(key)
                    || PriceUpdateJobService.KEY_SELECTED_AREA.equals(key)
                    || PriceUpdateJobService.KEY_APPLY_VAT.equals(key)
                    || PriceUpdateJobService.KEY_APPLY_STROMSTOTTE.equals(key)
                    || GridFeePreferences.KEY_GRID_FEE.equals(key)
                    || (key != null && key.startsWith(GridFeePreferences.KEY_GRID_FEE_PREFIX))
                    || PriceUpdateJobService.KEY_PRICE_DISPLAY_STYLE.equals(key)
                    || MainChartController.isRenderPreferenceKey(key)) {
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
        mainChartController.onStop();
        cancelQuarterBoundaryRefresh();
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(
                STATE_SETTINGS_EXPANDED,
                settingsExpandableContainer != null
                        && settingsExpandableContainer.getVisibility() == View.VISIBLE
        );
        averageDetailsController.onSaveInstanceState(outState);
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
        ArrayAdapter<String> countryAdapter = createDropdownAdapter(countryNames);
        countryDropdown.setAdapter(countryAdapter);
        stabilizeDropdownWidth(countryDropdown, countryNames);

        String selectedCountry = getSelectedCountryCode();
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
        updateSettingRowVisibility(stromstotteContainer, isNorway);
        stromstotteSwitch.setChecked(isNorway && applyStromstotte);
        if (!isNorway && applyStromstotte) {
            sharedPreferences.edit().putBoolean(PriceUpdateJobService.KEY_APPLY_STROMSTOTTE, false).apply();
        }

        updateRegionVisibility(regionContainer, areaDropdown, currentCountry);
        updatePriceDisplayVisibility(currentCountry.getCode());
        vatSwitch.setChecked(sharedPreferences.getBoolean(PriceUpdateJobService.KEY_APPLY_VAT, true));
        updateVatLabel(vatLabel);
        updateGridFeeUnit(gridFeeContainer, currentCountry.getCode());
        setupInfoDialogs();

        gridFeeInput.setText(GridFeePreferences.getSavedGridFee(sharedPreferences, currentCountry.getCode()));

        countryDropdown.setOnItemClickListener((parent, view, position, id) -> {
            String selectedLabel = (String) parent.getItemAtPosition(position);
            RegionConfig.Country selected = findCountryByLabel(selectedLabel);
            if (selected == null) {
                return;
            }
            String countryCode = selected.getCode();
            String currentCountryCode = getSelectedCountryCode();
            if (countryCode.equals(currentCountryCode)) {
                return;
            }

            currentCountryIndex = countries.indexOf(selected);
            PriceRepository.invalidateCachedPrices(
                    sharedPreferences.edit()
                            .putString(PriceUpdateJobService.KEY_SELECTED_COUNTRY, countryCode)
            ).apply();

            updateAreaDropdown(areaDropdown, sharedPreferences, selected);

            boolean norway = "NO".equals(countryCode);
            updateSettingRowVisibility(stromstotteContainer, norway);
            if (!norway) {
                stromstotteSwitch.setChecked(false);
                sharedPreferences.edit().putBoolean(PriceUpdateJobService.KEY_APPLY_STROMSTOTTE, false).apply();
            }
            updateRegionVisibility(regionContainer, areaDropdown, selected);
            updatePriceDisplayVisibility(countryCode);
            updateVatLabel(vatLabel);
            updateGridFeeUnit(gridFeeContainer, countryCode);
            gridFeeInput.setText(GridFeePreferences.getSavedGridFee(sharedPreferences, countryCode));

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
            String currentArea = sharedPreferences.getString(
                    PriceUpdateJobService.KEY_SELECTED_AREA,
                    areas.isEmpty() ? null : areas.get(0).getCode()
            );
            if (area.equals(currentArea)) {
                return;
            }

            PriceRepository.invalidateCachedPrices(
                    sharedPreferences.edit()
                            .putString(PriceUpdateJobService.KEY_SELECTED_AREA, area)
            ).apply();
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
            updateGridFeeUnit(gridFeeContainer, getSelectedCountryCode());
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
                String countryCode = sharedPreferences.getString(
                        PriceUpdateJobService.KEY_SELECTED_COUNTRY,
                        "NO"
                );
                sharedPreferences.edit()
                        .putString(
                                GridFeePreferences.getPreferenceKey(countryCode),
                                s == null ? "" : s.toString()
                        )
                        .apply();
                updateWidgets();
            }
        });

        gridFeeInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                requestFocusedFieldVisibility();
                return;
            }
            CharSequence value = gridFeeInput.getText();
            if (value == null || value.toString().trim().isEmpty()) {
                gridFeeInput.setText("0");
            }
        });

    }

    private void setupSettingsToggle(boolean expanded) {
        setSettingsExpanded(expanded, false);
        settingsToggleRow.setOnClickListener(v ->
                setSettingsExpanded(settingsExpandableContainer.getVisibility() != View.VISIBLE, true));
    }

    private void setSettingsExpanded(boolean expanded, boolean animateCaret) {
        settingsExpandableContainer.setVisibility(expanded ? View.VISIBLE : View.GONE);
        float targetRotation = expanded ? 180f : 0f;
        settingsToggleCaret.animate().cancel();
        if (animateCaret) {
            settingsToggleCaret.animate()
                    .rotation(targetRotation)
                    .setDuration(220L)
                    .setInterpolator(new LinearOutSlowInInterpolator())
                    .start();
        } else {
            settingsToggleCaret.setRotation(targetRotation);
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

    private void updateWidgets() {
        MainWidget.updateAllWidgets(this);
        ListWidget.updateAllWidgets(this);
    }

    private void applyWindowInsets() {
        ScrollView root = mainScrollView;
        View content = findViewById(R.id.main_content);
        int padStart = content.getPaddingStart();
        int padTop = content.getPaddingTop();
        int padEnd = content.getPaddingEnd();
        int padBottom = content.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets safeArea = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
            );
            Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());
            currentImeInsetBottom = imeInsets.bottom;
            int bottomInset = Math.max(safeArea.bottom, imeInsets.bottom);
            content.setPaddingRelative(
                    padStart + safeArea.left,
                    padTop + safeArea.top,
                    padEnd + safeArea.right,
                    padBottom + bottomInset
            );
            if (imeInsets.bottom > 0) {
                requestFocusedFieldVisibility();
            }
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void requestFocusedFieldVisibility() {
        if (mainScrollView == null) {
            return;
        }
        mainScrollView.post(() -> scrollFocusedViewIntoViewport(mainScrollView));
        mainScrollView.postDelayed(() -> scrollFocusedViewIntoViewport(mainScrollView), 100L);
        mainScrollView.postDelayed(() -> scrollFocusedViewIntoViewport(mainScrollView), 220L);
    }

    private void scrollFocusedViewIntoViewport(ScrollView root) {
        View focusedView = getCurrentFocus();
        if (focusedView == null || !isViewDescendantOf(focusedView, root)) {
            return;
        }
        int[] rootLocation = new int[2];
        int[] focusedLocation = new int[2];
        root.getLocationOnScreen(rootLocation);
        focusedView.getLocationOnScreen(focusedLocation);

        int topMargin = dpToPx(12);
        int bottomMargin = dpToPx(24);
        int visibleTop = rootLocation[1] + topMargin;
        int visibleBottom = rootLocation[1] + root.getHeight() - currentImeInsetBottom - bottomMargin;
        if (visibleBottom <= visibleTop) {
            return;
        }

        Rect focusedBounds = new Rect(
                focusedLocation[0],
                focusedLocation[1],
                focusedLocation[0] + focusedView.getWidth(),
                focusedLocation[1] + focusedView.getHeight()
        );
        if (focusedBounds.bottom > visibleBottom) {
            root.smoothScrollBy(0, focusedBounds.bottom - visibleBottom);
        } else if (focusedBounds.top < visibleTop) {
            root.smoothScrollBy(0, focusedBounds.top - visibleTop);
        }
    }

    private boolean isViewDescendantOf(View child, View ancestor) {
        View current = child;
        while (current != null) {
            if (current == ancestor) {
                return true;
            }
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return false;
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
        List<PriceFetcher.PriceEntry> allData = CurrentPriceResolver.getAdjustedEntries(this, sharedPreferences);
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
        CurrentPriceResolver.Snapshot snapshot = CurrentPriceResolver.resolve(this);
        if (snapshot.hasData) {
            updateCurrentPriceLabel();
            currentPriceValue.setText(snapshot.formattedPrice);
            if (snapshot.unitText != null && !snapshot.unitText.isEmpty()) {
                currentPriceUnit.setText(snapshot.unitText);
                currentPriceUnit.setVisibility(View.VISIBLE);
            } else {
                currentPriceUnit.setText("");
                currentPriceUnit.setVisibility(View.GONE);
            }
            currentPriceInfoTrigger.setEnabled(true);
            renderPriceVisuals();
            return;
        }

        currentPriceUnit.setText("");
        currentPriceUnit.setVisibility(View.GONE);
        currentPriceInfoTrigger.setEnabled(false);

        if (snapshot.apiError) {
            updateCurrentPriceLabel();
            currentPriceValue.setText(R.string.current_price_unavailable);
            renderPriceVisuals();
            return;
        }

        renderLoadingPlaceholders();
    }

    private void renderPriceVisuals() {
        List<PriceFetcher.PriceEntry> allData =
                CurrentPriceResolver.getAdjustedEntries(this, sharedPreferences);
        mainChartController.render(allData);
        renderAverageSummaries(allData);
    }

    private void renderLoadingPlaceholders() {
        currentPriceValue.setText(R.string.current_price_placeholder);
        updateAverageCardDates();
        yesterdayAverageValue.setText(R.string.current_price_placeholder);
        yesterdayAverageUnit.setText(R.string.average_loading);
        yesterdayAverageMin.setText(R.string.average_min_placeholder);
        yesterdayAverageMax.setText(R.string.average_max_placeholder);
        todayAverageValue.setText(R.string.current_price_placeholder);
        todayAverageUnit.setText(R.string.average_loading);
        todayAverageMin.setText(R.string.average_min_placeholder);
        todayAverageMax.setText(R.string.average_max_placeholder);
        tomorrowAverageValue.setText(R.string.current_price_placeholder);
        tomorrowAverageUnit.setText(R.string.average_loading);
        tomorrowAverageMin.setText(R.string.average_min_placeholder);
        tomorrowAverageMax.setText(R.string.average_max_placeholder);
        setAverageCardEnabled(yesterdayAverageCard, false);
        setAverageCardEnabled(todayAverageCard, false);
        setAverageCardEnabled(tomorrowAverageCard, false);
    }

    private int dpToPx(int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                getResources().getDisplayMetrics()
        ));
    }

    private void configureAveragePreviewIcons() {
        setAveragePreviewIcon(yesterdayAverageMin, R.drawable.ic_average_min_16);
        setAveragePreviewIcon(yesterdayAverageMax, R.drawable.ic_average_max_16);
        setAveragePreviewIcon(todayAverageMin, R.drawable.ic_average_min_16);
        setAveragePreviewIcon(todayAverageMax, R.drawable.ic_average_max_16);
        setAveragePreviewIcon(tomorrowAverageMin, R.drawable.ic_average_min_16);
        setAveragePreviewIcon(tomorrowAverageMax, R.drawable.ic_average_max_16);
    }

    private void setAveragePreviewIcon(TextView textView, int drawableResId) {
        Drawable drawable = getDrawable(drawableResId);
        if (drawable == null) {
            return;
        }

        int iconSize = dpToPx(15);
        drawable.setBounds(0, 0, iconSize, iconSize);
        textView.setCompoundDrawablesRelative(drawable, null, null, null);
    }

    private void renderAverageSummaries(List<PriceFetcher.PriceEntry> entries) {
        String country = getSelectedCountryCode();
        List<AveragePriceSummaryResolver.DaySummary> daySummaries =
                AveragePriceSummaryResolver.resolveSurroundingDays(entries, country);
        AveragePriceSummaryResolver.DaySummary yesterday = daySummaries.get(0);
        AveragePriceSummaryResolver.DaySummary today = daySummaries.get(1);
        AveragePriceSummaryResolver.DaySummary tomorrow = daySummaries.get(2);
        updateAverageCardDates(yesterday.date, today.date, tomorrow.date);

        String unitText = PriceDisplayUtils.getUnitText(country, sharedPreferences);
        setAverageCard(
                yesterdayAverageValue,
                yesterdayAverageUnit,
                yesterdayAverageMin,
                yesterdayAverageMax,
                yesterday.summary,
                country,
                unitText,
                R.string.average_unavailable_short
        );
        setAverageCard(
                todayAverageValue,
                todayAverageUnit,
                todayAverageMin,
                todayAverageMax,
                today.summary,
                country,
                unitText,
                R.string.average_unavailable_short
        );
        setAverageCard(
                tomorrowAverageValue,
                tomorrowAverageUnit,
                tomorrowAverageMin,
                tomorrowAverageMax,
                tomorrow.summary,
                country,
                unitText,
                R.string.tomorrow_average_pending
        );
        setAverageCardEnabled(yesterdayAverageCard, yesterday.summary.hasData());
        setAverageCardEnabled(todayAverageCard, today.summary.hasData());
        setAverageCardEnabled(tomorrowAverageCard, tomorrow.summary.hasData());
        averageDetailsController.restorePendingDialogIfPossible();
    }

    private void updateAverageCardDates() {
        String country = getSelectedCountryCode();
        ZoneId zoneId = RegionConfig.getZoneId(country);
        if (zoneId == null) {
            zoneId = ZoneId.systemDefault();
        }
        LocalDate today = LocalDate.now(zoneId);
        updateAverageCardDates(today.minusDays(1), today, today.plusDays(1));
    }

    private void updateAverageCardDates(LocalDate yesterday, LocalDate today, LocalDate tomorrow) {
        yesterdayAverageDate.setText(String.valueOf(yesterday.getDayOfMonth()));
        todayAverageDate.setText(String.valueOf(today.getDayOfMonth()));
        tomorrowAverageDate.setText(String.valueOf(tomorrow.getDayOfMonth()));
    }

    private void setAverageCard(TextView valueView,
                                TextView captionView,
                                TextView minView,
                                TextView maxView,
                                AveragePriceSummaryResolver.Summary summary,
                                String country,
                                String unitText,
                                int unavailableCaptionResId) {
        if (!summary.hasData()) {
            valueView.setText(R.string.current_price_placeholder);
            captionView.setText(unavailableCaptionResId);
            minView.setText(R.string.average_min_placeholder);
            maxView.setText(R.string.average_max_placeholder);
            return;
        }

        valueView.setText(PriceDisplayUtils.formatPrice(summary.average(), country, sharedPreferences));
        captionView.setText(unitText);
        minView.setText(getString(
                R.string.average_min_format,
                PriceDisplayUtils.formatPrice(summary.minPrice(), country, sharedPreferences)
        ));
        maxView.setText(getString(
                R.string.average_max_format,
                PriceDisplayUtils.formatPrice(summary.maxPrice(), country, sharedPreferences)
        ));
    }

    private void setAverageCardEnabled(View card, boolean enabled) {
        if (card == null) {
            return;
        }
        card.setEnabled(enabled);
        card.setClickable(enabled);
        card.setFocusable(enabled);
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
            showInfoDialog(
                    R.string.grid_fee_info_title,
                    getString(R.string.grid_fee_info_message)
            );
        });
        findViewById(R.id.main_bar_pool_info_trigger).setOnClickListener(v -> {
            showInfoDialog(
                    R.string.main_bar_pool_info_title,
                    getString(R.string.main_bar_pool_info_message)
            );
        });
    }

    private void setupCurrentPriceInfoTrigger() {
        currentPriceInfoTrigger.setOnClickListener(v ->
                new CurrentPriceDetailsDialogFragment()
                        .show(getSupportFragmentManager(), "current_price_details_dialog"));
    }

    private void setupAboutDialogTrigger() {
        findViewById(R.id.app_icon_button).setOnClickListener(v ->
                new AboutDialogFragment().show(getSupportFragmentManager(), "about_dialog"));
    }

    private void showInfoDialog(int titleResId, String message) {
        InfoDialogFragment.newInstance(getString(titleResId), message)
                .show(getSupportFragmentManager(), "info_dialog");
    }

    private String getSelectedCountryCode() {
        return PriceRepository.getSelectedCountryCode(this, sharedPreferences);
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
        updateSettingRowVisibility(priceDisplayContainer, showPriceDisplaySelector);
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
        stabilizeDropdownWidth(targetAreaDropdown, labels);

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
        updateSettingRowVisibility(targetRegionContainer, showRegion);
        targetAreaDropdown.setEnabled(showRegion);
        targetAreaDropdown.setFocusable(showRegion);
        targetAreaDropdown.setFocusableInTouchMode(showRegion);
    }

    private void updateSettingRowVisibility(View container, boolean visible) {
        int targetVisibility = visible ? View.VISIBLE : View.GONE;
        if (container.getVisibility() != targetVisibility) {
            container.setVisibility(targetVisibility);
        }
        setViewEnabled(container, visible);
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

    private void stabilizeDropdownWidth(AutoCompleteTextView dropdown, List<String> items) {
        ViewParent parent = dropdown.getParent();
        if (!(parent instanceof TextInputLayout)) {
            return;
        }

        TextInputLayout inputLayout = (TextInputLayout) parent;
        float maxTextWidthPx = 0f;
        for (String item : items) {
            maxTextWidthPx = Math.max(maxTextWidthPx, dropdown.getPaint().measureText(item));
        }

        int desiredMinWidthPx = (int) Math.ceil(maxTextWidthPx)
                + dropdown.getCompoundPaddingLeft()
                + dropdown.getCompoundPaddingRight()
                + inputLayout.getPaddingLeft()
                + inputLayout.getPaddingRight()
                + dpToPx(56)
                + dpToPx(8);
        inputLayout.setMinWidth(desiredMinWidthPx);
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
