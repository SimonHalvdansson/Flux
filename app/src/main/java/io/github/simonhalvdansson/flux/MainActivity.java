package io.github.simonhalvdansson.flux;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.LayoutTransition;
import android.animation.ValueAnimator;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
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
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.transition.platform.MaterialArcMotion;
import com.google.android.material.transition.platform.MaterialContainerTransform;

import java.text.NumberFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String KEY_MAIN_ACTIVITY_CHART_MODE = "main_activity_chart_mode";
    private static final String KEY_MAIN_ACTIVITY_BAR_POOL_MODE = "main_activity_bar_pool_mode";
    private static final String KEY_MAIN_ACTIVITY_SHOW_Y_AXIS = "main_activity_show_y_axis";
    private static final String STATE_SETTINGS_EXPANDED = "state_settings_expanded";
    private static final String STATE_AVERAGE_DETAILS_DAY_OFFSET = "state_average_details_day_offset";
    private static final String STATE_AVERAGE_DETAILS_INCREMENT_MINUTES =
            "state_average_details_increment_minutes";
    private static final String STATE_AVERAGE_DETAILS_SCROLL_Y = "state_average_details_scroll_y";
    private static final int MAIN_CHART_MODE_BARS = 0;
    private static final int MAIN_CHART_MODE_GRAPH = 1;
    private static final int MAIN_CHART_MODE_LINES = 2;
    private static final int TOOLTIP_VERTICAL_OFFSET_DP = 6;
    private static final int TOOLTIP_HORIZONTAL_PADDING_DP = 12;
    private static final int TOOLTIP_VERTICAL_PADDING_DP = 10;
    private static final long CHART_MODE_TRANSITION_DURATION_MS = 100L;
    private static final long SECTION_VISIBILITY_ANIMATION_MS = 180L;
    private static final long AVERAGE_DETAILS_TRANSITION_DURATION_MS = 460L;
    private static final long AVERAGE_DETAILS_SCRIM_DURATION_MS = 180L;
    private static final int AVERAGE_DETAILS_MAX_WIDTH_DP = 520;
    private static final int AVERAGE_DETAILS_SIDE_MARGIN_DP = 24;
    private static final int AVERAGE_DETAILS_CONTAINER_TRANSFORM_ELEVATION_DP = 10;
    private static final long AVERAGE_DETAILS_ROW_HIGHLIGHT_DELAY_MS = 220L;
    private static final long AVERAGE_DETAILS_ROW_HIGHLIGHT_DURATION_MS = 700L;
    private static final int AVERAGE_DETAILS_ROW_HIGHLIGHT_ALPHA = 96;
    private static final DateTimeFormatter AVERAGE_DETAILS_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("MMMM d", Locale.ENGLISH);
    private static final int AVERAGE_DAY_YESTERDAY = -1;
    private static final int AVERAGE_DAY_TODAY = 0;
    private static final int AVERAGE_DAY_TOMORROW = 1;
    private static final int AVERAGE_DETAILS_NO_RESTORE = Integer.MIN_VALUE;

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
    private static final long BAR_UPDATE_ANIMATION_DURATION_MS = 160L;
    private static final long GRAPH_FADE_IN_DURATION_MS = 420L;
    private static final long QUARTER_REFRESH_SLOP_MS = 250L;
    private static final float[] CHART_Y_AXIS_TICK_FRACTIONS = {0.8f, 0.6f, 0.4f, 0.2f};
    private static final int CHART_Y_AXIS_EDGE_MARGIN_DP = 6;

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
    private View chartContainer;
    private View chartVisualContainer;
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
    private ScrollView mainScrollView;
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
    private boolean suppressNextBarHeightAnimation;
    private boolean suppressNextChartModePreferenceRender;
    private int chartModeTransitionId = 0;
    private int currentCountryIndex = 0;
    private int currentImeInsetBottom = 0;
    private List<PriceFetcher.PriceEntry> displayedBarEntries = new ArrayList<>();
    private List<List<PriceFetcher.PriceEntry>> displayedBucketEntries = new ArrayList<>();
    private List<PriceFetcher.PriceEntry> displayedGraphEntries = new ArrayList<>();
    private double displayedGraphMaxPrice = 1.0;
    private double displayedChartScaleMax = 1.0;
    private int selectedChartBucketIndex = -1;
    private float selectedChartFraction = Float.NaN;
    private boolean suppressNextTooltipDismissCallback;

    private AutoCompleteTextView countryDropdown;
    private AutoCompleteTextView areaDropdown;
    private LinearLayout regionContainer;
    private LinearLayout priceDisplayContainer;
    private LinearLayout stromstotteContainer;
    private MaterialSwitch stromstotteSwitch;
    private MaterialSwitch vatSwitch;
    private MaterialSwitch mainChartYAxisSwitch;
    private TextView vatLabel;
    private TextView chartYAxisTopValue;
    private TextView chartYAxisUpperMidValue;
    private TextView chartYAxisLowerMidValue;
    private TextView chartYAxisBottomValue;
    private TextInputLayout gridFeeContainer;
    private TextInputEditText gridFeeInput;
    private MaterialButtonToggleGroup swissPriceUnitToggleGroup;
    private View mainBarPoolContainer;
    private MaterialButtonToggleGroup mainBarPoolToggleGroup;
    private View averageDetailsOverlay;
    private View activeAverageDetailsSourceCard;
    private int activeAverageDetailsDayOffset = AVERAGE_DETAILS_NO_RESTORE;
    private int activeAverageDetailsIncrementMinutes = WidgetPreferences.INCREMENT_60_MINUTES;
    private int pendingAverageDetailsDayOffset = AVERAGE_DETAILS_NO_RESTORE;
    private int pendingAverageDetailsIncrementMinutes = WidgetPreferences.INCREMENT_60_MINUTES;
    private int pendingAverageDetailsScrollY;

    private final OnBackPressedCallback averageDetailsBackCallback = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            dismissAverageDetailsDialog(true);
        }
    };

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
        mainScrollView = findViewById(R.id.main_container);
        settingsToggleRow = findViewById(R.id.settings_toggle_row);
        settingsToggleCaret = findViewById(R.id.settings_toggle_caret);
        settingsExpandableContainer = findViewById(R.id.settings_expandable_container);
        chartContainer = findViewById(R.id.bar_chart_section);
        chartVisualContainer = findViewById(R.id.chart_visual_container);
        chartYAxisContainer = findViewById(R.id.chart_y_axis_container);
        chartYAxisGuides = findViewById(R.id.chart_y_axis_guides);
        chartYAxisSpacer = findViewById(R.id.chart_y_axis_spacer);
        chartYAxisTopGuide = findViewById(R.id.chart_y_axis_top_guide);
        chartYAxisUpperMidGuide = findViewById(R.id.chart_y_axis_upper_mid_guide);
        chartYAxisLowerMidGuide = findViewById(R.id.chart_y_axis_lower_mid_guide);
        chartYAxisBottomGuide = findViewById(R.id.chart_y_axis_bottom_guide);
        barChartContainer = findViewById(R.id.bar_chart_container);
        graphImageView = findViewById(R.id.graph_image);
        chartTouchOverlay = findViewById(R.id.chart_touch_overlay);
        mainChartToggleGroup = findViewById(R.id.main_chart_toggle_group);
        shouldAnimateInitialChart = savedInstanceState == null
                && !getIntent().getBooleanExtra(EXTRA_DISABLE_CHART_ANIMATION, false);
        boolean restoreSettingsExpanded = savedInstanceState != null
                && savedInstanceState.getBoolean(STATE_SETTINGS_EXPANDED, false);
        if (savedInstanceState != null
                && savedInstanceState.containsKey(STATE_AVERAGE_DETAILS_DAY_OFFSET)) {
            pendingAverageDetailsDayOffset = savedInstanceState.getInt(
                    STATE_AVERAGE_DETAILS_DAY_OFFSET,
                    AVERAGE_DETAILS_NO_RESTORE
            );
            pendingAverageDetailsIncrementMinutes = savedInstanceState.getInt(
                    STATE_AVERAGE_DETAILS_INCREMENT_MINUTES,
                    WidgetPreferences.INCREMENT_60_MINUTES
            );
            pendingAverageDetailsScrollY = savedInstanceState.getInt(
                    STATE_AVERAGE_DETAILS_SCROLL_Y,
                    0
            );
        }

        setupAppSettings();
        setupSettingsToggle(restoreSettingsExpanded);
        setupMainChartModeToggle();
        setupChartTouchOverlay();
        setupCurrentPriceInfoTrigger();
        setupAverageCardDialogs();
        configureAppIconShadow(appIconView);
        configureBarShadows();
        applyWindowInsets();
        setupAboutDialogTrigger();
        getOnBackPressedDispatcher().addCallback(this, averageDetailsBackCallback);

        preferenceChangeListener = (prefs, key) -> {
            if (KEY_MAIN_ACTIVITY_CHART_MODE.equals(key) && suppressNextChartModePreferenceRender) {
                suppressNextChartModePreferenceRender = false;
                return;
            }
            if (KEY_MAIN_ACTIVITY_CHART_MODE.equals(key)
                    && getMainChartMode() == MAIN_CHART_MODE_BARS) {
                suppressNextBarHeightAnimation = true;
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
        clearChartSelection(false);
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
        if (averageDetailsOverlay != null
                && activeAverageDetailsDayOffset != AVERAGE_DETAILS_NO_RESTORE) {
            outState.putInt(STATE_AVERAGE_DETAILS_DAY_OFFSET, activeAverageDetailsDayOffset);
            outState.putInt(
                    STATE_AVERAGE_DETAILS_INCREMENT_MINUTES,
                    activeAverageDetailsIncrementMinutes
            );
            ScrollView scrollView = averageDetailsOverlay.findViewById(
                    R.id.average_details_price_scroll
            );
            outState.putInt(
                    STATE_AVERAGE_DETAILS_SCROLL_Y,
                    scrollView != null ? scrollView.getScrollY() : 0
            );
        }
    }

    private void setupAppSettings() {
        countryDropdown = findViewById(R.id.country_dropdown);
        areaDropdown = findViewById(R.id.area_dropdown);
        regionContainer = findViewById(R.id.region_container);
        priceDisplayContainer = findViewById(R.id.price_display_container);
        stromstotteContainer = findViewById(R.id.stromstotte_container);
        stromstotteSwitch = findViewById(R.id.stromstotte_switch);
        vatSwitch = findViewById(R.id.vat_switch);
        mainChartYAxisSwitch = findViewById(R.id.main_chart_y_axis_switch);
        vatLabel = findViewById(R.id.vat_label);
        chartYAxisTopValue = findViewById(R.id.chart_y_axis_top_value);
        chartYAxisUpperMidValue = findViewById(R.id.chart_y_axis_upper_mid_value);
        chartYAxisLowerMidValue = findViewById(R.id.chart_y_axis_lower_mid_value);
        chartYAxisBottomValue = findViewById(R.id.chart_y_axis_bottom_value);
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
        mainChartYAxisSwitch.setChecked(isMainChartYAxisEnabled());
        updateVatLabel(vatLabel);
        updateGridFeeUnit(gridFeeContainer, currentCountry.getCode());
        updateChartYAxisVisibility(mainChartYAxisSwitch.isChecked());
        mainBarPoolToggleGroup.check(getMainBarPoolButtonId(getMainBarPoolMode()));
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

        mainChartYAxisSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sharedPreferences.edit()
                    .putBoolean(KEY_MAIN_ACTIVITY_SHOW_Y_AXIS, isChecked)
                    .apply();
            updateChartYAxisVisibility(isChecked);
            clearChartSelection(false);
            chartVisualContainer.post(this::renderCurrentPrice);
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

        mainBarPoolToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            sharedPreferences.edit()
                    .putInt(KEY_MAIN_ACTIVITY_BAR_POOL_MODE, getMainBarPoolModeForButton(checkedId))
                    .apply();
            clearChartSelection(false);
            renderCurrentPrice();
        });
    }

    private void setupMainChartModeToggle() {
        syncChartModeButtonIconTints();
        mainChartToggleGroup.setSelectionRequired(true);
        mainChartToggleGroup.check(getMainChartModeButtonId(getMainChartMode()));
        updateMainBarPoolVisibility(getMainChartMode(), false);
        mainChartToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            int chartMode = getMainChartModeForButton(checkedId);
            if (chartMode == getMainChartMode()) {
                return;
            }
            suppressNextChartModePreferenceRender = true;
            suppressNextBarHeightAnimation = chartMode == MAIN_CHART_MODE_BARS;
            sharedPreferences.edit()
                    .putInt(KEY_MAIN_ACTIVITY_CHART_MODE, chartMode)
                    .apply();
            updateMainBarPoolVisibility(chartMode, true);
            clearChartSelection(false);
            animateChartModeChange();
        });
    }

    private void syncChartModeButtonIconTints() {
        syncButtonIconTint(R.id.main_chart_bars_button);
        syncButtonIconTint(R.id.main_chart_graph_button);
        syncButtonIconTint(R.id.main_chart_lines_button);
    }

    private void syncButtonIconTint(int buttonId) {
        MaterialButton button = findViewById(buttonId);
        if (button == null) {
            return;
        }
        button.setIconTint(button.getTextColors());
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

    private void updateMainBarPoolVisibility(int chartMode, boolean animate) {
        updateSectionVisibility(mainBarPoolContainer, chartMode == MAIN_CHART_MODE_BARS, animate);
    }

    private void updateSectionVisibility(View container, boolean visible, boolean animate) {
        container.animate().cancel();
        container.clearAnimation();

        container.setAlpha(1f);
        container.setVisibility(visible ? View.VISIBLE : View.GONE);
        setViewEnabled(container, visible);
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

    private void animateChartModeChange() {
        chartModeTransitionId++;
        int transitionId = chartModeTransitionId;
        chartVisualContainer.animate().cancel();

        if (chartContainer.getVisibility() != View.VISIBLE) {
            chartVisualContainer.setAlpha(1f);
            renderCurrentPrice();
            return;
        }

        chartTouchOverlay.setEnabled(false);
        chartVisualContainer.animate()
                .alpha(0f)
                .setDuration(CHART_MODE_TRANSITION_DURATION_MS)
                .setInterpolator(new LinearOutSlowInInterpolator())
                .withEndAction(() -> {
                    if (transitionId != chartModeTransitionId) {
                        return;
                    }
                    renderCurrentPrice();
                    if (chartContainer.getVisibility() != View.VISIBLE) {
                        chartVisualContainer.setAlpha(1f);
                        return;
                    }
                    chartVisualContainer.setAlpha(0f);
                    chartVisualContainer.animate()
                            .alpha(1f)
                            .setDuration(CHART_MODE_TRANSITION_DURATION_MS)
                            .setInterpolator(new LinearOutSlowInInterpolator())
                            .withEndAction(() -> {
                                if (transitionId != chartModeTransitionId) {
                                    return;
                                }
                                chartVisualContainer.setAlpha(1f);
                            })
                            .start();
                })
                .start();
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
            renderBarChart();
            return;
        }

        currentPriceUnit.setText("");
        currentPriceUnit.setVisibility(View.GONE);
        currentPriceInfoTrigger.setEnabled(false);

        if (snapshot.apiError) {
            updateCurrentPriceLabel();
            currentPriceValue.setText(R.string.current_price_unavailable);
            renderBarChart();
            return;
        }

        renderLoadingPlaceholders();
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

    private void renderBarChart() {
        clearChartSelection(false);

        List<PriceFetcher.PriceEntry> allData = CurrentPriceResolver.getAdjustedEntries(this, sharedPreferences);
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
            renderAverageSummaries(allData);
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

        double graphMaxPrice = BarChartUtils.resolveScaleMax(displayedGraphEntries);
        displayedGraphMaxPrice = graphMaxPrice;
        double chartDataScaleMax = Math.max(barScaleMax, displayedGraphMaxPrice);
        double yAxisScaleMax = resolveRoundedChartScaleMax(chartDataScaleMax);
        displayedChartScaleMax = isMainChartYAxisEnabled() ? yAxisScaleMax : chartDataScaleMax;
        if (displayedChartScaleMax <= 0.0) {
            displayedChartScaleMax = 1.0;
        }
        if (yAxisScaleMax <= 0.0) {
            yAxisScaleMax = 1.0;
        }

        int chartMode = getMainChartMode();
        updateChartYAxis(yAxisScaleMax);
        if (chartMode == MAIN_CHART_MODE_BARS) {
            renderBars(displayedBarEntries, displayedChartScaleMax);
        } else {
            renderGraph(chartMode, displayedGraphEntries, displayedChartScaleMax);
        }

        logChartDiagnostics(allData, hourlyData, displayedGraphEntries, chartMode);
        updateTimeLabels(displayedBarEntries);
        renderAverageSummaries(allData);
    }

    private void clearChartData() {
        displayedBarEntries = new ArrayList<>();
        displayedBucketEntries = new ArrayList<>();
        displayedGraphEntries = new ArrayList<>();
        displayedGraphMaxPrice = 1.0;
        displayedChartScaleMax = 1.0;
        updateChartYAxis(1.0);
        chartTouchOverlay.setEnabled(false);
        cancelGraphAnimation();
        graphImageView.setImageDrawable(null);
        clearChartSelection(false);
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
            ImageView bar = findViewById(BAR_IDS[i]);
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
            applyBarState(targetHeightsPx, targetVisibilities);
        } else if (shouldAnimateInitialChart) {
            shouldAnimateInitialChart = false;
            applyBarState(new int[BAR_IDS.length], targetVisibilities);
            chartContainer.post(() -> animateBars(
                    new int[BAR_IDS.length],
                    targetHeightsPx,
                    targetVisibilities,
                    BAR_ANIMATION_DURATION_MS,
                    BAR_ANIMATION_STAGGER_MS
            ));
        } else {
            chartContainer.post(() -> animateBarUpdates(targetHeightsPx, targetVisibilities));
        }
    }

    private int resolveMaxBarHeightPx() {
        int fallbackHeightPx = Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                CHART_MAX_HEIGHT_DP,
                getResources().getDisplayMetrics()
        ));
        int containerHeightPx = chartVisualContainer != null ? chartVisualContainer.getHeight() : 0;
        if (containerHeightPx <= 0) {
            containerHeightPx = fallbackHeightPx;
        }
        int availableHeightPx = containerHeightPx - barChartContainer.getPaddingTop() - barChartContainer.getPaddingBottom();
        return Math.max(0, availableHeightPx);
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
                    height,
                    selectedChartFraction
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
                ZonedDateTime.now(ZoneId.systemDefault()),
                selectedChartFraction
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
        float selectedFraction = clampedX / (float) Math.max(1, width - 1);
        int bucketIndex = Math.min(
                displayedBarEntries.size() - 1,
                Math.max(0, (int) ((clampedX / (float) width) * displayedBarEntries.size()))
        );
        updateChartSelection(bucketIndex, selectedFraction);
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
            clearChartSelection(true);
            return;
        }

        dismissChartTooltipPopup(true);

        TextView tooltipView = new TextView(this);
        tooltipView.setText(text);
        tooltipView.setTextColor(MaterialColors.getColor(tooltipView, com.google.android.material.R.attr.colorOnSurface));
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
        chartTooltipPopup.setOnDismissListener(() -> {
            chartTooltipPopup = null;
            if (suppressNextTooltipDismissCallback) {
                suppressNextTooltipDismissCallback = false;
                return;
            }
            clearChartSelection(true);
        });

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

    private void dismissChartTooltipPopup(boolean suppressDismissCallback) {
        if (chartTooltipPopup == null) {
            return;
        }
        suppressNextTooltipDismissCallback = suppressDismissCallback;
        PopupWindow popup = chartTooltipPopup;
        chartTooltipPopup = null;
        popup.dismiss();
    }

    private void clearChartSelection(boolean rerenderChart) {
        dismissChartTooltipPopup(true);
        boolean hadSelection = selectedChartBucketIndex >= 0 || !Float.isNaN(selectedChartFraction);
        selectedChartBucketIndex = -1;
        selectedChartFraction = Float.NaN;
        if (hadSelection && rerenderChart) {
            rerenderChartSelectionState();
        }
    }

    private void updateChartSelection(int bucketIndex, float selectionFraction) {
        if (selectedChartBucketIndex == bucketIndex
                && Math.abs(selectedChartFraction - selectionFraction) < 0.0001f) {
            return;
        }
        selectedChartBucketIndex = bucketIndex;
        selectedChartFraction = selectionFraction;
        rerenderChartSelectionState();
    }

    private void rerenderChartSelectionState() {
        if (chartContainer.getVisibility() != View.VISIBLE) {
            return;
        }
        int chartMode = getMainChartMode();
        if (chartMode == MAIN_CHART_MODE_BARS) {
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
            ImageView bar = findViewById(BAR_IDS[i]);
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

    private void renderAverageSummaries(List<PriceFetcher.PriceEntry> entries) {
        String country = getSelectedCountryCode();
        ZoneId zoneId = RegionConfig.getZoneId(country);
        if (zoneId == null) {
            zoneId = ZoneId.systemDefault();
        }
        LocalDate today = LocalDate.now(zoneId);
        LocalDate yesterday = today.minusDays(1);
        LocalDate tomorrow = today.plusDays(1);
        updateAverageCardDates(yesterday, today, tomorrow);

        AverageSummary yesterdaySummary = new AverageSummary();
        AverageSummary todaySummary = new AverageSummary();
        AverageSummary tomorrowSummary = new AverageSummary();

        for (PriceFetcher.PriceEntry entry : entries) {
            if (entry.startTime == null || entry.endTime == null) {
                continue;
            }
            long minutes = Duration.between(entry.startTime, entry.endTime).toMinutes();
            if (minutes <= 0) {
                continue;
            }
            LocalDate entryDate = entry.startTime.atZoneSameInstant(zoneId).toLocalDate();
            if (yesterday.equals(entryDate)) {
                yesterdaySummary.add(entry.pricePerKwh, minutes);
            } else if (today.equals(entryDate)) {
                todaySummary.add(entry.pricePerKwh, minutes);
            } else if (tomorrow.equals(entryDate)) {
                tomorrowSummary.add(entry.pricePerKwh, minutes);
            }
        }

        String unitText = PriceDisplayUtils.getUnitText(country, sharedPreferences);
        setAverageCard(
                yesterdayAverageValue,
                yesterdayAverageUnit,
                yesterdayAverageMin,
                yesterdayAverageMax,
                yesterdaySummary,
                country,
                unitText,
                R.string.average_unavailable_short
        );
        setAverageCard(
                todayAverageValue,
                todayAverageUnit,
                todayAverageMin,
                todayAverageMax,
                todaySummary,
                country,
                unitText,
                R.string.average_unavailable_short
        );
        setAverageCard(
                tomorrowAverageValue,
                tomorrowAverageUnit,
                tomorrowAverageMin,
                tomorrowAverageMax,
                tomorrowSummary,
                country,
                unitText,
                R.string.tomorrow_average_pending
        );
        setAverageCardEnabled(yesterdayAverageCard, yesterdaySummary.hasData());
        setAverageCardEnabled(todayAverageCard, todaySummary.hasData());
        setAverageCardEnabled(tomorrowAverageCard, tomorrowSummary.hasData());
        restorePendingAverageDetailsDialogIfPossible();
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
                                AverageSummary summary,
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
                PriceDisplayUtils.formatPrice(summary.minPrice, country, sharedPreferences)
        ));
        maxView.setText(getString(
                R.string.average_max_format,
                PriceDisplayUtils.formatPrice(summary.maxPrice, country, sharedPreferences)
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

    private static final class AverageSummary {
        private double weightedTotal = 0.0;
        private long totalMinutes = 0L;
        private double minPrice = Double.POSITIVE_INFINITY;
        private double maxPrice = Double.NEGATIVE_INFINITY;

        void add(double price, long minutes) {
            weightedTotal += price * minutes;
            totalMinutes += minutes;
            minPrice = Math.min(minPrice, price);
            maxPrice = Math.max(maxPrice, price);
        }

        boolean hasData() {
            return totalMinutes > 0L;
        }

        double average() {
            return weightedTotal / totalMinutes;
        }
    }

    private static final class AverageDayDetails {
        private final int titleResId;
        private final LocalDate date;
        private final ZoneId zoneId;
        private final String countryCode;
        private final String unitText;
        private final List<PriceFetcher.PriceEntry> entries;
        private final AverageSummary summary;
        private final PriceFetcher.PriceEntry minEntry;
        private final PriceFetcher.PriceEntry maxEntry;

        AverageDayDetails(int titleResId,
                          LocalDate date,
                          ZoneId zoneId,
                          String countryCode,
                          String unitText,
                          List<PriceFetcher.PriceEntry> entries,
                          AverageSummary summary,
                          PriceFetcher.PriceEntry minEntry,
                          PriceFetcher.PriceEntry maxEntry) {
            this.titleResId = titleResId;
            this.date = date;
            this.zoneId = zoneId;
            this.countryCode = countryCode;
            this.unitText = unitText;
            this.entries = entries;
            this.summary = summary;
            this.minEntry = minEntry;
            this.maxEntry = maxEntry;
        }
    }

    private void animateBars(int[] startHeightsPx,
                             int[] targetHeightsPx,
                             boolean[] targetVisibilities,
                             long durationMs,
                             long staggerMs) {
        cancelBarAnimation();

        List<Animator> animators = new ArrayList<>();
        LinearOutSlowInInterpolator interpolator = new LinearOutSlowInInterpolator();

        for (int i = 0; i < BAR_IDS.length; i++) {
            ImageView bar = findViewById(BAR_IDS[i]);
            if (!targetVisibilities[i] && startHeightsPx[i] <= 0) {
                bar.setVisibility(View.INVISIBLE);
                setBarHeight(bar, 0);
                continue;
            }

            bar.setVisibility(View.VISIBLE);
            setBarHeight(bar, startHeightsPx[i]);
            ValueAnimator animator = ValueAnimator.ofInt(startHeightsPx[i], targetHeightsPx[i]);
            animator.setDuration(durationMs);
            animator.setStartDelay(i * staggerMs);
            animator.setInterpolator(interpolator);
            animator.addUpdateListener(valueAnimator -> setBarHeight(bar, (int) valueAnimator.getAnimatedValue()));
            animators.add(animator);
        }

        barAnimator = new AnimatorSet();
        barAnimator.playTogether(animators);
        barAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationCancel(Animator animation) {
                applyBarState(targetHeightsPx, targetVisibilities);
                barAnimator = null;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                applyBarState(targetHeightsPx, targetVisibilities);
                barAnimator = null;
            }
        });
        barAnimator.start();
    }

    private void animateBarUpdates(int[] targetHeightsPx, boolean[] targetVisibilities) {
        int[] currentHeightsPx = getCurrentBarHeights();
        if (!hasBarStateChanges(currentHeightsPx, targetHeightsPx, targetVisibilities)) {
            applyBarState(targetHeightsPx, targetVisibilities);
            return;
        }
        animateBars(
                currentHeightsPx,
                targetHeightsPx,
                targetVisibilities,
                BAR_UPDATE_ANIMATION_DURATION_MS,
                0L
        );
    }

    private int[] getCurrentBarHeights() {
        int[] heightsPx = new int[BAR_IDS.length];
        for (int i = 0; i < BAR_IDS.length; i++) {
            ImageView bar = findViewById(BAR_IDS[i]);
            ViewGroup.LayoutParams params = bar.getLayoutParams();
            heightsPx[i] = params != null ? params.height : 0;
        }
        return heightsPx;
    }

    private boolean hasBarStateChanges(int[] currentHeightsPx,
                                       int[] targetHeightsPx,
                                       boolean[] targetVisibilities) {
        for (int i = 0; i < BAR_IDS.length; i++) {
            ImageView bar = findViewById(BAR_IDS[i]);
            int currentVisibility = bar.getVisibility();
            int targetVisibility = targetVisibilities[i] ? View.VISIBLE : View.INVISIBLE;
            if (currentHeightsPx[i] != targetHeightsPx[i] || currentVisibility != targetVisibility) {
                return true;
            }
        }
        return false;
    }

    private void applyBarHeights(int[] targetHeightsPx) {
        for (int i = 0; i < BAR_IDS.length; i++) {
            ImageView bar = findViewById(BAR_IDS[i]);
            setBarHeight(bar, targetHeightsPx[i]);
        }
    }

    private void applyBarState(int[] targetHeightsPx, boolean[] targetVisibilities) {
        applyBarHeights(targetHeightsPx);
        for (int i = 0; i < BAR_IDS.length; i++) {
            ImageView bar = findViewById(BAR_IDS[i]);
            bar.setVisibility(targetVisibilities[i] ? View.VISIBLE : View.INVISIBLE);
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
        currentPriceInfoTrigger.setOnClickListener(v -> showCurrentPriceDetailsDialog());
    }

    private void setupAverageCardDialogs() {
        yesterdayAverageCard.setOnClickListener(
                v -> showAverageDetailsDialog(AVERAGE_DAY_YESTERDAY, v)
        );
        todayAverageCard.setOnClickListener(
                v -> showAverageDetailsDialog(AVERAGE_DAY_TODAY, v)
        );
        tomorrowAverageCard.setOnClickListener(
                v -> showAverageDetailsDialog(AVERAGE_DAY_TOMORROW, v)
        );
    }

    private void showAverageDetailsDialog(int dayOffset, View sourceCard) {
        showAverageDetailsDialog(
                dayOffset,
                sourceCard,
                true,
                WidgetPreferences.INCREMENT_60_MINUTES,
                0
        );
    }

    private void showAverageDetailsDialog(int dayOffset,
                                          View sourceCard,
                                          boolean animate,
                                          int initialIncrementMinutes,
                                          int initialScrollY) {
        if (averageDetailsOverlay != null || sourceCard == null) {
            return;
        }

        AverageDayDetails details = buildAverageDayDetails(dayOffset);
        if (!details.summary.hasData()) {
            return;
        }
        View overlay = getLayoutInflater().inflate(R.layout.dialog_average_details, activityRoot, false);
        View scrim = overlay.findViewById(R.id.average_details_scrim);
        View detailsContainer = overlay.findViewById(R.id.average_details_container);

        averageDetailsOverlay = overlay;
        activeAverageDetailsSourceCard = sourceCard;
        activeAverageDetailsDayOffset = dayOffset;
        activeAverageDetailsIncrementMinutes = normalizeAverageDetailsIncrement(initialIncrementMinutes);

        bindAverageDetailsDialog(
                overlay,
                details,
                activeAverageDetailsIncrementMinutes,
                initialScrollY
        );
        scrim.setOnClickListener(v -> dismissAverageDetailsDialog(true));

        activityRoot.addView(overlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        overlay.setVisibility(View.VISIBLE);
        averageDetailsBackCallback.setEnabled(true);
        overlay.requestFocus();

        activityRoot.post(() -> {
            applyAverageDetailsContainerSize(detailsContainer);
            if (animate) {
                startAverageDetailsEnterTransition(sourceCard, overlay, detailsContainer);
            } else {
                scrim.setAlpha(1f);
                sourceCard.setVisibility(View.INVISIBLE);
                detailsContainer.setVisibility(View.VISIBLE);
            }
        });
    }

    private void bindAverageDetailsDialog(View overlay,
                                          AverageDayDetails details,
                                          int initialIncrementMinutes,
                                          int initialScrollY) {
        ((TextView) overlay.findViewById(R.id.average_details_title)).setText(details.titleResId);
        ((TextView) overlay.findViewById(R.id.average_details_date)).setText(
                formatAverageDetailsDate(details.date)
        );
        ((TextView) overlay.findViewById(R.id.average_details_average_label)).setText(R.string.average_label);

        TextView valueView = overlay.findViewById(R.id.average_details_value);
        TextView unitView = overlay.findViewById(R.id.average_details_unit);
        TextView minPriceView = overlay.findViewById(R.id.average_details_min_price);
        TextView minTimeView = overlay.findViewById(R.id.average_details_min_time);
        TextView maxPriceView = overlay.findViewById(R.id.average_details_max_price);
        TextView maxTimeView = overlay.findViewById(R.id.average_details_max_time);
        valueView.setText(PriceDisplayUtils.formatPrice(
                details.summary.average(),
                details.countryCode,
                sharedPreferences
        ));
        unitView.setText(details.unitText);
        bindAverageDetailsExtreme(
                minPriceView,
                minTimeView,
                details.minEntry,
                details
        );
        bindAverageDetailsExtreme(
                maxPriceView,
                maxTimeView,
                details.maxEntry,
                details
        );
        bindAverageDetailsExtremeActions(overlay, details);

        ChipGroup chipGroup = overlay.findViewById(R.id.average_details_density_chip_group);
        configureAverageDetailsChipAnimation(chipGroup);
        chipGroup.check(getAverageDetailsChipForIncrement(initialIncrementMinutes));
        chipGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == View.NO_ID) {
                return;
            }
            activeAverageDetailsIncrementMinutes = getAverageDetailsIncrementForChip(checkedId);
            renderAverageDetailsPriceRows(
                    overlay,
                    details,
                    activeAverageDetailsIncrementMinutes
            );
        });
        renderAverageDetailsPriceRows(overlay, details, initialIncrementMinutes);
        if (initialScrollY > 0) {
            ScrollView scrollView = overlay.findViewById(R.id.average_details_price_scroll);
            scrollView.post(() -> scrollView.scrollTo(0, initialScrollY));
        }
    }

    private void bindAverageDetailsExtreme(TextView priceView,
                                           TextView timeView,
                                           PriceFetcher.PriceEntry entry,
                                           AverageDayDetails details) {
        String priceText = PriceDisplayUtils.formatPrice(
                entry.pricePerKwh,
                details.countryCode,
                sharedPreferences
        );
        String displayText = getString(
                R.string.current_price_details_value_exact,
                priceText,
                details.unitText
        );
        SpannableString styledText = new SpannableString(displayText);
        int unitStart = Math.max(0, displayText.length() - details.unitText.length());
        int unitColor = MaterialColors.getColor(
                priceView,
                com.google.android.material.R.attr.colorOnSurfaceVariant
        );
        styledText.setSpan(
                new RelativeSizeSpan(0.78f),
                unitStart,
                displayText.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        styledText.setSpan(
                new ForegroundColorSpan(unitColor),
                unitStart,
                displayText.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        priceView.setText(styledText);
        timeView.setText(formatAverageDetailsTimeRange(entry, details.zoneId));
    }

    private String formatAverageDetailsDate(LocalDate date) {
        return date.format(AVERAGE_DETAILS_DATE_FORMATTER);
    }

    private void configureAverageDetailsChipAnimation(ChipGroup chipGroup) {
        LayoutTransition layoutTransition = new LayoutTransition();
        layoutTransition.enableTransitionType(LayoutTransition.CHANGING);
        layoutTransition.setDuration(LayoutTransition.APPEARING, 120L);
        layoutTransition.setDuration(LayoutTransition.DISAPPEARING, 120L);
        layoutTransition.setDuration(LayoutTransition.CHANGE_APPEARING, 180L);
        layoutTransition.setDuration(LayoutTransition.CHANGE_DISAPPEARING, 180L);
        layoutTransition.setDuration(LayoutTransition.CHANGING, 180L);
        chipGroup.setLayoutTransition(layoutTransition);
    }

    private void renderAverageDetailsPriceRows(View overlay,
                                               AverageDayDetails details,
                                               int incrementMinutes) {
        LinearLayout rows = overlay.findViewById(R.id.average_details_price_rows);
        ScrollView scrollView = overlay.findViewById(R.id.average_details_price_scroll);
        rows.removeAllViews();

        List<PriceFetcher.PriceEntry> displayEntries = getAverageDetailsDisplayEntries(
                details.entries,
                incrementMinutes
        );

        for (int i = 0; i < displayEntries.size(); i++) {
            if (i > 0) {
                addAverageDetailsRowDivider(rows);
            }
            PriceFetcher.PriceEntry entry = displayEntries.get(i);
            View row = getLayoutInflater().inflate(R.layout.list_item_average_price, rows, false);
            row.setTag(entry);
            TextView timeView = row.findViewById(R.id.average_details_price_row_time);
            TextView priceView = row.findViewById(R.id.average_details_price_row_value);
            timeView.setText(formatAverageDetailsTimeRange(entry, details.zoneId));
            priceView.setText(getString(
                    R.string.current_price_details_value_exact,
                    PriceDisplayUtils.formatPrice(entry.pricePerKwh, details.countryCode, sharedPreferences),
                    details.unitText
            ));
            rows.addView(row);
        }
        scrollView.scrollTo(0, 0);
    }

    private void bindAverageDetailsExtremeActions(View overlay, AverageDayDetails details) {
        bindAverageDetailsExtremeAction(
                overlay.findViewById(R.id.average_details_min),
                overlay,
                details.minEntry
        );
        bindAverageDetailsExtremeAction(
                overlay.findViewById(R.id.average_details_max),
                overlay,
                details.maxEntry
        );
    }

    private void bindAverageDetailsExtremeAction(View container,
                                                 View overlay,
                                                 PriceFetcher.PriceEntry entry) {
        boolean enabled = entry != null;
        container.setEnabled(enabled);
        container.setClickable(enabled);
        container.setFocusable(enabled);
        container.setOnClickListener(enabled
                ? v -> scrollToAverageDetailsEntry(overlay, entry)
                : null);
    }

    private void scrollToAverageDetailsEntry(View overlay, PriceFetcher.PriceEntry entry) {
        ScrollView scrollView = overlay.findViewById(R.id.average_details_price_scroll);
        LinearLayout rows = overlay.findViewById(R.id.average_details_price_rows);
        View targetRow = findAverageDetailsRow(rows, entry);
        if (targetRow == null) {
            return;
        }

        scrollView.post(() -> {
            int targetScrollY = targetRow.getTop() - ((scrollView.getHeight() - targetRow.getHeight()) / 2);
            int maxScrollY = Math.max(0, rows.getHeight() - scrollView.getHeight());
            targetScrollY = Math.max(0, Math.min(targetScrollY, maxScrollY));
            scrollView.smoothScrollTo(0, targetScrollY);
            targetRow.postDelayed(
                    () -> highlightAverageDetailsRow(targetRow),
                    AVERAGE_DETAILS_ROW_HIGHLIGHT_DELAY_MS
            );
        });
    }

    private View findAverageDetailsRow(LinearLayout rows, PriceFetcher.PriceEntry targetEntry) {
        for (int i = 0; i < rows.getChildCount(); i++) {
            View child = rows.getChildAt(i);
            Object tag = child.getTag();
            if (tag instanceof PriceFetcher.PriceEntry
                    && averageDetailsEntryContains((PriceFetcher.PriceEntry) tag, targetEntry)) {
                return child;
            }
        }
        return null;
    }

    private boolean averageDetailsEntryContains(PriceFetcher.PriceEntry displayEntry,
                                                PriceFetcher.PriceEntry targetEntry) {
        if (displayEntry == null
                || targetEntry == null
                || displayEntry.startTime == null
                || displayEntry.endTime == null
                || targetEntry.startTime == null
                || targetEntry.endTime == null) {
            return false;
        }
        boolean containsTarget = !targetEntry.startTime.isBefore(displayEntry.startTime)
                && !targetEntry.endTime.isAfter(displayEntry.endTime);
        boolean overlapsTarget = targetEntry.startTime.isBefore(displayEntry.endTime)
                && targetEntry.endTime.isAfter(displayEntry.startTime);
        return containsTarget || overlapsTarget;
    }

    private void highlightAverageDetailsRow(View row) {
        Drawable originalForeground = row.getForeground();
        int highlightColor = MaterialColors.getColor(
                row,
                com.google.android.material.R.attr.colorSecondaryContainer
        );
        GradientDrawable highlight = new GradientDrawable();
        highlight.setColor(withAlpha(highlightColor, AVERAGE_DETAILS_ROW_HIGHLIGHT_ALPHA));
        highlight.setCornerRadius(dpToPx(12));
        row.setForeground(highlight);

        ValueAnimator animator = ValueAnimator.ofInt(AVERAGE_DETAILS_ROW_HIGHLIGHT_ALPHA, 0);
        animator.setDuration(AVERAGE_DETAILS_ROW_HIGHLIGHT_DURATION_MS);
        animator.setInterpolator(new LinearOutSlowInInterpolator());
        animator.addUpdateListener(animation -> highlight.setColor(withAlpha(
                highlightColor,
                (int) animation.getAnimatedValue()
        )));
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (row.getForeground() == highlight) {
                    row.setForeground(originalForeground);
                }
            }
        });
        animator.start();
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private void addAverageDetailsRowDivider(LinearLayout rows) {
        View divider = new View(this);
        divider.setAlpha(0.32f);
        divider.setBackgroundColor(MaterialColors.getColor(
                rows,
                com.google.android.material.R.attr.colorOutlineVariant
        ));
        rows.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
        ));
    }

    private List<PriceFetcher.PriceEntry> getAverageDetailsDisplayEntries(List<PriceFetcher.PriceEntry> entries,
                                                                          int incrementMinutes) {
        if (incrementMinutes <= WidgetPreferences.INCREMENT_15_MINUTES) {
            return new ArrayList<>(entries);
        }
        return PriceFetcher.aggregateConsecutive(
                entries,
                incrementMinutes,
                WidgetPreferences.POOL_MODE_AVERAGE
        );
    }

    private int getAverageDetailsIncrementForChip(int checkedId) {
        if (checkedId == R.id.average_details_density_30_chip) {
            return WidgetPreferences.INCREMENT_30_MINUTES;
        }
        if (checkedId == R.id.average_details_density_hourly_chip) {
            return WidgetPreferences.INCREMENT_60_MINUTES;
        }
        return WidgetPreferences.INCREMENT_15_MINUTES;
    }

    private int getAverageDetailsChipForIncrement(int incrementMinutes) {
        if (incrementMinutes == WidgetPreferences.INCREMENT_15_MINUTES) {
            return R.id.average_details_density_15_chip;
        }
        if (incrementMinutes == WidgetPreferences.INCREMENT_30_MINUTES) {
            return R.id.average_details_density_30_chip;
        }
        return R.id.average_details_density_hourly_chip;
    }

    private int normalizeAverageDetailsIncrement(int incrementMinutes) {
        if (incrementMinutes == WidgetPreferences.INCREMENT_15_MINUTES
                || incrementMinutes == WidgetPreferences.INCREMENT_30_MINUTES
                || incrementMinutes == WidgetPreferences.INCREMENT_60_MINUTES) {
            return incrementMinutes;
        }
        return WidgetPreferences.INCREMENT_60_MINUTES;
    }

    private void restorePendingAverageDetailsDialogIfPossible() {
        if (pendingAverageDetailsDayOffset == AVERAGE_DETAILS_NO_RESTORE
                || averageDetailsOverlay != null) {
            return;
        }

        View sourceCard = getAverageDetailsSourceCard(pendingAverageDetailsDayOffset);
        if (sourceCard == null || !sourceCard.isEnabled()) {
            return;
        }

        int dayOffset = pendingAverageDetailsDayOffset;
        int incrementMinutes = normalizeAverageDetailsIncrement(pendingAverageDetailsIncrementMinutes);
        int scrollY = pendingAverageDetailsScrollY;
        pendingAverageDetailsDayOffset = AVERAGE_DETAILS_NO_RESTORE;
        pendingAverageDetailsIncrementMinutes = WidgetPreferences.INCREMENT_60_MINUTES;
        pendingAverageDetailsScrollY = 0;
        showAverageDetailsDialog(dayOffset, sourceCard, false, incrementMinutes, scrollY);
    }

    private View getAverageDetailsSourceCard(int dayOffset) {
        if (dayOffset == AVERAGE_DAY_YESTERDAY) {
            return yesterdayAverageCard;
        }
        if (dayOffset == AVERAGE_DAY_TOMORROW) {
            return tomorrowAverageCard;
        }
        if (dayOffset == AVERAGE_DAY_TODAY) {
            return todayAverageCard;
        }
        return null;
    }

    private String formatAverageDetailsTimeRange(PriceFetcher.PriceEntry entry, ZoneId zoneId) {
        if (entry == null || entry.startTime == null || entry.endTime == null) {
            return "";
        }
        ZonedDateTime start = entry.startTime.atZoneSameInstant(zoneId);
        ZonedDateTime end = entry.endTime.atZoneSameInstant(zoneId);
        return String.format(
                "%02d:%02d-%02d:%02d",
                start.getHour(),
                start.getMinute(),
                end.getHour(),
                end.getMinute()
        );
    }

    private AverageDayDetails buildAverageDayDetails(int dayOffset) {
        String countryCode = getSelectedCountryCode();
        ZoneId zoneId = RegionConfig.getZoneId(countryCode);
        if (zoneId == null) {
            zoneId = ZoneId.systemDefault();
        }

        LocalDate date = LocalDate.now(zoneId).plusDays(dayOffset);
        List<PriceFetcher.PriceEntry> allEntries = CurrentPriceResolver.getAdjustedEntries(this, sharedPreferences);
        List<PriceFetcher.PriceEntry> entries = getEntriesForLocalDate(allEntries, date, zoneId);
        AverageSummary summary = summarizeAverageEntries(entries);
        PriceFetcher.PriceEntry minEntry = findAverageDetailsExtremeEntry(entries, true);
        PriceFetcher.PriceEntry maxEntry = findAverageDetailsExtremeEntry(entries, false);
        return new AverageDayDetails(
                getAverageDetailsTitleResId(dayOffset),
                date,
                zoneId,
                countryCode,
                PriceDisplayUtils.getUnitText(countryCode, sharedPreferences),
                entries,
                summary,
                minEntry,
                maxEntry
        );
    }

    private List<PriceFetcher.PriceEntry> getEntriesForLocalDate(List<PriceFetcher.PriceEntry> entries,
                                                                 LocalDate date,
                                                                 ZoneId zoneId) {
        List<PriceFetcher.PriceEntry> dayEntries = new ArrayList<>();
        for (PriceFetcher.PriceEntry entry : entries) {
            if (entry == null || entry.startTime == null || entry.endTime == null) {
                continue;
            }
            LocalDate entryDate = entry.startTime.atZoneSameInstant(zoneId).toLocalDate();
            if (date.equals(entryDate)) {
                dayEntries.add(entry);
            }
        }
        return dayEntries;
    }

    private AverageSummary summarizeAverageEntries(List<PriceFetcher.PriceEntry> entries) {
        AverageSummary summary = new AverageSummary();
        for (PriceFetcher.PriceEntry entry : entries) {
            long minutes = Duration.between(entry.startTime, entry.endTime).toMinutes();
            if (minutes > 0L) {
                summary.add(entry.pricePerKwh, minutes);
            }
        }
        return summary;
    }

    private PriceFetcher.PriceEntry findAverageDetailsExtremeEntry(List<PriceFetcher.PriceEntry> entries,
                                                                   boolean findMinimum) {
        PriceFetcher.PriceEntry extremeEntry = null;
        for (PriceFetcher.PriceEntry entry : entries) {
            if (entry == null || entry.startTime == null || entry.endTime == null) {
                continue;
            }
            long minutes = Duration.between(entry.startTime, entry.endTime).toMinutes();
            if (minutes <= 0L) {
                continue;
            }
            if (extremeEntry == null
                    || (findMinimum && entry.pricePerKwh < extremeEntry.pricePerKwh)
                    || (!findMinimum && entry.pricePerKwh > extremeEntry.pricePerKwh)) {
                extremeEntry = entry;
            }
        }
        return extremeEntry;
    }

    private int getAverageDetailsTitleResId(int dayOffset) {
        if (dayOffset == AVERAGE_DAY_YESTERDAY) {
            return R.string.average_yesterday_label;
        }
        if (dayOffset == AVERAGE_DAY_TOMORROW) {
            return R.string.average_tomorrow_label;
        }
        return R.string.average_today_label;
    }

    private void applyAverageDetailsContainerSize(View detailsContainer) {
        int rootWidth = activityRoot.getWidth();
        if (rootWidth <= 0) {
            rootWidth = getResources().getDisplayMetrics().widthPixels;
        }
        int rootHeight = activityRoot.getHeight();
        if (rootHeight <= 0) {
            rootHeight = getResources().getDisplayMetrics().heightPixels;
        }
        int availableWidth = Math.max(1, rootWidth - dpToPx(AVERAGE_DETAILS_SIDE_MARGIN_DP * 2));
        int availableHeight = Math.max(1, rootHeight - dpToPx(AVERAGE_DETAILS_SIDE_MARGIN_DP * 2));
        int targetWidth = Math.min(availableWidth, dpToPx(AVERAGE_DETAILS_MAX_WIDTH_DP));
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) detailsContainer.getLayoutParams();
        params.width = targetWidth;
        detailsContainer.measure(
                View.MeasureSpec.makeMeasureSpec(targetWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        );
        params.height = detailsContainer.getMeasuredHeight() > availableHeight
                ? availableHeight
                : FrameLayout.LayoutParams.WRAP_CONTENT;
        params.gravity = Gravity.CENTER;
        detailsContainer.setLayoutParams(params);
    }

    private void startAverageDetailsEnterTransition(View sourceCard, View overlay, View detailsContainer) {
        View scrim = overlay.findViewById(R.id.average_details_scrim);
        scrim.animate()
                .alpha(1f)
                .setDuration(AVERAGE_DETAILS_SCRIM_DURATION_MS)
                .start();

        MaterialContainerTransform transform = createAverageDetailsContainerTransform(sourceCard, detailsContainer);
        transform.setTransitionDirection(MaterialContainerTransform.TRANSITION_DIRECTION_ENTER);
        transform.addTarget(detailsContainer);
        transform.addListener(new Transition.TransitionListener() {
            @Override
            public void onTransitionStart(Transition transition) {
            }

            @Override
            public void onTransitionEnd(Transition transition) {
                detailsContainer.setVisibility(View.VISIBLE);
                sourceCard.setVisibility(View.INVISIBLE);
            }

            @Override
            public void onTransitionCancel(Transition transition) {
                detailsContainer.setVisibility(View.VISIBLE);
                sourceCard.setVisibility(View.INVISIBLE);
            }

            @Override
            public void onTransitionPause(Transition transition) {
            }

            @Override
            public void onTransitionResume(Transition transition) {
            }
        });

        TransitionManager.beginDelayedTransition(activityRoot, transform);
        sourceCard.setVisibility(View.INVISIBLE);
        detailsContainer.setVisibility(View.VISIBLE);
    }

    private void dismissAverageDetailsDialog(boolean animate) {
        if (averageDetailsOverlay == null) {
            return;
        }

        View overlay = averageDetailsOverlay;
        View sourceCard = activeAverageDetailsSourceCard;
        View detailsContainer = overlay.findViewById(R.id.average_details_container);
        averageDetailsBackCallback.setEnabled(false);

        if (!animate || sourceCard == null || !sourceCard.isAttachedToWindow()) {
            removeAverageDetailsOverlay(sourceCard);
            return;
        }

        overlay.findViewById(R.id.average_details_scrim)
                .animate()
                .alpha(0f)
                .setDuration(AVERAGE_DETAILS_SCRIM_DURATION_MS)
                .start();

        MaterialContainerTransform transform = createAverageDetailsContainerTransform(detailsContainer, sourceCard);
        transform.setTransitionDirection(MaterialContainerTransform.TRANSITION_DIRECTION_RETURN);
        transform.addTarget(sourceCard);
        transform.addListener(new Transition.TransitionListener() {
            private boolean finished;

            @Override
            public void onTransitionStart(Transition transition) {
            }

            @Override
            public void onTransitionEnd(Transition transition) {
                finish();
            }

            @Override
            public void onTransitionCancel(Transition transition) {
                finish();
            }

            @Override
            public void onTransitionPause(Transition transition) {
            }

            @Override
            public void onTransitionResume(Transition transition) {
            }

            private void finish() {
                if (finished) {
                    return;
                }
                finished = true;
                removeAverageDetailsOverlay(sourceCard);
            }
        });

        TransitionManager.beginDelayedTransition(activityRoot, transform);
        detailsContainer.setVisibility(View.INVISIBLE);
        sourceCard.setVisibility(View.VISIBLE);
    }

    private MaterialContainerTransform createAverageDetailsContainerTransform(View startView, View endView) {
        MaterialContainerTransform transform = new MaterialContainerTransform();
        transform.setStartView(startView);
        transform.setEndView(endView);
        transform.setDrawingViewId(R.id.activity_root);
        transform.setDuration(AVERAGE_DETAILS_TRANSITION_DURATION_MS);
        transform.setScrimColor(Color.TRANSPARENT);
        transform.setFadeMode(MaterialContainerTransform.FADE_MODE_THROUGH);
        transform.setFitMode(MaterialContainerTransform.FIT_MODE_AUTO);
        transform.setPathMotion(new MaterialArcMotion());
        transform.setElevationShadowEnabled(true);
        transform.setStartContainerColor(getAverageDetailsContainerColor(startView));
        transform.setEndContainerColor(getAverageDetailsContainerColor(endView));
        transform.setStartElevation(getAverageDetailsContainerElevation(startView));
        transform.setEndElevation(getAverageDetailsContainerElevation(endView));
        return transform;
    }

    private float getAverageDetailsContainerElevation(View view) {
        if (view.getId() == R.id.average_details_container) {
            return dpToPx(AVERAGE_DETAILS_CONTAINER_TRANSFORM_ELEVATION_DP);
        }
        if (view instanceof MaterialCardView) {
            return ((MaterialCardView) view).getCardElevation();
        }
        return view.getElevation();
    }

    private int getAverageDetailsContainerColor(View view) {
        if (view instanceof MaterialCardView) {
            return ((MaterialCardView) view).getCardBackgroundColor().getDefaultColor();
        }
        return MaterialColors.getColor(
                view,
                com.google.android.material.R.attr.colorSurfaceContainerHigh
        );
    }

    private void removeAverageDetailsOverlay(View sourceCard) {
        if (sourceCard != null) {
            sourceCard.setVisibility(View.VISIBLE);
        }
        if (averageDetailsOverlay != null) {
            averageDetailsOverlay.animate().cancel();
            activityRoot.removeView(averageDetailsOverlay);
        }
        averageDetailsOverlay = null;
        activeAverageDetailsSourceCard = null;
        activeAverageDetailsDayOffset = AVERAGE_DETAILS_NO_RESTORE;
        activeAverageDetailsIncrementMinutes = WidgetPreferences.INCREMENT_60_MINUTES;
        averageDetailsBackCallback.setEnabled(false);
    }

    private void setupAboutDialogTrigger() {
        findViewById(R.id.app_icon_button).setOnClickListener(v ->
                new AboutDialogFragment().show(getSupportFragmentManager(), "about_dialog"));
    }

    private void showInfoDialog(int titleResId, String message) {
        InfoDialogFragment.newInstance(getString(titleResId), message)
                .show(getSupportFragmentManager(), "info_dialog");
    }

    private void showCurrentPriceDetailsDialog() {
        List<PriceFetcher.PriceEntry> allData = CurrentPriceResolver.getAdjustedEntries(this, sharedPreferences);
        PriceFetcher.PriceEntry currentEntry = CurrentPriceResolver.findCurrentEntry(allData);
        if (currentEntry == null) {
            return;
        }

        String countryCode = getSelectedCountryCode();
        String unitText = PriceDisplayUtils.getUnitText(countryCode, sharedPreferences);
        double displayMultiplier = RegionConfig.getPriceDisplayMultiplier(countryCode);
        View contentView = getLayoutInflater().inflate(R.layout.dialog_current_price_details, null);
        bindCurrentPriceDetailsRow(
                contentView.findViewById(R.id.current_price_details_time_row),
                contentView.findViewById(R.id.current_price_details_time_value),
                buildCurrentPriceTimeValue(currentEntry)
        );
        bindCurrentPriceDetailsRow(
                contentView.findViewById(R.id.current_price_details_price_row),
                contentView.findViewById(R.id.current_price_details_price_value),
                getString(
                        R.string.current_price_details_value_exact,
                        formatDetailedPrice(currentEntry.pricePerKwh * displayMultiplier, countryCode, 0, 5),
                        unitText
                )
        );
        bindCurrentPriceDetailsRow(
                contentView.findViewById(R.id.current_price_details_original_row),
                contentView.findViewById(R.id.current_price_details_original_value),
                buildCurrentPriceOriginalValue(currentEntry, countryCode)
        );
        bindCurrentPriceDetailsRow(
                contentView.findViewById(R.id.current_price_details_exchange_rate_row),
                contentView.findViewById(R.id.current_price_details_exchange_rate_value),
                buildCurrentPriceExchangeRateValue(currentEntry, countryCode)
        );

        new MaterialAlertDialogBuilder(this)
                .setView(contentView)
                .show();
    }

    private String getSelectedCountryCode() {
        return PriceRepository.getSelectedCountryCode(this, sharedPreferences);
    }

    private void bindCurrentPriceDetailsRow(View rowView, TextView valueView, String value) {
        if (value == null || value.trim().isEmpty()) {
            rowView.setVisibility(View.GONE);
            return;
        }

        rowView.setVisibility(View.VISIBLE);
        valueView.setText(value);
    }

    private String buildCurrentPriceTimeValue(PriceFetcher.PriceEntry currentEntry) {
        if (currentEntry == null || currentEntry.startTime == null || currentEntry.endTime == null) {
            return null;
        }

        ZonedDateTime start = currentEntry.startTime.atZoneSameInstant(ZoneId.systemDefault());
        ZonedDateTime end = currentEntry.endTime.atZoneSameInstant(ZoneId.systemDefault());
        return String.format(
                "%02d:%02d-%02d:%02d",
                start.getHour(),
                start.getMinute(),
                end.getHour(),
                end.getMinute()
        );
    }

    private String buildCurrentPriceOriginalValue(PriceFetcher.PriceEntry currentEntry, String countryCode) {
        if (currentEntry == null || Double.isNaN(currentEntry.pricePerKwhEur)) {
            return null;
        }

        return getString(
                R.string.current_price_details_value_original,
                formatDetailedPrice(currentEntry.pricePerKwhEur, countryCode, 0, 5)
        );
    }

    private String buildCurrentPriceExchangeRateValue(PriceFetcher.PriceEntry currentEntry, String countryCode) {
        if (currentEntry == null) {
            return null;
        }

        String currency = currentEntry.currency;
        if (currency == null || currency.isEmpty()) {
            currency = RegionConfig.getCurrency(countryCode);
        }

        if ("EUR".equals(currency)
                || Double.isNaN(currentEntry.exchangeRatePerEur)
                || currentEntry.exchangeRatePerEur <= 0.0) {
            return null;
        }

        return getString(
                R.string.current_price_details_value_exchange_rate,
                formatDetailedPrice(currentEntry.exchangeRatePerEur, countryCode, 0, 5),
                currency
        );
    }

    private String formatVatPercent(double vatPercent) {
        NumberFormat numberFormat = NumberFormat.getNumberInstance();
        numberFormat.setGroupingUsed(false);
        numberFormat.setMinimumFractionDigits(0);
        numberFormat.setMaximumFractionDigits(1);
        return numberFormat.format(vatPercent);
    }

    private String formatDetailedPrice(double value, String countryCode, int minFractionDigits, int maxFractionDigits) {
        NumberFormat numberFormat = NumberFormat.getNumberInstance(RegionConfig.getNumberLocale(countryCode));
        numberFormat.setGroupingUsed(false);
        numberFormat.setMinimumFractionDigits(minFractionDigits);
        numberFormat.setMaximumFractionDigits(maxFractionDigits);
        String formattedValue = numberFormat.format(value);
        String negativeZero = numberFormat.format(-0.0d);
        if (formattedValue.equals(negativeZero)) {
            return numberFormat.format(0.0d);
        }
        return formattedValue;
    }

    private void updateVatLabel(TextView label) {
        label.setText(R.string.vat_label);
    }

    private boolean isMainChartYAxisEnabled() {
        return sharedPreferences.getBoolean(KEY_MAIN_ACTIVITY_SHOW_Y_AXIS, true);
    }

    private void updateChartYAxisVisibility(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        chartYAxisContainer.setVisibility(visibility);
        chartYAxisGuides.setVisibility(visibility);
        chartYAxisSpacer.setVisibility(visibility);
        if (!visible) {
            setChartYAxisTicksVisible(false);
        }
    }

    private void setChartYAxisTicksVisible(boolean visible) {
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

    private void updateChartYAxis(double maxPricePerKwh) {
        boolean showYAxis = isMainChartYAxisEnabled();
        updateChartYAxisVisibility(showYAxis);
        if (!showYAxis) {
            return;
        }

        double safeMaxPrice = maxPricePerKwh > 0.0 ? maxPricePerKwh : 1.0;
        if (chartYAxisContainer.getHeight() <= 0 || chartYAxisGuides.getHeight() <= 0) {
            setChartYAxisTicksVisible(false);
            chartYAxisContainer.post(() -> updateChartYAxis(safeMaxPrice));
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

        for (int i = 0; i < CHART_Y_AXIS_TICK_FRACTIONS.length; i++) {
            double tickValue = normalizeTickValue(safeMaxPrice * CHART_Y_AXIS_TICK_FRACTIONS[i]);
            bindChartYAxisTick(
                    tickLabels[i],
                    tickGuides[i],
                    tickValue,
                    CHART_Y_AXIS_TICK_FRACTIONS[i],
                    countryCode
            );
        }
        setChartYAxisTicksVisible(true);
    }

    private String formatChartAxisValue(double pricePerKwh, String countryCode) {
        double displayValue = pricePerKwh * getChartAxisDisplayMultiplier(countryCode);
        int fractionDigits = resolveChartAxisFractionDigits(displayValue);
        NumberFormat numberFormat = NumberFormat.getNumberInstance(RegionConfig.getNumberLocale(countryCode));
        numberFormat.setGroupingUsed(false);
        numberFormat.setMinimumFractionDigits(fractionDigits);
        numberFormat.setMaximumFractionDigits(fractionDigits);
        String formattedValue = numberFormat.format(displayValue);
        String negativeZero = numberFormat.format(-0.0d);
        if (formattedValue.equals(negativeZero)) {
            return numberFormat.format(0.0d);
        }
        return formattedValue;
    }

    private void bindChartYAxisTick(TextView label,
                                    View guide,
                                    double tickValue,
                                    float tickFraction,
                                    String countryCode) {
        label.setText(formatChartAxisValue(tickValue, countryCode));
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
        float edgeMarginPx = dpToPx(CHART_Y_AXIS_EDGE_MARGIN_DP);
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

    private double resolveRoundedChartScaleMax(double maxPricePerKwh) {
        if (maxPricePerKwh <= 0.0d) {
            return 1.0d;
        }

        double minimumTickStep = maxPricePerKwh / (CHART_Y_AXIS_TICK_FRACTIONS.length + 1.0d);
        double roundedTickStep = resolveNiceChartTickStep(minimumTickStep);
        return roundedTickStep * (CHART_Y_AXIS_TICK_FRACTIONS.length + 1.0d);
    }

    private double resolveNiceChartTickStep(double minimumStep) {
        if (minimumStep <= 0.0d) {
            return 1.0d;
        }

        double exponent = Math.floor(Math.log10(minimumStep));
        double scale = Math.pow(10.0d, exponent);
        double[] multipliers = {1.0d, 2.0d, 2.5d, 4.0d, 5.0d, 6.0d, 8.0d, 10.0d};
        for (double multiplier : multipliers) {
            double candidate = multiplier * scale;
            if (candidate + 0.0000001d >= minimumStep) {
                return candidate;
            }
        }
        return scale * 10.0d;
    }

    private int resolveChartAxisFractionDigits(double displayValue) {
        double absoluteValue = Math.abs(displayValue);
        int integerDigits;
        if (absoluteValue >= 100.0d) {
            integerDigits = 3;
        } else if (absoluteValue >= 10.0d) {
            integerDigits = 2;
        } else {
            integerDigits = 1;
        }
        return Math.max(0, 3 - integerDigits);
    }

    private double getChartAxisDisplayMultiplier(String countryCode) {
        return "CH".equals(countryCode) ? 100.0d : RegionConfig.getPriceDisplayMultiplier(countryCode);
    }

    private double normalizeTickValue(double value) {
        return Math.abs(value) < 0.0000001d ? 0.0d : value;
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
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
