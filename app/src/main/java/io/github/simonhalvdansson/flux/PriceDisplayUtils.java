package io.github.simonhalvdansson.flux;

import android.content.SharedPreferences;

import java.text.NumberFormat;

/**
 * Helper methods for formatting price values for display.
 */
public final class PriceDisplayUtils {

    public static final String DISPLAY_STYLE_SWISS_RAPPEN = "swiss_rappen";
    public static final String DISPLAY_STYLE_SWISS_CENTIMES = "swiss_centimes";

    private PriceDisplayUtils() {
    }

    public static String getUnitText(String country) {
        return getUnitText(country, null);
    }

    public static String getUnitText(String country, SharedPreferences prefs) {
        if ("CH".equals(country)) {
            return DISPLAY_STYLE_SWISS_CENTIMES.equals(getSwissDisplayStyle(prefs))
                    ? "ct/kWh"
                    : "Rp./kWh";
        }
        return RegionConfig.getUnitLabel(country);
    }

    public static double parseGridFee(String rawValue) {
        return parseGridFee(rawValue, null, null);
    }

    public static double parseGridFee(String rawValue, String country, SharedPreferences prefs) {
        if (rawValue == null) {
            return 0.0;
        }
        try {
            double parsedValue = Double.parseDouble(rawValue.replace(',', '.'));
            double displayMultiplier = getPriceDisplayMultiplier(country, prefs);
            if (displayMultiplier <= 0.0) {
                return parsedValue;
            }
            return parsedValue / displayMultiplier;
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    public static String formatPrice(double pricePerKwh, String country) {
        return formatPrice(pricePerKwh, country, null);
    }

    public static String formatPrice(double pricePerKwh, String country, SharedPreferences prefs) {
        NumberFormat numberFormat = NumberFormat.getNumberInstance(RegionConfig.getNumberLocale(country));
        double value = pricePerKwh * getPriceDisplayMultiplier(country, prefs);
        numberFormat.setGroupingUsed(false);
        numberFormat.setMinimumFractionDigits(2);
        numberFormat.setMaximumFractionDigits(2);
        String formattedValue = numberFormat.format(value);
        String negativeZero = numberFormat.format(-0.0d);
        if (formattedValue.equals(negativeZero)) {
            return numberFormat.format(0.0d);
        }
        return formattedValue;
    }

    public static boolean supportsDisplayStyleSelection(String country) {
        return "CH".equals(country);
    }

    public static String getSwissDisplayStyle(SharedPreferences prefs) {
        if (prefs == null) {
            return DISPLAY_STYLE_SWISS_RAPPEN;
        }
        String style = prefs.getString(
                PriceUpdateJobService.KEY_PRICE_DISPLAY_STYLE,
                DISPLAY_STYLE_SWISS_RAPPEN
        );
        return DISPLAY_STYLE_SWISS_CENTIMES.equals(style)
                ? DISPLAY_STYLE_SWISS_CENTIMES
                : DISPLAY_STYLE_SWISS_RAPPEN;
    }

    private static double getPriceDisplayMultiplier(String country, SharedPreferences prefs) {
        if ("CH".equals(country)) {
            return 100.0;
        }
        return RegionConfig.getPriceDisplayMultiplier(country);
    }

    public static double applyPriceAdjustments(double pricePerKwh,
                                               String country,
                                               boolean applyVat,
                                               boolean applyStromstotte,
                                               double gridFee) {
        double adjustedPrice = pricePerKwh;
        if (applyStromstotte && adjustedPrice > PriceFetcher.STROMSTOTTE_THRESHOLD) {
            adjustedPrice -= (adjustedPrice - PriceFetcher.STROMSTOTTE_THRESHOLD)
                    * PriceFetcher.STROMSTOTTE_PERCENT;
        }
        if (applyVat) {
            adjustedPrice *= PriceFetcher.getVatRate(country);
        }
        return adjustedPrice + gridFee;
    }
}
