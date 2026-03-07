package io.github.simonhalvdansson.flux;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.core.content.ContextCompat;

import java.util.Random;

public class NoiseOverlayView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private Bitmap noiseTile;

    public NoiseOverlayView(Context context) {
        super(context);
        init();
    }

    public NoiseOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public NoiseOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint.setFilterBitmap(true);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rebuildNoise(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (paint.getShader() == null) {
            return;
        }
        canvas.drawRect(0f, 0f, getWidth(), getHeight(), paint);
    }

    private void rebuildNoise(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }

        boolean isNightMode = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        int tileMinDp = isNightMode ? 176 : 96;
        int tileMaxDp = isNightMode ? 256 : 156;
        int tileSize = Math.max(
                Math.round(dpToPx(tileMinDp)),
                Math.min(Math.max(width, height) / 3, Math.round(dpToPx(tileMaxDp)))
        );

        int lightColor = getColor(R.color.main_backdrop_noise_light);
        int darkColor = getColor(R.color.main_backdrop_noise_dark);
        int lightChance = isNightMode ? 10 : 34;
        int darkChance = isNightMode ? 18 : 54;
        float lightBaseAlpha = isNightMode ? 0.025f : 0.10f;
        float lightAlphaRange = isNightMode ? 0.07f : 0.20f;
        float darkBaseAlpha = isNightMode ? 0.015f : 0.06f;
        float darkAlphaRange = isNightMode ? 0.04f : 0.14f;

        noiseTile = Bitmap.createBitmap(tileSize, tileSize, Bitmap.Config.ARGB_8888);
        Random random = new Random((((long) width) << 32) ^ height);

        for (int y = 0; y < tileSize; y++) {
            for (int x = 0; x < tileSize; x++) {
                int sample = random.nextInt(100);
                int color = Color.TRANSPARENT;
                if (sample < lightChance) {
                    color = withAlpha(lightColor, lightBaseAlpha + (random.nextFloat() * lightAlphaRange));
                } else if (sample < darkChance) {
                    color = withAlpha(darkColor, darkBaseAlpha + (random.nextFloat() * darkAlphaRange));
                }
                noiseTile.setPixel(x, y, color);
            }
        }

        paint.setShader(new BitmapShader(noiseTile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
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
}