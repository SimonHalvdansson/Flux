package io.github.simonhalvdansson.flux;

import java.text.NumberFormat;

/**
 * Helper methods for formatting price values for display.
 */
public final class PriceDisplayUtils {

    private PriceDisplayUtils() {
    }

    public static String getUnitText(String country) {
        return RegionConfig.getUnitLabel(country);
    }

    public static double parseGridFee(String rawValue) {
        if (rawValue == null) {
            return 0.0;
        }
        try {
            return Double.parseDouble(rawValue.replace(',', '.'));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    public static String formatPrice(double pricePerKwh, String country) {
        NumberFormat numberFormat = NumberFormat.getNumberInstance(RegionConfig.getNumberLocale(country));
        double value = pricePerKwh * RegionConfig.getPriceDisplayMultiplier(country);
        numberFormat.setGroupingUsed(false);
        numberFormat.setMinimumFractionDigits(2);
        numberFormat.setMaximumFractionDigits(2);
        return numberFormat.format(value);
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

