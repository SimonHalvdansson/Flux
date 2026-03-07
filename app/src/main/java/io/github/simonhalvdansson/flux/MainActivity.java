package io.github.simonhalvdansson.flux;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.graphics.Outline;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator;

import java.time.OffsetDateTime;
import java.time.LocalDate;
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

    private TextView currentPriceValue;
    private TextView todayAverageValue;
    private View chartContainer;
    private SharedPreferences sharedPreferences;
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener;
    private AnimatorSet barAnimator;
    private boolean shouldAnimateBars;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sharedPreferences = PriceRepository.getPreferences(this);
        ImageView appIconView = findViewById(R.id.app_icon);
        currentPriceValue = findViewById(R.id.current_price_value);
        todayAverageValue = findViewById(R.id.today_average_value);
        chartContainer = findViewById(R.id.bar_chart_section);
        shouldAnimateBars = savedInstanceState == null
                && !getIntent().getBooleanExtra(EXTRA_DISABLE_CHART_ANIMATION, false);

        configureAppIconShadow(appIconView);
        configureBarShadows();
        applyWindowInsets();

        preferenceChangeListener = (prefs, key) -> {
            if (PriceRepository.KEY_JSON_DATA.equals(key)
                    || PriceUpdateJobService.KEY_API_ERROR.equals(key)
                    || PriceUpdateJobService.KEY_SELECTED_COUNTRY.equals(key)
                    || PriceUpdateJobService.KEY_APPLY_VAT.equals(key)
                    || PriceUpdateJobService.KEY_APPLY_STROMSTOTTE.equals(key)
                    || PriceUpdateJobService.KEY_GRID_FEE.equals(key)) {
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
        //appIconView.setTranslationZ(elevationPx);
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

    private void renderCurrentPrice() {
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
            long diff = Math.abs(java.time.Duration.between(currentHourStart, hourlyData.get(i).startTime).toMinutes());
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
                PriceDisplayUtils.formatPrice(total / count, country),
                PriceDisplayUtils.getUnitText(country)
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
}
