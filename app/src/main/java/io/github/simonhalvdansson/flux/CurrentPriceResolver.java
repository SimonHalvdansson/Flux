package io.github.simonhalvdansson.flux;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;

public final class CurrentPriceResolver {

    public static final class Snapshot {
        public final boolean hasData;
        public final boolean apiError;
        public final String country;
        public final String formattedPrice;
        public final String unitText;

        Snapshot(boolean hasData, boolean apiError, String country, String formattedPrice, String unitText) {
            this.hasData = hasData;
            this.apiError = apiError;
            this.country = country;
            this.formattedPrice = formattedPrice;
            this.unitText = unitText;
        }

        public String getDisplayPrice() {
            if (!hasData) {
                return null;
            }
            return formattedPrice + " " + unitText;
        }
    }

    private CurrentPriceResolver() {
    }

    public static Snapshot resolve(Context context) {
        SharedPreferences prefs = PriceRepository.getPreferences(context);
        String country = prefs.getString(PriceUpdateJobService.KEY_SELECTED_COUNTRY, "NO");
        String unitText = PriceDisplayUtils.getUnitText(country);
        boolean apiError = prefs.getBoolean(PriceUpdateJobService.KEY_API_ERROR, false);

        List<PriceFetcher.PriceEntry> entries = getAdjustedEntries(prefs);
        PriceFetcher.PriceEntry currentEntry = findCurrentEntry(entries);
        if (currentEntry == null) {
            return new Snapshot(false, apiError, country, null, unitText);
        }

        return new Snapshot(
                true,
                apiError,
                country,
                PriceDisplayUtils.formatPrice(currentEntry.pricePerKwh, country),
                unitText
        );
    }

    public static List<PriceFetcher.PriceEntry> getAdjustedEntries(SharedPreferences prefs) {
        String combinedJson = prefs.getString(PriceRepository.KEY_JSON_DATA, null);
        if (combinedJson == null || combinedJson.trim().isEmpty()) {
            return PriceFetcher.parseCombinedJson(null);
        }

        String country = prefs.getString(PriceUpdateJobService.KEY_SELECTED_COUNTRY, "NO");
        boolean applyVat = prefs.getBoolean(PriceUpdateJobService.KEY_APPLY_VAT, false);
        boolean applyStromstotte = prefs.getBoolean(PriceUpdateJobService.KEY_APPLY_STROMSTOTTE, false);
        double gridFee = PriceDisplayUtils.parseGridFee(prefs.getString(PriceUpdateJobService.KEY_GRID_FEE, ""));

        List<PriceFetcher.PriceEntry> entries = PriceFetcher.parseCombinedJson(combinedJson);
        for (PriceFetcher.PriceEntry entry : entries) {
            entry.pricePerKwh = PriceDisplayUtils.applyPriceAdjustments(
                    entry.pricePerKwh,
                    country,
                    applyVat,
                    applyStromstotte,
                    gridFee
            );
        }
        entries.sort(Comparator.comparing(o -> o.startTime));
        return entries;
    }

    public static PriceFetcher.PriceEntry findCurrentEntry(List<PriceFetcher.PriceEntry> entries) {
        int currentIndex = findCurrentIndex(entries);
        if (currentIndex < 0) {
            return null;
        }
        return entries.get(currentIndex);
    }

    public static int findCurrentIndex(List<PriceFetcher.PriceEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return -1;
        }

        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        for (int i = 0; i < entries.size(); i++) {
            ZonedDateTime start = entries.get(i).startTime.atZoneSameInstant(ZoneId.systemDefault());
            ZonedDateTime end = entries.get(i).endTime.atZoneSameInstant(ZoneId.systemDefault());
            if ((now.isEqual(start) || now.isAfter(start)) && now.isBefore(end)) {
                return i;
            }
        }

        if (now.isBefore(entries.get(0).startTime.atZoneSameInstant(ZoneId.systemDefault()))) {
            return 0;
        }
        return entries.size() - 1;
    }
}
