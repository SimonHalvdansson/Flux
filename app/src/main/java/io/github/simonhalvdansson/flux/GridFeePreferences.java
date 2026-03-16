package io.github.simonhalvdansson.flux;

import android.content.SharedPreferences;

import java.util.Locale;

public final class GridFeePreferences {

    public static final String KEY_GRID_FEE = "grid_fee";
    public static final String KEY_GRID_FEE_PREFIX = KEY_GRID_FEE + "_";

    private GridFeePreferences() {
    }

    public static String getPreferenceKey(String countryCode) {
        return KEY_GRID_FEE_PREFIX + sanitizeCountryCode(countryCode);
    }

    public static String getSavedGridFee(SharedPreferences prefs, String countryCode) {
        String key = getPreferenceKey(countryCode);
        String savedValue = prefs.getString(key, null);
        if (savedValue != null) {
            return savedValue;
        }

        String legacyValue = prefs.getString(KEY_GRID_FEE, null);
        if (legacyValue != null) {
            prefs.edit()
                    .putString(key, legacyValue)
                    .remove(KEY_GRID_FEE)
                    .apply();
            return legacyValue;
        }

        return "0";
    }

    private static String sanitizeCountryCode(String countryCode) {
        if (countryCode == null || countryCode.trim().isEmpty()) {
            return "NO";
        }
        return countryCode.trim().toUpperCase(Locale.US);
    }
}
