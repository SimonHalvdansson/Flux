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
            String normalizedValue = normalizeSavedValue(savedValue);
            if (!normalizedValue.equals(savedValue)) {
                prefs.edit().putString(key, normalizedValue).apply();
            }
            return normalizedValue;
        }

        String legacyValue = prefs.getString(KEY_GRID_FEE, null);
        if (legacyValue != null) {
            String normalizedValue = normalizeSavedValue(legacyValue);
            prefs.edit()
                    .putString(key, normalizedValue)
                    .remove(KEY_GRID_FEE)
                    .apply();
            return normalizedValue;
        }

        return "0";
    }

    private static String normalizeSavedValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "0";
        }
        return value;
    }

    private static String sanitizeCountryCode(String countryCode) {
        if (countryCode == null || countryCode.trim().isEmpty()) {
            return "NO";
        }
        return countryCode.trim().toUpperCase(Locale.US);
    }
}
