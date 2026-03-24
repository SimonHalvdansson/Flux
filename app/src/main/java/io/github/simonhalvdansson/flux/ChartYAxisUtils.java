package io.github.simonhalvdansson.flux;

import java.text.NumberFormat;

final class ChartYAxisUtils {

    static final float[] TICK_FRACTIONS = {0.8f, 0.6f, 0.4f, 0.2f};

    private ChartYAxisUtils() {
    }

    static double resolveRoundedScaleMax(double maxPricePerKwh) {
        if (maxPricePerKwh <= 0.0d) {
            return 1.0d;
        }

        double minimumTickStep = maxPricePerKwh / (TICK_FRACTIONS.length + 1.0d);
        double roundedTickStep = resolveNiceTickStep(minimumTickStep);
        return roundedTickStep * (TICK_FRACTIONS.length + 1.0d);
    }

    static double normalizeTickValue(double value) {
        return Math.abs(value) < 0.0000001d ? 0.0d : value;
    }

    static String formatAxisValue(double pricePerKwh, String countryCode) {
        double displayValue = pricePerKwh * getDisplayMultiplier(countryCode);
        int fractionDigits = resolveFractionDigits(displayValue);
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

    private static double resolveNiceTickStep(double minimumStep) {
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

    private static int resolveFractionDigits(double displayValue) {
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

    private static double getDisplayMultiplier(String countryCode) {
        return "CH".equals(countryCode) ? 100.0d : RegionConfig.getPriceDisplayMultiplier(countryCode);
    }
}
