package io.github.simonhalvdansson.flux;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

import androidx.core.content.ContextCompat;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

public class GraphUtils {

    // Add these globals near the top of GraphUtils (dp-based sizing)
    private static final float LINE_WIDTH_DP   = 4f;
    private static final float STEP_LINE_WIDTH_DP = 2.5f;
    private static final float CIRCLE_RADIUS_DP = 6f;
    private static final float PAD_SLOP_DP      = 1f;

    private static final float EDGE_FADE_MINUTES  = 50f;
    private static final float VERTICAL_EASE_POWER = 1.3f;
    private static final float HORIZONTAL_EASE_POWER = 1.6f;


    // Grid line styling
    private static final float GRID_LINE_WIDTH_DP = 1.5f;
    private static final float GRID_FADE_POWER = 1.6f;
    private static final float GRID_ALPHA = 0.25f;

    private static float catmullRom(float p0, float p1, float p2, float p3, float t) {
        float t2 = t * t, t3 = t2 * t;
        return 0.5f * ((2f * p1)
                + (-p0 + p2) * t
                + (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2
                + (-p0 + 3f * p1 - 3f * p2 + p3) * t3);
    }

    public static Bitmap createLineGraphBitmapCubic(
            Context context, List<PriceFetcher.PriceEntry> data,
            double maxPrice, int widthPx, int heightPx, ZonedDateTime now) {

        Bitmap bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        if (data == null || data.size() < 2 || maxPrice <= 0) return bitmap;

        // colors
        int colorPast    = ContextCompat.getColor(context, android.R.color.system_accent3_200);
        int colorCurrent = ContextCompat.getColor(context, android.R.color.system_accent3_700);
        int colorFuture  = ContextCompat.getColor(context, android.R.color.system_accent3_500);
        int fadeBase     = ContextCompat.getColor(context, android.R.color.system_accent3_400);

        // dp → px
        android.util.DisplayMetrics dm = context.getResources().getDisplayMetrics();
        float lineWidthPx   = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, LINE_WIDTH_DP, dm);
        float circleRadius  = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, CIRCLE_RADIUS_DP, dm);
        float padSlopPx     = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_DIP, PAD_SLOP_DP, dm);

        final int n = data.size();
        float[] y = new float[n];
        for (int i = 0; i < n; i++) y[i] = (float) data.get(i).pricePerKwh;

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

        // pads to avoid clipping stroke and circle
        float padX = lineWidthPx / 2f + padSlopPx;
        float padTop = Math.max(lineWidthPx / 2f + padSlopPx, circleRadius + padSlopPx);
        float padBottom = lineWidthPx / 2f + padSlopPx;

        float usableW = Math.max(1f, widthPx  - 2f * padX);
        float usableH = Math.max(1f, heightPx - padTop - padBottom);
        float stepX = usableW / (float) (totalPts - 1);
        float leftX = padX;
        float rightX = padX + (totalPts - 1) * stepX;
        float bottomY = heightPx - padBottom;

        // "now" position
        ZonedDateTime firstStart = data.get(0).startTime.atZoneSameInstant(ZoneId.systemDefault());
        float stepsFromStart = (float) (Duration.between(firstStart, now).toMinutes() / (float) stepMinutes);
        stepsFromStart = Math.max(0f, Math.min(stepsFromStart, n - 1));
        float nowPt = stepsFromStart * subdiv;

        int base = (int) Math.floor(stepsFromStart);
        int i0c = Math.max(0, base - 1), i1c = base, i2c = Math.min(n - 1, base + 1), i3c = Math.min(n - 1, base + 2);
        float tNow = stepsFromStart - base;
        float nowYVal = catmullRom(y[i0c], y[i1c], y[i2c], y[i3c], tNow);
        float nowYClamped = Math.max(0f, Math.min((float) maxPrice, nowYVal));
        float currentX = leftX + nowPt * stepX;
        float currentY = heightPx - padBottom - (nowYClamped / (float) maxPrice) * usableH;

        // layer for horizontal fade later
        int layerId = canvas.saveLayer(0, 0, widthPx, heightPx, null);

        // draw curve and collect samples
        float y0c = Math.max(0f, Math.min((float) maxPrice, y[0]));
        float prevX = leftX;
        float prevY = heightPx - padBottom - (y0c / (float) maxPrice) * usableH;
        int ptIndex = 1;

        float[] ySamp = new float[totalPts];
        ySamp[0] = prevY;

        for (int i = 0; i < n - 1; i++) {
            int i0 = Math.max(0, i - 1), i1 = i, i2 = i + 1, i3 = Math.min(n - 1, i + 2);

            for (int s = 1; s <= subdiv; s++) {
                float t = s / (float) subdiv;
                float yInterp = catmullRom(y[i0], y[i1], y[i2], y[i3], t);
                float yVal = Math.max(0f, Math.min((float) maxPrice, yInterp)); // clamp overshoot
                float x = leftX + ptIndex * stepX;
                float yPix = heightPx - padBottom - (yVal / (float) maxPrice) * usableH;

                if (prevX < currentX && x > currentX) {
                    float ratio = (currentX - prevX) / (x - prevX);
                    float splitY = prevY + ratio * (yPix - prevY);
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

        // mask path for fill
        android.graphics.Path maskPath = new android.graphics.Path();
        maskPath.moveTo(leftX, bottomY);
        maskPath.lineTo(leftX, ySamp[0]);
        for (int i = 1; i < totalPts; i++) maskPath.lineTo(leftX + i * stepX, ySamp[i]);
        maskPath.lineTo(rightX, bottomY);
        maskPath.close();

        // vertical eased fade under curve
        final int vStops = 32;
        int[] vColors = new int[vStops];
        float[] vPos = new float[vStops];
        int topAlpha = 140;
        float vPow = Math.max(1f, VERTICAL_EASE_POWER);
        for (int i = 0; i < vStops; i++) {
            float tt = i / (float) (vStops - 1); // 0 top → 1 bottom
            float a = (float) Math.pow(1f - tt, vPow);
            int alpha = Math.round(topAlpha * Math.max(0f, Math.min(1f, a)));
            vColors[i] = androidx.core.graphics.ColorUtils.setAlphaComponent(fadeBase, alpha);
            vPos[i] = tt;
        }
        android.graphics.Shader fadeV = new android.graphics.LinearGradient(
                0, 0, 0, heightPx, vColors, vPos, android.graphics.Shader.TileMode.CLAMP);

        Paint fadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fadePaint.setStyle(Paint.Style.FILL);
        fadePaint.setShader(fadeV);

        int clipId = canvas.save();
        canvas.clipPath(maskPath);
        canvas.drawRect(0, 0, widthPx, heightPx, fadePaint);
        canvas.restoreToCount(clipId);

        // vertical grid lines
        float gridWidth = GRID_LINE_WIDTH_DP * dm.density;
        android.graphics.Paint grid = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        grid.setStyle(android.graphics.Paint.Style.STROKE);
        grid.setStrokeWidth(gridWidth);

        final int gStops = 16;
        int[] gColors = new int[gStops];
        float[] gPos = new float[gStops];
        for (int s = 0; s < gStops; s++) {
            float tt = s / (float) (gStops - 1); // 0 bottom → 1 top
            float a = (float) Math.pow(1f - tt, GRID_FADE_POWER);
            int alpha = Math.round(255f * GRID_ALPHA * a);
            gColors[s] = androidx.core.graphics.ColorUtils.setAlphaComponent(colorFuture, alpha);
            gPos[s] = tt;
        }

        int stepsPerHour = Math.max(1, Math.round(60f / (float) stepMinutes));
        int gridStep = Math.max(1, stepsPerHour * 2);
        for (int i = 0; i < n; i += gridStep) {
            float x = leftX + (i * subdiv) * stepX;
            float yi = Math.max(0f, Math.min((float) maxPrice, y[i]));
            float yTop = bottomY - (yi / (float) maxPrice) * usableH;
            android.graphics.Shader grad = new android.graphics.LinearGradient(
                    x, bottomY, x, yTop, gColors, gPos, android.graphics.Shader.TileMode.CLAMP);
            grid.setShader(grad);
            canvas.drawLine(x, bottomY, x, yTop, grid);
        }

        // current point
        Paint circle = new Paint(Paint.ANTI_ALIAS_FLAG);
        circle.setStyle(Paint.Style.FILL);
        circle.setColor(colorCurrent);
        canvas.drawCircle(currentX, currentY, circleRadius, circle);

        // horizontal edge fade (power-scaled)
        float edgePts = subdiv * (EDGE_FADE_MINUTES / (float) stepMinutes);
        float edgePx  = Math.max(lineWidthPx, edgePts * stepX);
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
                0, 0, widthPx, 0, hColors, hPos, android.graphics.Shader.TileMode.CLAMP);

        Paint mask = new Paint(Paint.ANTI_ALIAS_FLAG);
        mask.setShader(fadeH);
        mask.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN));
        canvas.drawRect(0, 0, widthPx, heightPx, mask);

        canvas.restoreToCount(layerId);
        return bitmap;
    }

    public static Bitmap createStepLineGraphBitmap(
            Context context,
            List<PriceFetcher.PriceEntry> data,
            double maxPrice,
            int widthPx,
            int heightPx) {

        Bitmap bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        if (data == null || data.isEmpty() || maxPrice <= 0) {
            return bitmap;
        }

        android.util.DisplayMetrics dm = context.getResources().getDisplayMetrics();
        float lineWidthPx = android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP,
                STEP_LINE_WIDTH_DP,
                dm
        );
        float padX = lineWidthPx / 2f + android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP,
                PAD_SLOP_DP,
                dm
        );
        float padY = lineWidthPx / 2f + android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP,
                PAD_SLOP_DP,
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

        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(lineWidthPx);
        line.setStrokeCap(Paint.Cap.BUTT);
        line.setStrokeJoin(Paint.Join.MITER);
        line.setColor(ContextCompat.getColor(context, R.color.main_lines_chart));

        Paint guide = new Paint(Paint.ANTI_ALIAS_FLAG);
        guide.setStyle(Paint.Style.STROKE);
        guide.setStrokeWidth(Math.max(1f, lineWidthPx * 0.55f));
        guide.setColor(ContextCompat.getColor(context, R.color.main_lines_chart_guide));

        for (int i = 0; i < data.size(); i++) {
            PriceFetcher.PriceEntry entry = data.get(i);
            float startFraction = Duration.between(windowStart, entry.startTime).toMinutes() / (float) totalMinutes;
            float endFraction = Duration.between(windowStart, entry.endTime).toMinutes() / (float) totalMinutes;
            float startX = leftX + (usableW * startFraction);
            float endX = leftX + (usableW * endFraction);
            float y = bottomY - ((float) entry.pricePerKwh / (float) maxPrice) * usableH;
            y = Math.max(topY, Math.min(bottomY, y));

            OffsetDateTime localStart = entry.startTime.atZoneSameInstant(ZoneId.systemDefault()).toOffsetDateTime();
            if (localStart.getMinute() == 0) {
                canvas.drawLine(startX, bottomY, startX, y, guide);
            }
            canvas.drawLine(startX, y, endX, y, line);

            if (i < data.size() - 1) {
                PriceFetcher.PriceEntry next = data.get(i + 1);
                float nextY = bottomY - ((float) next.pricePerKwh / (float) maxPrice) * usableH;
                nextY = Math.max(topY, Math.min(bottomY, nextY));
                canvas.drawLine(endX, y, endX, nextY, line);
            }
        }

        return bitmap;
    }





}

