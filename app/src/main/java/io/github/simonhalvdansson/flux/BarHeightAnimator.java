package io.github.simonhalvdansson.flux;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.interpolator.view.animation.LinearOutSlowInInterpolator;

import java.util.ArrayList;
import java.util.List;

final class BarHeightAnimator {
    private final View root;
    private final int[] barIds;
    private AnimatorSet animator;

    BarHeightAnimator(View root, int[] barIds) {
        this.root = root;
        this.barIds = barIds;
    }

    void animate(int[] startHeightsPx,
                 int[] targetHeightsPx,
                 boolean[] targetVisibilities,
                 long durationMs,
                 long staggerMs) {
        cancel();

        List<Animator> animators = new ArrayList<>();
        LinearOutSlowInInterpolator interpolator = new LinearOutSlowInInterpolator();

        for (int i = 0; i < barIds.length; i++) {
            ImageView bar = findBar(i);
            if (!targetVisibilities[i] && startHeightsPx[i] <= 0) {
                bar.setVisibility(View.INVISIBLE);
                setBarHeight(bar, 0);
                continue;
            }

            bar.setVisibility(View.VISIBLE);
            setBarHeight(bar, startHeightsPx[i]);
            ValueAnimator heightAnimator = ValueAnimator.ofInt(startHeightsPx[i], targetHeightsPx[i]);
            heightAnimator.setDuration(durationMs);
            heightAnimator.setStartDelay(i * staggerMs);
            heightAnimator.setInterpolator(interpolator);
            heightAnimator.addUpdateListener(valueAnimator ->
                    setBarHeight(bar, (int) valueAnimator.getAnimatedValue()));
            animators.add(heightAnimator);
        }

        AnimatorSet newAnimator = new AnimatorSet();
        animator = newAnimator;
        newAnimator.playTogether(animators);
        newAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean canceled;

            @Override
            public void onAnimationCancel(Animator animation) {
                canceled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!canceled) {
                    applyState(targetHeightsPx, targetVisibilities);
                }
                if (animator == newAnimator) {
                    animator = null;
                }
            }
        });
        newAnimator.start();
    }

    void animateUpdates(int[] targetHeightsPx,
                        boolean[] targetVisibilities,
                        long durationMs) {
        cancel();
        int[] currentHeightsPx = getCurrentHeights();
        if (!hasStateChanges(currentHeightsPx, targetHeightsPx, targetVisibilities)) {
            applyState(targetHeightsPx, targetVisibilities);
            return;
        }
        animate(currentHeightsPx, targetHeightsPx, targetVisibilities, durationMs, 0L);
    }

    int[] getCurrentHeights() {
        int[] heightsPx = new int[barIds.length];
        for (int i = 0; i < barIds.length; i++) {
            ViewGroup.LayoutParams params = findBar(i).getLayoutParams();
            heightsPx[i] = params != null ? params.height : 0;
        }
        return heightsPx;
    }

    void applyState(int[] targetHeightsPx, boolean[] targetVisibilities) {
        for (int i = 0; i < barIds.length; i++) {
            ImageView bar = findBar(i);
            setBarHeight(bar, targetHeightsPx[i]);
            bar.setVisibility(targetVisibilities[i] ? View.VISIBLE : View.INVISIBLE);
        }
    }

    void cancel() {
        if (animator != null) {
            AnimatorSet runningAnimator = animator;
            animator = null;
            runningAnimator.cancel();
        }
    }

    private boolean hasStateChanges(int[] currentHeightsPx,
                                    int[] targetHeightsPx,
                                    boolean[] targetVisibilities) {
        for (int i = 0; i < barIds.length; i++) {
            int targetVisibility = targetVisibilities[i] ? View.VISIBLE : View.INVISIBLE;
            if (currentHeightsPx[i] != targetHeightsPx[i]
                    || findBar(i).getVisibility() != targetVisibility) {
                return true;
            }
        }
        return false;
    }

    private ImageView findBar(int index) {
        return root.findViewById(barIds[index]);
    }

    private static void setBarHeight(ImageView bar, int heightPx) {
        ViewGroup.LayoutParams params = bar.getLayoutParams();
        if (params.height != heightPx) {
            params.height = heightPx;
            bar.setLayoutParams(params);
        }
    }
}
