package io.github.simonhalvdansson.flux;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.TypedValue;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

public class GraphUtils {

    private static final float LINE_WIDTH_DP = 4f;
    private static final float STEP_LINE_WIDTH_DP = 2.5f;
    private static final float CIRCLE_RADIUS_DP = 6f;
    private static final float PAD_SLOP_DP = 1f;
    private static final float EDGE_FADE_MINUTES = 38f;
    private static final float VERTICAL_EASE_POWER = 1.3f;
    private static final float HORIZONTAL_EASE_POWER = 1.6f;
    private static final float GRID_LINE_WIDTH_DP = 1.5f;
    private static final float GRID_FADE_POWER = 1.6f;
    private static final float GRID_ALPHA = 0.25f;
    private static final float SELECTION_DOT_RADIUS_DP = 5f;
    private static final float SELECTION_DOT_HALO_RADIUS_DP = 9f;

    private static float catmullRom(float p0, float p1, float p2, float p3, float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        return 0.5f * ((2f * p1)
                + (-p0 + p2) * t
                + (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2
                + (-p0 + 3f * p1 - 3f * p2 + p3) * t3);
    }

    public static Bitmap createLineGraphBitmapCubic(
            Context context,
            List<PriceFetcher.PriceEntry> data,
            double maxPrice,
            int widthPx,
            int heightPx,
            ZonedDateTime now) {
        return createLineGraphBitmapCubic(context, data, maxPrice, widthPx, heightPx, now, Float.NaN);
    }

    public static Bitmap createLineGraphBitmapCubic(
            Context context,
            List<PriceFetcher.PriceEntry> data,
            double maxPrice,
            int widthPx,
            int heightPx,
            ZonedDateTime now,
            float selectedFraction) {

        Bitmap bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        if (data == null || data.size() < 2 || maxPrice <= 0) {
            return bitmap;
        }

        int colorPast = ContextCompat.getColor(context, android.R.color.system_accent3_200);
        int colorCurrent = ContextCompat.getColor(context, android.R.color.system_accent3_700);
        int colorFuture = ContextCompat.getColor(context, android.R.color.system_accent3_500);
        int fadeBase = ContextCompat.getColor(context, android.R.color.system_accent3_400);

        android.util.DisplayMetrics dm = context.getResources().getDisplayMetrics();
        float lineWidthPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, LINE_WIDTH_DP, dm);
        float circleRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, CIRCLE_RADIUS_DP, dm);
        float padSlopPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, PAD_SLOP_DP, dm);
        float selectionDotRadius = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                SELECTION_DOT_RADIUS_DP,
                dm
        );
        float selectionHaloRadius = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                SELECTION_DOT_HALO_RADIUS_DP,
                dm
        );

        final int n = data.size();
        float[] y = new float[n];
        for (int i = 0; i < n; i++) {
            y[i] = (float) data.get(i).pricePerKwh;
        }

        long stepMinutes = 60;
        if (n > 1) {
            long minStep = Long.MAX_VALUE;
            for (int i = 0; i < n - 1; i++) {
                long minutes = Duration.between(data.get(i).startTime, data.get(i + 1).startTime).toMinutes();
                if (minutes > 0 && minutes < minStep) {
                    minStep = minutes;
                }
            }
            if (minStep != Long.MAX_VALUE) {
                stepMinutes = Math.max(1, minStep);
            }
        }

        final int subdiv = 8;
        final int totalPts = (n - 1) * subdiv + 1;

        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(lineWidthPx);
        line.setStrokeCap(Paint.Cap.ROUND);

        float padX = lineWidthPx / 2f + padSlopPx;
        float padTop = Math.max(lineWidthPx / 2f + padSlopPx, circleRadius + padSlopPx);
        float padBottom = lineWidthPx / 2f + padSlopPx;

        float usableW = Math.max(1f, widthPx - (2f * padX));
        float usableH = Math.max(1f, heightPx - padTop - padBottom);
        float stepX = usableW / (float) (totalPts - 1);
        float leftX = padX;
        float rightX = padX + ((totalPts - 1) * stepX);
        float bottomY = heightPx - padBottom;

        ZonedDateTime firstStart = data.get(0).startTime.atZoneSameInstant(ZoneId.systemDefault());
        float stepsFromStart = (float) (Duration.between(firstStart, now).toMinutes() / (float) stepMinutes);
        stepsFromStart = Math.max(0f, Math.min(stepsFromStart, n - 1));
        float nowPt = stepsFromStart * subdiv;

        int base = (int) Math.floor(stepsFromStart);
        int i0c = Math.max(0, base - 1);
        int i1c = base;
        int i2c = Math.min(n - 1, base + 1);
        int i3c = Math.min(n - 1, base + 2);
        float tNow = stepsFromStart - base;
        float nowYVal = catmullRom(y[i0c], y[i1c], y[i2c], y[i3c], tNow);
        float nowYClamped = Math.max(0f, Math.min((float) maxPrice, nowYVal));
        float currentX = leftX + (nowPt * stepX);
        float currentY = heightPx - padBottom - ((nowYClamped / (float) maxPrice) * usableH);

        int layerId = canvas.saveLayer(0, 0, widthPx, heightPx, null);

        float y0c = Math.max(0f, Math.min((float) maxPrice, y[0]));
        float prevX = leftX;
        float prevY = heightPx - padBottom - ((y0c / (float) maxPrice) * usableH);
        int ptIndex = 1;

        float[] ySamp = new float[totalPts];
        ySamp[0] = prevY;

        for (int i = 0; i < n - 1; i++) {
            int i0 = Math.max(0, i - 1);
            int i1 = i;
            int i2 = i + 1;
            int i3 = Math.min(n - 1, i + 2);

            for (int s = 1; s <= subdiv; s++) {
                float t = s / (float) subdiv;
                float yInterp = catmullRom(y[i0], y[i1], y[i2], y[i3], t);
                float yVal = Math.max(0f, Math.min((float) maxPrice, yInterp));
                float x = leftX + (ptIndex * stepX);
                float yPix = heightPx - padBottom - ((yVal / (float) maxPrice) * usableH);

                if (prevX < currentX && x > currentX) {
                    float ratio = (currentX - prevX) / (x - prevX);
                    float splitY = prevY + (ratio * (yPix - prevY));
                    line.setColor(colorPast);
                    canvas.drawLine(prevX, prevY, currentX, splitY, line);
                    line.setColor(colorFuture);
                    canvas.drawLine(currentX, splitY, x, yPix, line);
                } else {
                    line.setColor(x <= currentX ? colorPast : colorFuture);
                    canvas.drawLine(prevX, prevY, x, yPix, line);
                }

                ySamp[ptIndex] = yPix;
                prevX = x;
                prevY = yPix;
                ptIndex++;
            }
        }

        android.graphics.Path maskPath = new android.graphics.Path();
        maskPath.moveTo(leftX, bottomY);
        maskPath.lineTo(leftX, ySamp[0]);
        for (int i = 1; i < totalPts; i++) {
            maskPath.lineTo(leftX + (i * stepX), ySamp[i]);
        }
        maskPath.lineTo(rightX, bottomY);
        maskPath.close();

        final int vStops = 32;
        int[] vColors = new int[vStops];
        float[] vPos = new float[vStops];
        int topAlpha = 140;
        float vPow = Math.max(1f, VERTICAL_EASE_POWER);
        for (int i = 0; i < vStops; i++) {
            float tt = i / (float) (vStops - 1);
            float a = (float) Math.pow(1f - tt, vPow);
            int alpha = Math.round(topAlpha * Math.max(0f, Math.min(1f, a)));
            vColors[i] = ColorUtils.setAlphaComponent(fadeBase, alpha);
            vPos[i] = tt;
        }
        android.graphics.Shader fadeV = new android.graphics.LinearGradient(
                0,
                0,
                0,
                heightPx,
                vColors,
                vPos,
                android.graphics.Shader.TileMode.CLAMP
        );

        Paint fadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fadePaint.setStyle(Paint.Style.FILL);
        fadePaint.setShader(fadeV);

        int clipId = canvas.save();
        canvas.clipPath(maskPath);
        canvas.drawRect(0, 0, widthPx, heightPx, fadePaint);
        canvas.restoreToCount(clipId);

        float gridWidth = GRID_LINE_WIDTH_DP * dm.density;
        Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
        grid.setStyle(Paint.Style.STROKE);
        grid.setStrokeWidth(gridWidth);

        final int gStops = 16;
        int[] gColors = new int[gStops];
        float[] gPos = new float[gStops];
        for (int s = 0; s < gStops; s++) {
            float tt = s / (float) (gStops - 1);
            float a = (float) Math.pow(1f - tt, GRID_FADE_POWER);
            int alpha = Math.round(255f * GRID_ALPHA * a);
            gColors[s] = ColorUtils.setAlphaComponent(colorFuture, alpha);
            gPos[s] = tt;
        }

        int stepsPerHour = Math.max(1, Math.round(60f / (float) stepMinutes));
        int gridStep = Math.max(1, stepsPerHour * 2);
        for (int i = 0; i < n; i += gridStep) {
            float x = leftX + ((i * subdiv) * stepX);
            float yi = Math.max(0f, Math.min((float) maxPrice, y[i]));
            float yTop = bottomY - ((yi / (float) maxPrice) * usableH);
            android.graphics.Shader grad = new android.graphics.LinearGradient(
                    x,
                    bottomY,
                    x,
                    yTop,
                    gColors,
                    gPos,
                    android.graphics.Shader.TileMode.CLAMP
            );
            grid.setShader(grad);
            canvas.drawLine(x, bottomY, x, yTop, grid);
        }

        Paint circle = new Paint(Paint.ANTI_ALIAS_FLAG);
        circle.setStyle(Paint.Style.FILL);
        circle.setColor(colorCurrent);
        canvas.drawCircle(currentX, currentY, circleRadius, circle);

        float edgePts = subdiv * (EDGE_FADE_MINUTES / (float) stepMinutes);
        float edgePx = Math.max(lineWidthPx, edgePts * stepX);
        float frac = Math.max(0f, Math.min(0.45f, edgePx / widthPx));
        final int hStops = 64;
        int[] hColors = new int[hStops];
        float[] hPos = new float[hStops];
        float hPow = Math.max(1f, HORIZONTAL_EASE_POWER);

        for (int i = 0; i < hStops; i++) {
            float u = i / (float) (hStops - 1);
            float alphaF;
            if (u < frac) {
                float t = u / frac;
                alphaF = (float) Math.pow(t, hPow);
            } else if (u > 1f - frac) {
                float t = (1f - u) / frac;
                alphaF = (float) Math.pow(t, hPow);
            } else {
                alphaF = 1f;
            }
            int a = Math.round(255f * Math.max(0f, Math.min(1f, alphaF)));
            hColors[i] = android.graphics.Color.argb(a, 0, 0, 0);
            hPos[i] = u;
        }

        android.graphics.Shader fadeH = new android.graphics.LinearGradient(
                0,
                0,
                widthPx,
                0,
                hColors,
                hPos,
                android.graphics.Shader.TileMode.CLAMP
        );

        Paint mask = new Paint(Paint.ANTI_ALIAS_FLAG);
        mask.setShader(fadeH);
        mask.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN));
        canvas.drawRect(0, 0, widthPx, heightPx, mask);

        canvas.restoreToCount(layerId);

        if (!Float.isNaN(selectedFraction)) {
            float clampedSelectedFraction = Math.max(0f, Math.min(1f, selectedFraction));
            float selectedX = leftX + (usableW * clampedSelectedFraction);
            float selectedPosition = (selectedX - leftX) / stepX;
            int lowerIndex = Math.max(0, Math.min(totalPts - 1, (int) Math.floor(selectedPosition)));
            int upperIndex = Math.max(0, Math.min(totalPts - 1, lowerIndex + 1));
            float segmentFraction = Math.max(0f, Math.min(1f, selectedPosition - lowerIndex));
            float selectedY = ySamp[lowerIndex] + ((ySamp[upperIndex] - ySamp[lowerIndex]) * segmentFraction);
            drawSelectionDot(
                    canvas,
                    selectedX,
                    selectedY,
                    selectionDotRadius,
                    selectionHaloRadius,
                    colorFuture,
                    ColorUtils.setAlphaComponent(colorCurrent, 96)
            );
        }

        return bitmap;
    }

    public static Bitmap createStepLineGraphBitmap(
            Context context,
            List<PriceFetcher.PriceEntry> data,
            double maxPrice,
            int widthPx,
            int heightPx) {
        return createStepLineGraphBitmap(context, data, maxPrice, widthPx, heightPx, Float.NaN);
    }

    public static Bitmap createStepLineGraphBitmap(
            Context context,
            List<PriceFetcher.PriceEntry> data,
            double maxPrice,
            int widthPx,
            int heightPx,
            float selectedFraction) {

        Bitmap bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        if (data == null || data.isEmpty() || maxPrice <= 0) {
            return bitmap;
        }

        android.util.DisplayMetrics dm = context.getResources().getDisplayMetrics();
        float lineWidthPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, STEP_LINE_WIDTH_DP, dm);
        float padX = lineWidthPx / 2f + TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, PAD_SLOP_DP, dm);
        float padY = lineWidthPx / 2f + TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, PAD_SLOP_DP, dm);
        float selectionDotRadius = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                SELECTION_DOT_RADIUS_DP,
                dm
        );
        float selectionHaloRadius = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                SELECTION_DOT_HALO_RADIUS_DP,
                dm
        );

        float usableW = Math.max(1f, widthPx - (2f * padX));
        float usableH = Math.max(1f, heightPx - (2f * padY));
        float leftX = padX;
        float topY = padY;
        float bottomY = heightPx - padY;

        OffsetDateTime windowStart = data.get(0).startTime;
        OffsetDateTime windowEnd = data.get(data.size() - 1).endTime;
        long totalMinutes = Math.max(1L, Duration.between(windowStart, windowEnd).toMinutes());
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());

        int colorPast = ContextCompat.getColor(context, android.R.color.system_accent3_200);
        int colorCurrent = ContextCompat.getColor(context, android.R.color.system_accent3_700);
        int colorFuture = ContextCompat.getColor(context, android.R.color.system_accent3_500);

        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(lineWidthPx);
        line.setStrokeCap(Paint.Cap.BUTT);
        line.setStrokeJoin(Paint.Join.MITER);

        Paint guide = new Paint(Paint.ANTI_ALIAS_FLAG);
        guide.setStyle(Paint.Style.STROKE);
        guide.setStrokeWidth(Math.max(1f, lineWidthPx * 0.55f));
        guide.setAlpha(128);

        float clampedSelectedFraction = Math.max(0f, Math.min(1f, selectedFraction));
        float selectedX = leftX + (usableW * clampedSelectedFraction);
        float selectedY = Float.NaN;

        for (int i = 0; i < data.size(); i++) {
            PriceFetcher.PriceEntry entry = data.get(i);
            float startFraction = Duration.between(windowStart, entry.startTime).toMinutes() / (float) totalMinutes;
            float endFraction = Duration.between(windowStart, entry.endTime).toMinutes() / (float) totalMinutes;
            float startX = leftX + (usableW * startFraction);
            float endX = leftX + (usableW * endFraction);
            float y = bottomY - (((float) entry.pricePerKwh / (float) maxPrice) * usableH);
            y = Math.max(topY, Math.min(bottomY, y));
            ZonedDateTime start = entry.startTime.atZoneSameInstant(ZoneId.systemDefault());
            ZonedDateTime end = entry.endTime.atZoneSameInstant(ZoneId.systemDefault());
            boolean isCurrent = (now.isEqual(start) || now.isAfter(start)) && now.isBefore(end);
            boolean isPast = now.isAfter(end);
            int segmentColor = isCurrent ? colorCurrent : (isPast ? colorPast : colorFuture);

            if (start.getMinute() == 0) {
                guide.setColor(isPast ? colorPast : colorFuture);
                canvas.drawLine(startX, bottomY, startX, y, guide);
            }
            line.setColor(segmentColor);
            canvas.drawLine(startX, y, endX, y, line);

            if (!Float.isNaN(selectedFraction)
                    && selectedFraction >= startFraction
                    && (selectedFraction <= endFraction || i == data.size() - 1)) {
                selectedY = y;
            }

            if (i < data.size() - 1) {
                PriceFetcher.PriceEntry next = data.get(i + 1);
                float nextY = bottomY - (((float) next.pricePerKwh / (float) maxPrice) * usableH);
                nextY = Math.max(topY, Math.min(bottomY, nextY));
                ZonedDateTime nextStart = next.startTime.atZoneSameInstant(ZoneId.systemDefault());
                ZonedDateTime nextEnd = next.endTime.atZoneSameInstant(ZoneId.systemDefault());
                boolean nextIsCurrent = (now.isEqual(nextStart) || now.isAfter(nextStart)) && now.isBefore(nextEnd);
                boolean nextIsPast = now.isAfter(nextEnd);
                line.setColor(nextIsCurrent ? colorCurrent : (nextIsPast ? colorPast : colorFuture));
                canvas.drawLine(endX, y, endX, nextY, line);
            }
        }

        if (!Float.isNaN(selectedY)) {
            drawSelectionDot(
                    canvas,
                    selectedX,
                    selectedY,
                    selectionDotRadius,
                    selectionHaloRadius,
                    colorFuture,
                    ColorUtils.setAlphaComponent(colorCurrent, 96)
            );
        }

        return bitmap;
    }

    private static void drawSelectionDot(Canvas canvas,
                                         float centerX,
                                         float centerY,
                                         float dotRadius,
                                         float haloRadius,
                                         int dotColor,
                                         int haloColor) {
        Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
        halo.setStyle(Paint.Style.FILL);
        halo.setColor(haloColor);
        canvas.drawCircle(centerX, centerY, haloRadius, halo);

        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setStyle(Paint.Style.FILL);
        dot.setColor(dotColor);
        canvas.drawCircle(centerX, centerY, dotRadius, dot);
    }
}
