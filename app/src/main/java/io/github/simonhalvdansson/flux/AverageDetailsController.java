package io.github.simonhalvdansson.flux;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.LayoutTransition;
import android.animation.ValueAnimator;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.BackEventCompat;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.android.material.transition.platform.MaterialArcMotion;
import com.google.android.material.transition.platform.MaterialContainerTransform;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class AverageDetailsController {
    private static final String STATE_DAY_OFFSET = "state_average_details_day_offset";
    private static final String STATE_INCREMENT_MINUTES =
            "state_average_details_increment_minutes";
    private static final String STATE_SCROLL_Y = "state_average_details_scroll_y";
    private static final int NO_RESTORE = Integer.MIN_VALUE;
    private static final long TRANSITION_DURATION_MS = 460L;
    private static final long SCRIM_DURATION_MS = 180L;
    private static final int MAX_WIDTH_DP = 520;
    private static final int SIDE_MARGIN_DP = 24;
    private static final int CONTAINER_TRANSFORM_ELEVATION_DP = 10;
    private static final long ROW_HIGHLIGHT_DELAY_MS = 220L;
    private static final long ROW_HIGHLIGHT_HOLD_MS = 850L;
    private static final long ROW_HIGHLIGHT_FADE_MS = 900L;
    private static final int ROW_HIGHLIGHT_ALPHA = 96;
    private static final int SOURCE_CARD_CORNER_RADIUS_DP = 8;
    private static final int DETAILS_CARD_CORNER_RADIUS_DP = 28;
    private static final int PREDICTIVE_BACK_TRANSLATION_X_DP = 56;
    private static final int PREDICTIVE_BACK_TRANSLATION_Y_DP = 18;
    private static final float PREDICTIVE_BACK_MIN_SCALE = 0.9f;
    private static final float PREDICTIVE_BACK_MIN_SCRIM_ALPHA = 0.45f;
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("MMMM d", Locale.ENGLISH);

    private final AppCompatActivity activity;
    private final FrameLayout activityRoot;
    private final SharedPreferences sharedPreferences;
    private final View yesterdayAverageCard;
    private final View todayAverageCard;
    private final View tomorrowAverageCard;
    private final OnBackPressedCallback backCallback = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackStarted(@NonNull BackEventCompat backEvent) {
            startPredictiveBack(backEvent);
        }

        @Override
        public void handleOnBackProgressed(@NonNull BackEventCompat backEvent) {
            updatePredictiveBack(backEvent);
        }

        @Override
        public void handleOnBackCancelled() {
            cancelPredictiveBack();
        }

        @Override
        public void handleOnBackPressed() {
            if (predictiveBackActive) {
                commitPredictiveBack();
            } else {
                dismiss(true);
            }
        }
    };

    private View overlay;
    private View activeSourceCard;
    private int activeDayOffset = NO_RESTORE;
    private int activeIncrementMinutes = WidgetPreferences.INCREMENT_60_MINUTES;
    private int pendingDayOffset = NO_RESTORE;
    private int pendingIncrementMinutes = WidgetPreferences.INCREMENT_60_MINUTES;
    private int pendingScrollY;
    private boolean predictiveBackActive;

    AverageDetailsController(AppCompatActivity activity,
                             FrameLayout activityRoot,
                             SharedPreferences sharedPreferences,
                             View yesterdayAverageCard,
                             View todayAverageCard,
                             View tomorrowAverageCard,
                             Bundle savedInstanceState) {
        this.activity = activity;
        this.activityRoot = activityRoot;
        this.sharedPreferences = sharedPreferences;
        this.yesterdayAverageCard = yesterdayAverageCard;
        this.todayAverageCard = todayAverageCard;
        this.tomorrowAverageCard = tomorrowAverageCard;
        restorePendingState(savedInstanceState);
    }

    OnBackPressedCallback getBackCallback() {
        return backCallback;
    }

    void setupCardDialogs() {
        yesterdayAverageCard.setOnClickListener(
                v -> show(AveragePriceSummaryResolver.DAY_YESTERDAY, v)
        );
        todayAverageCard.setOnClickListener(
                v -> show(AveragePriceSummaryResolver.DAY_TODAY, v)
        );
        tomorrowAverageCard.setOnClickListener(
                v -> show(AveragePriceSummaryResolver.DAY_TOMORROW, v)
        );
    }

    void onSaveInstanceState(Bundle outState) {
        if (overlay == null || activeDayOffset == NO_RESTORE) {
            return;
        }
        outState.putInt(STATE_DAY_OFFSET, activeDayOffset);
        outState.putInt(STATE_INCREMENT_MINUTES, activeIncrementMinutes);
        ScrollView scrollView = overlay.findViewById(R.id.average_details_price_scroll);
        outState.putInt(STATE_SCROLL_Y, scrollView != null ? scrollView.getScrollY() : 0);
    }

    void restorePendingDialogIfPossible() {
        if (pendingDayOffset == NO_RESTORE || overlay != null) {
            return;
        }

        View sourceCard = getSourceCard(pendingDayOffset);
        if (sourceCard == null || !sourceCard.isEnabled()) {
            return;
        }

        int dayOffset = pendingDayOffset;
        int incrementMinutes = normalizeIncrement(pendingIncrementMinutes);
        int scrollY = pendingScrollY;
        pendingDayOffset = NO_RESTORE;
        pendingIncrementMinutes = WidgetPreferences.INCREMENT_60_MINUTES;
        pendingScrollY = 0;
        show(dayOffset, sourceCard, false, incrementMinutes, scrollY);
    }

    void dismiss(boolean animate) {
        if (overlay == null) {
            return;
        }

        predictiveBackActive = false;
        View currentOverlay = overlay;
        View sourceCard = activeSourceCard;
        View detailsContainer = currentOverlay.findViewById(R.id.average_details_container);
        backCallback.setEnabled(false);

        if (!animate || sourceCard == null || !sourceCard.isAttachedToWindow()) {
            removeOverlay(sourceCard);
            return;
        }

        currentOverlay.findViewById(R.id.average_details_scrim)
                .animate()
                .alpha(0f)
                .setDuration(SCRIM_DURATION_MS)
                .start();

        MaterialContainerTransform transform = createContainerTransform(detailsContainer, sourceCard);
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
                removeOverlay(sourceCard);
            }
        });

        TransitionManager.beginDelayedTransition(activityRoot, transform);
        detailsContainer.setVisibility(View.INVISIBLE);
        sourceCard.setVisibility(View.VISIBLE);
    }

    private void restorePendingState(Bundle savedInstanceState) {
        if (savedInstanceState == null || !savedInstanceState.containsKey(STATE_DAY_OFFSET)) {
            return;
        }
        pendingDayOffset = savedInstanceState.getInt(STATE_DAY_OFFSET, NO_RESTORE);
        pendingIncrementMinutes = savedInstanceState.getInt(
                STATE_INCREMENT_MINUTES,
                WidgetPreferences.INCREMENT_60_MINUTES
        );
        pendingScrollY = savedInstanceState.getInt(STATE_SCROLL_Y, 0);
    }

    private void show(int dayOffset, View sourceCard) {
        show(dayOffset, sourceCard, true, WidgetPreferences.INCREMENT_60_MINUTES, 0);
    }

    private void show(int dayOffset,
                      View sourceCard,
                      boolean animate,
                      int initialIncrementMinutes,
                      int initialScrollY) {
        if (overlay != null || sourceCard == null) {
            return;
        }

        AveragePriceSummaryResolver.DaySummary details = buildDayDetails(dayOffset);
        if (!details.summary.hasData()) {
            return;
        }
        View currentOverlay = activity.getLayoutInflater()
                .inflate(R.layout.dialog_average_details, activityRoot, false);
        View scrim = currentOverlay.findViewById(R.id.average_details_scrim);
        View detailsContainer = currentOverlay.findViewById(R.id.average_details_container);

        overlay = currentOverlay;
        activeSourceCard = sourceCard;
        activeDayOffset = dayOffset;
        activeIncrementMinutes = normalizeIncrement(initialIncrementMinutes);

        bindDialog(currentOverlay, details, activeIncrementMinutes, initialScrollY);
        scrim.setOnClickListener(v -> dismiss(true));

        activityRoot.addView(currentOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        currentOverlay.setVisibility(View.VISIBLE);
        backCallback.setEnabled(true);
        currentOverlay.requestFocus();

        activityRoot.post(() -> {
            applyContainerSize(detailsContainer);
            if (animate) {
                startEnterTransition(sourceCard, currentOverlay, detailsContainer);
            } else {
                scrim.setAlpha(1f);
                sourceCard.setVisibility(View.INVISIBLE);
                detailsContainer.setVisibility(View.VISIBLE);
            }
        });
    }

    private void startPredictiveBack(@NonNull BackEventCompat backEvent) {
        if (overlay == null) {
            return;
        }

        predictiveBackActive = true;
        View detailsContainer = overlay.findViewById(R.id.average_details_container);
        View scrim = overlay.findViewById(R.id.average_details_scrim);
        detailsContainer.animate().cancel();
        scrim.animate().cancel();
        updatePredictiveBack(backEvent);
    }

    private void updatePredictiveBack(@NonNull BackEventCompat backEvent) {
        if (overlay == null) {
            return;
        }

        if (!predictiveBackActive) {
            startPredictiveBack(backEvent);
            return;
        }

        float clampedProgress = clamp(backEvent.getProgress());
        View detailsContainer = overlay.findViewById(R.id.average_details_container);
        View scrim = overlay.findViewById(R.id.average_details_scrim);
        applyPredictiveBackProgress(
                detailsContainer,
                scrim,
                clampedProgress,
                backEvent.getSwipeEdge(),
                backEvent.getTouchY()
        );
    }

    private void cancelPredictiveBack() {
        if (!predictiveBackActive || overlay == null) {
            return;
        }

        predictiveBackActive = false;
        View detailsContainer = overlay.findViewById(R.id.average_details_container);
        View scrim = overlay.findViewById(R.id.average_details_scrim);
        detailsContainer.animate()
                .translationX(0f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(TRANSITION_DURATION_MS)
                .setListener(null)
                .start();
        scrim.animate()
                .alpha(1f)
                .setDuration(TRANSITION_DURATION_MS)
                .start();
    }

    private void commitPredictiveBack() {
        if (overlay == null) {
            return;
        }

        predictiveBackActive = false;
        View detailsContainer = overlay.findViewById(R.id.average_details_container);
        View scrim = overlay.findViewById(R.id.average_details_scrim);
        detailsContainer.animate().cancel();
        scrim.animate().cancel();
        dismiss(true);
    }

    private void applyPredictiveBackProgress(View detailsContainer,
                                             View scrim,
                                             float progress,
                                             int swipeEdge,
                                             float touchY) {
        float easedProgress = 1f - ((1f - progress) * (1f - progress));
        float scale = 1f - ((1f - PREDICTIVE_BACK_MIN_SCALE) * easedProgress);
        float edgeDirection = swipeEdge == BackEventCompat.EDGE_RIGHT ? -1f : 1f;

        detailsContainer.setPivotX(edgeDirection > 0f ? 0f : detailsContainer.getWidth());
        detailsContainer.setPivotY(touchY > 0f
                ? Math.max(0f, Math.min(
                        detailsContainer.getHeight(),
                        touchY - detailsContainer.getTop()
                ))
                : detailsContainer.getHeight() / 2f);
        detailsContainer.setScaleX(scale);
        detailsContainer.setScaleY(scale);
        detailsContainer.setTranslationX(edgeDirection
                * dpToPx(PREDICTIVE_BACK_TRANSLATION_X_DP)
                * easedProgress);
        detailsContainer.setTranslationY(dpToPx(PREDICTIVE_BACK_TRANSLATION_Y_DP)
                * easedProgress);
        scrim.setAlpha(1f - ((1f - PREDICTIVE_BACK_MIN_SCRIM_ALPHA) * easedProgress));
    }

    private float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private void bindDialog(View currentOverlay,
                            AveragePriceSummaryResolver.DaySummary details,
                            int initialIncrementMinutes,
                            int initialScrollY) {
        ((TextView) currentOverlay.findViewById(R.id.average_details_title))
                .setText(details.titleResId);
        ((TextView) currentOverlay.findViewById(R.id.average_details_date))
                .setText(formatDate(details.date));
        ((TextView) currentOverlay.findViewById(R.id.average_details_average_label))
                .setText(R.string.average_label);

        TextView valueView = currentOverlay.findViewById(R.id.average_details_value);
        TextView unitView = currentOverlay.findViewById(R.id.average_details_unit);
        TextView minPriceView = currentOverlay.findViewById(R.id.average_details_min_price);
        TextView minTimeView = currentOverlay.findViewById(R.id.average_details_min_time);
        TextView maxPriceView = currentOverlay.findViewById(R.id.average_details_max_price);
        TextView maxTimeView = currentOverlay.findViewById(R.id.average_details_max_time);
        String unitText = PriceDisplayUtils.getUnitText(details.countryCode, sharedPreferences);
        valueView.setText(PriceDisplayUtils.formatPrice(
                details.summary.average(),
                details.countryCode,
                sharedPreferences
        ));
        unitView.setText(unitText);
        bindExtreme(minPriceView, minTimeView, details.minEntry, details, unitText);
        bindExtreme(maxPriceView, maxTimeView, details.maxEntry, details, unitText);
        bindExtremeActions(currentOverlay, details);

        ChipGroup chipGroup = currentOverlay.findViewById(R.id.average_details_density_chip_group);
        configureChipAnimation(chipGroup);
        chipGroup.check(getChipForIncrement(initialIncrementMinutes));
        chipGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == View.NO_ID) {
                return;
            }
            activeIncrementMinutes = getIncrementForChip(checkedId);
            renderPriceRows(currentOverlay, details, unitText, activeIncrementMinutes);
        });
        renderPriceRows(currentOverlay, details, unitText, initialIncrementMinutes);
        if (initialScrollY > 0) {
            ScrollView scrollView = currentOverlay.findViewById(R.id.average_details_price_scroll);
            scrollView.post(() -> scrollView.scrollTo(0, initialScrollY));
        }
    }

    private void bindExtreme(TextView priceView,
                             TextView timeView,
                             PriceFetcher.PriceEntry entry,
                             AveragePriceSummaryResolver.DaySummary details,
                             String unitText) {
        String priceText = PriceDisplayUtils.formatPrice(
                entry.pricePerKwh,
                details.countryCode,
                sharedPreferences
        );
        String displayText = activity.getString(
                R.string.current_price_details_value_exact,
                priceText,
                unitText
        );
        SpannableString styledText = new SpannableString(displayText);
        int unitStart = Math.max(0, displayText.length() - unitText.length());
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
        timeView.setText(formatTimeRange(entry, details.zoneId));
    }

    private String formatDate(LocalDate date) {
        return date.format(DATE_FORMATTER);
    }

    private void configureChipAnimation(ChipGroup chipGroup) {
        LayoutTransition layoutTransition = new LayoutTransition();
        layoutTransition.enableTransitionType(LayoutTransition.CHANGING);
        layoutTransition.setDuration(LayoutTransition.APPEARING, 120L);
        layoutTransition.setDuration(LayoutTransition.DISAPPEARING, 120L);
        layoutTransition.setDuration(LayoutTransition.CHANGE_APPEARING, 180L);
        layoutTransition.setDuration(LayoutTransition.CHANGE_DISAPPEARING, 180L);
        layoutTransition.setDuration(LayoutTransition.CHANGING, 180L);
        chipGroup.setLayoutTransition(layoutTransition);
    }

    private void renderPriceRows(View currentOverlay,
                                 AveragePriceSummaryResolver.DaySummary details,
                                 String unitText,
                                 int incrementMinutes) {
        LinearLayout rows = currentOverlay.findViewById(R.id.average_details_price_rows);
        ScrollView scrollView = currentOverlay.findViewById(R.id.average_details_price_scroll);
        rows.removeAllViews();

        List<PriceFetcher.PriceEntry> displayEntries = getDisplayEntries(
                details.entries,
                incrementMinutes
        );

        for (int i = 0; i < displayEntries.size(); i++) {
            if (i > 0) {
                addRowDivider(rows);
            }
            PriceFetcher.PriceEntry entry = displayEntries.get(i);
            View row = activity.getLayoutInflater()
                    .inflate(R.layout.list_item_average_price, rows, false);
            row.setTag(entry);
            TextView timeView = row.findViewById(R.id.average_details_price_row_time);
            TextView priceView = row.findViewById(R.id.average_details_price_row_value);
            timeView.setText(formatTimeRange(entry, details.zoneId));
            priceView.setText(activity.getString(
                    R.string.current_price_details_value_exact,
                    PriceDisplayUtils.formatPrice(entry.pricePerKwh, details.countryCode, sharedPreferences),
                    unitText
            ));
            rows.addView(row);
        }
        scrollView.scrollTo(0, 0);
    }

    private void bindExtremeActions(View currentOverlay,
                                    AveragePriceSummaryResolver.DaySummary details) {
        bindExtremeAction(
                currentOverlay.findViewById(R.id.average_details_min),
                currentOverlay,
                details.minEntry
        );
        bindExtremeAction(
                currentOverlay.findViewById(R.id.average_details_max),
                currentOverlay,
                details.maxEntry
        );
    }

    private void bindExtremeAction(View container,
                                   View currentOverlay,
                                   PriceFetcher.PriceEntry entry) {
        boolean enabled = entry != null;
        container.setEnabled(enabled);
        container.setClickable(enabled);
        container.setFocusable(enabled);
        container.setOnClickListener(enabled
                ? v -> scrollToEntry(currentOverlay, entry)
                : null);
    }

    private void scrollToEntry(View currentOverlay, PriceFetcher.PriceEntry entry) {
        ScrollView scrollView = currentOverlay.findViewById(R.id.average_details_price_scroll);
        LinearLayout rows = currentOverlay.findViewById(R.id.average_details_price_rows);
        View targetRow = findRow(rows, entry);
        if (targetRow == null) {
            return;
        }

        scrollView.post(() -> {
            int targetScrollY = targetRow.getTop()
                    - ((scrollView.getHeight() - targetRow.getHeight()) / 2);
            int maxScrollY = Math.max(0, rows.getHeight() - scrollView.getHeight());
            targetScrollY = Math.max(0, Math.min(targetScrollY, maxScrollY));
            scrollView.smoothScrollTo(0, targetScrollY);
            targetRow.postDelayed(
                    () -> highlightRow(targetRow),
                    ROW_HIGHLIGHT_DELAY_MS
            );
        });
    }

    private View findRow(LinearLayout rows, PriceFetcher.PriceEntry targetEntry) {
        for (int i = 0; i < rows.getChildCount(); i++) {
            View child = rows.getChildAt(i);
            Object tag = child.getTag();
            if (tag instanceof PriceFetcher.PriceEntry
                    && containsEntry((PriceFetcher.PriceEntry) tag, targetEntry)) {
                return child;
            }
        }
        return null;
    }

    private boolean containsEntry(PriceFetcher.PriceEntry displayEntry,
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

    private void highlightRow(View row) {
        Drawable originalForeground = row.getForeground();
        int highlightColor = MaterialColors.getColor(
                row,
                com.google.android.material.R.attr.colorSecondaryContainer
        );
        GradientDrawable highlight = new GradientDrawable();
        highlight.setColor(withAlpha(highlightColor, ROW_HIGHLIGHT_ALPHA));
        highlight.setCornerRadius(dpToPx(12));
        row.setForeground(highlight);

        ValueAnimator animator = ValueAnimator.ofInt(ROW_HIGHLIGHT_ALPHA, 0);
        animator.setStartDelay(ROW_HIGHLIGHT_HOLD_MS);
        animator.setDuration(ROW_HIGHLIGHT_FADE_MS);
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

    private void addRowDivider(LinearLayout rows) {
        View divider = new View(activity);
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

    private List<PriceFetcher.PriceEntry> getDisplayEntries(List<PriceFetcher.PriceEntry> entries,
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

    private int getIncrementForChip(int checkedId) {
        if (checkedId == R.id.average_details_density_30_chip) {
            return WidgetPreferences.INCREMENT_30_MINUTES;
        }
        if (checkedId == R.id.average_details_density_hourly_chip) {
            return WidgetPreferences.INCREMENT_60_MINUTES;
        }
        return WidgetPreferences.INCREMENT_15_MINUTES;
    }

    private int getChipForIncrement(int incrementMinutes) {
        if (incrementMinutes == WidgetPreferences.INCREMENT_15_MINUTES) {
            return R.id.average_details_density_15_chip;
        }
        if (incrementMinutes == WidgetPreferences.INCREMENT_30_MINUTES) {
            return R.id.average_details_density_30_chip;
        }
        return R.id.average_details_density_hourly_chip;
    }

    private int normalizeIncrement(int incrementMinutes) {
        if (incrementMinutes == WidgetPreferences.INCREMENT_15_MINUTES
                || incrementMinutes == WidgetPreferences.INCREMENT_30_MINUTES
                || incrementMinutes == WidgetPreferences.INCREMENT_60_MINUTES) {
            return incrementMinutes;
        }
        return WidgetPreferences.INCREMENT_60_MINUTES;
    }

    private View getSourceCard(int dayOffset) {
        if (dayOffset == AveragePriceSummaryResolver.DAY_YESTERDAY) {
            return yesterdayAverageCard;
        }
        if (dayOffset == AveragePriceSummaryResolver.DAY_TOMORROW) {
            return tomorrowAverageCard;
        }
        if (dayOffset == AveragePriceSummaryResolver.DAY_TODAY) {
            return todayAverageCard;
        }
        return null;
    }

    private String formatTimeRange(PriceFetcher.PriceEntry entry, ZoneId zoneId) {
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

    private AveragePriceSummaryResolver.DaySummary buildDayDetails(int dayOffset) {
        String countryCode = PriceRepository.getSelectedCountryCode(activity, sharedPreferences);
        List<PriceFetcher.PriceEntry> allEntries =
                CurrentPriceResolver.getAdjustedEntries(activity, sharedPreferences);
        return AveragePriceSummaryResolver.resolveDay(allEntries, countryCode, dayOffset);
    }

    private void applyContainerSize(View detailsContainer) {
        int rootWidth = activityRoot.getWidth();
        if (rootWidth <= 0) {
            rootWidth = activity.getResources().getDisplayMetrics().widthPixels;
        }
        int rootHeight = activityRoot.getHeight();
        if (rootHeight <= 0) {
            rootHeight = activity.getResources().getDisplayMetrics().heightPixels;
        }
        int availableWidth = Math.max(1, rootWidth - dpToPx(SIDE_MARGIN_DP * 2));
        int availableHeight = Math.max(1, rootHeight - dpToPx(SIDE_MARGIN_DP * 2));
        int targetWidth = Math.min(availableWidth, dpToPx(MAX_WIDTH_DP));
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

    private void startEnterTransition(View sourceCard, View currentOverlay, View detailsContainer) {
        View scrim = currentOverlay.findViewById(R.id.average_details_scrim);
        scrim.animate()
                .alpha(1f)
                .setDuration(SCRIM_DURATION_MS)
                .start();

        MaterialContainerTransform transform = createContainerTransform(sourceCard, detailsContainer);
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

    private MaterialContainerTransform createContainerTransform(View startView, View endView) {
        MaterialContainerTransform transform = new MaterialContainerTransform();
        transform.setStartView(startView);
        transform.setEndView(endView);
        transform.setDrawingViewId(R.id.activity_root);
        transform.setDuration(TRANSITION_DURATION_MS);
        transform.setScrimColor(Color.TRANSPARENT);
        transform.setFadeMode(MaterialContainerTransform.FADE_MODE_THROUGH);
        transform.setFitMode(MaterialContainerTransform.FIT_MODE_AUTO);
        transform.setPathMotion(new MaterialArcMotion());
        transform.setElevationShadowEnabled(true);
        transform.setStartShapeAppearanceModel(createContainerShape(startView));
        transform.setEndShapeAppearanceModel(createContainerShape(endView));
        transform.setScaleMaskProgressThresholds(createContainerProgressThresholds());
        transform.setShapeMaskProgressThresholds(createContainerProgressThresholds());
        transform.setAllContainerColors(getContainerColor(endView));
        transform.setStartElevation(getContainerElevation(startView));
        transform.setEndElevation(getContainerElevation(endView));
        return transform;
    }

    private MaterialContainerTransform.ProgressThresholds createContainerProgressThresholds() {
        return new MaterialContainerTransform.ProgressThresholds(0f, 1f);
    }

    private ShapeAppearanceModel createContainerShape(View view) {
        int cornerRadiusDp = view.getId() == R.id.average_details_container
                ? DETAILS_CARD_CORNER_RADIUS_DP
                : SOURCE_CARD_CORNER_RADIUS_DP;
        return ShapeAppearanceModel.builder()
                .setAllCornerSizes(dpToPx(cornerRadiusDp))
                .build();
    }

    private float getContainerElevation(View view) {
        if (view.getId() == R.id.average_details_container) {
            return dpToPx(CONTAINER_TRANSFORM_ELEVATION_DP);
        }
        if (view instanceof MaterialCardView) {
            return ((MaterialCardView) view).getCardElevation();
        }
        return view.getElevation();
    }

    private int getContainerColor(View view) {
        if (view instanceof MaterialCardView) {
            return ((MaterialCardView) view).getCardBackgroundColor().getDefaultColor();
        }
        return MaterialColors.getColor(
                view,
                com.google.android.material.R.attr.colorSurfaceContainerHigh
        );
    }

    private void removeOverlay(View sourceCard) {
        if (sourceCard != null) {
            sourceCard.setVisibility(View.VISIBLE);
        }
        if (overlay != null) {
            overlay.animate().cancel();
            activityRoot.removeView(overlay);
        }
        overlay = null;
        activeSourceCard = null;
        activeDayOffset = NO_RESTORE;
        activeIncrementMinutes = WidgetPreferences.INCREMENT_60_MINUTES;
        backCallback.setEnabled(false);
    }

    private int dpToPx(int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                activity.getResources().getDisplayMetrics()
        ));
    }
}
