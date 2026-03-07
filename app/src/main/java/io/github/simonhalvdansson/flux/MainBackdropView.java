package io.github.simonhalvdansson.flux;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.core.content.ContextCompat;

public class MainBackdropView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);

    private LinearGradient baseGradient;
    private Blob[] blobs = new Blob[0];
    private int veilColor;

    public MainBackdropView(Context context) {
        super(context);
        init();
    }

    public MainBackdropView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MainBackdropView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        if (!isInEditMode()) {
            float blurRadius = dpToPx(96f);
            setRenderEffect(RenderEffect.createBlurEffect(
                    blurRadius,
                    blurRadius,
                    Shader.TileMode.CLAMP
            ));
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rebuildShaders(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (baseGradient == null) {
            return;
        }

        paint.setShader(baseGradient);
        canvas.drawRect(0f, 0f, getWidth(), getHeight(), paint);

        for (Blob blob : blobs) {
            paint.setShader(blob.gradient);
            canvas.drawCircle(blob.centerX, blob.centerY, blob.radius, paint);
        }

        paint.setShader(null);
        paint.setColor(veilColor);
        canvas.drawRect(0f, 0f, getWidth(), getHeight(), paint);
    }

    private void rebuildShaders(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }

        boolean isNightMode = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        float alphaMultiplier = isNightMode ? 0.58f : 1f;

        int baseTopColor = getColor(R.color.main_backdrop_base_top);
        int baseBottomColor = getColor(R.color.main_backdrop_base_bottom);
        int blueColor = getColor(R.color.main_backdrop_blob_blue);
        int lilacColor = getColor(R.color.main_backdrop_blob_lilac);
        int pinkColor = getColor(R.color.main_backdrop_blob_pink);
        int roseColor = getColor(R.color.main_backdrop_blob_rose);
        int mistColor = getColor(R.color.main_backdrop_blob_mist);
        veilColor = getColor(R.color.main_backdrop_veil);

        baseGradient = new LinearGradient(
                0f,
                0f,
                width,
                height,
                baseTopColor,
                baseBottomColor,
                Shader.TileMode.CLAMP
        );

        float maxDimension = Math.max(width, height);
        blobs = new Blob[] {
                createBlob(width * -0.06f, height * 0.92f, maxDimension * 0.42f, blueColor, 0.96f * alphaMultiplier, 0.46f * alphaMultiplier),
                createBlob(width * 0.42f, height * 0.98f, maxDimension * 0.33f, lilacColor, 0.58f * alphaMultiplier, 0.24f * alphaMultiplier),
                createBlob(width * 0.80f, height * 0.28f, maxDimension * 0.35f, pinkColor, 0.72f * alphaMultiplier, 0.28f * alphaMultiplier),
                createBlob(width * 0.56f, height * 0.46f, maxDimension * 0.28f, roseColor, 0.34f * alphaMultiplier, 0.14f * alphaMultiplier),
                createBlob(width * 0.16f, height * 0.18f, maxDimension * 0.26f, mistColor, 0.24f * alphaMultiplier, 0.10f * alphaMultiplier)
        };
    }

    private Blob createBlob(float centerX, float centerY, float radius, int color, float centerAlpha, float midAlpha) {
        RadialGradient gradient = new RadialGradient(
                centerX,
                centerY,
                radius,
                new int[] {
                        withAlpha(color, centerAlpha),
                        withAlpha(color, midAlpha),
                        Color.TRANSPARENT
                },
                new float[] {0f, 0.58f, 1f},
                Shader.TileMode.CLAMP
        );
        return new Blob(centerX, centerY, radius, gradient);
    }

    private int getColor(int colorRes) {
        return ContextCompat.getColor(getContext(), colorRes);
    }

    private int withAlpha(int color, float alphaScale) {
        int alpha = Math.round(Color.alpha(color) * alphaScale);
        alpha = Math.max(0, Math.min(255, alpha));
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private float dpToPx(float dp) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                getResources().getDisplayMetrics()
        );
    }

    private static final class Blob {
        final float centerX;
        final float centerY;
        final float radius;
        final RadialGradient gradient;

        Blob(float centerX, float centerY, float radius, RadialGradient gradient) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.radius = radius;
            this.gradient = gradient;
        }
    }
}