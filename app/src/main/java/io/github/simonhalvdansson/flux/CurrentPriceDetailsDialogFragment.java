package io.github.simonhalvdansson.flux;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

public class CurrentPriceDetailsDialogFragment extends DialogFragment {
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        SharedPreferences sharedPreferences = PriceRepository.getPreferences(context);
        List<PriceFetcher.PriceEntry> allData =
                CurrentPriceResolver.getAdjustedEntries(context, sharedPreferences);
        PriceFetcher.PriceEntry currentEntry = CurrentPriceResolver.findCurrentEntry(allData);
        if (currentEntry == null) {
            return new MaterialAlertDialogBuilder(context)
                    .setMessage(R.string.current_price_unavailable)
                    .create();
        }

        String countryCode = PriceRepository.getSelectedCountryCode(context, sharedPreferences);
        String unitText = PriceDisplayUtils.getUnitText(countryCode, sharedPreferences);
        double displayMultiplier = RegionConfig.getPriceDisplayMultiplier(countryCode);
        View contentView = LayoutInflater.from(context)
                .inflate(R.layout.dialog_current_price_details, null);
        bindRow(
                contentView.findViewById(R.id.current_price_details_time_row),
                contentView.findViewById(R.id.current_price_details_time_value),
                buildTimeValue(currentEntry)
        );
        bindRow(
                contentView.findViewById(R.id.current_price_details_price_row),
                contentView.findViewById(R.id.current_price_details_price_value),
                getString(
                        R.string.current_price_details_value_exact,
                        formatDetailedPrice(
                                currentEntry.pricePerKwh * displayMultiplier,
                                countryCode,
                                0,
                                5
                        ),
                        unitText
                )
        );
        bindRow(
                contentView.findViewById(R.id.current_price_details_original_row),
                contentView.findViewById(R.id.current_price_details_original_value),
                buildOriginalValue(currentEntry, countryCode)
        );
        bindRow(
                contentView.findViewById(R.id.current_price_details_exchange_rate_row),
                contentView.findViewById(R.id.current_price_details_exchange_rate_value),
                buildExchangeRateValue(currentEntry, countryCode)
        );

        return new MaterialAlertDialogBuilder(context)
                .setView(contentView)
                .create();
    }

    private void bindRow(View rowView, TextView valueView, String value) {
        if (value == null || value.trim().isEmpty()) {
            rowView.setVisibility(View.GONE);
            return;
        }

        rowView.setVisibility(View.VISIBLE);
        valueView.setText(value);
    }

    private String buildTimeValue(PriceFetcher.PriceEntry currentEntry) {
        if (currentEntry == null || currentEntry.startTime == null || currentEntry.endTime == null) {
            return null;
        }

        ZonedDateTime start = currentEntry.startTime.atZoneSameInstant(ZoneId.systemDefault());
        ZonedDateTime end = currentEntry.endTime.atZoneSameInstant(ZoneId.systemDefault());
        return String.format(
                "%02d:%02d-%02d:%02d",
                start.getHour(),
                start.getMinute(),
                end.getHour(),
                end.getMinute()
        );
    }

    private String buildOriginalValue(PriceFetcher.PriceEntry currentEntry, String countryCode) {
        if (currentEntry == null || Double.isNaN(currentEntry.pricePerKwhEur)) {
            return null;
        }

        return getString(
                R.string.current_price_details_value_original,
                formatDetailedPrice(currentEntry.pricePerKwhEur, countryCode, 0, 5)
        );
    }

    private String buildExchangeRateValue(PriceFetcher.PriceEntry currentEntry, String countryCode) {
        if (currentEntry == null) {
            return null;
        }

        String currency = currentEntry.currency;
        if (currency == null || currency.isEmpty()) {
            currency = RegionConfig.getCurrency(countryCode);
        }

        if ("EUR".equals(currency)
                || Double.isNaN(currentEntry.exchangeRatePerEur)
                || currentEntry.exchangeRatePerEur <= 0.0) {
            return null;
        }

        return getString(
                R.string.current_price_details_value_exchange_rate,
                formatDetailedPrice(currentEntry.exchangeRatePerEur, countryCode, 0, 5),
                currency
        );
    }

    private String formatDetailedPrice(double value,
                                       String countryCode,
                                       int minFractionDigits,
                                       int maxFractionDigits) {
        NumberFormat numberFormat = NumberFormat.getNumberInstance(
                RegionConfig.getNumberLocale(countryCode)
        );
        numberFormat.setGroupingUsed(false);
        numberFormat.setMinimumFractionDigits(minFractionDigits);
        numberFormat.setMaximumFractionDigits(maxFractionDigits);
        String formattedValue = numberFormat.format(value);
        String negativeZero = numberFormat.format(-0.0d);
        if (formattedValue.equals(negativeZero)) {
            return numberFormat.format(0.0d);
        }
        return formattedValue;
    }
}
