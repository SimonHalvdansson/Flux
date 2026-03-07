package io.github.simonhalvdansson.flux;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.List;


public class ConfigurationActivity extends AppCompatActivity {

    private final List<RegionConfig.Country> countries = RegionConfig.getCountries();
    private int currentCountryIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_configuration);

        PriceUpdateScheduler.schedulePriceUpdateJob(this);

        AutoCompleteTextView countryDropdown = findViewById(R.id.country_dropdown);
        AutoCompleteTextView areaDropdown = findViewById(R.id.area_dropdown);

        List<String> countryNames = new ArrayList<>();
        for (RegionConfig.Country country : countries) {
            countryNames.add(country.getDisplayName());
        }
        ArrayAdapter<String> countryAdapter = new ArrayAdapter<>(this, R.layout.spinner_dropdown_item, countryNames);
        countryDropdown.setAdapter(countryAdapter);

        // Draw behind system bars
        final View root = findViewById(R.id.main_container);

        // Remember original paddings
        final int padStart = root.getPaddingStart();
        final int padTop   = root.getPaddingTop();
        final int padEnd   = root.getPaddingEnd();
        final int padBot   = root.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime  = insets.getInsets(WindowInsetsCompat.Type.ime());

            root.setPaddingRelative(
                    padStart + bars.left,
                    padTop   + bars.top,
                    padEnd   + bars.right,
                    padBot   + Math.max(bars.bottom, ime.bottom)
            );
            return insets; // don’t consume
        });
        ViewCompat.requestApplyInsets(root);

        SharedPreferences prefs = PriceRepository.getPreferences(this);
        LinearLayout regionContainer = findViewById(R.id.region_container);
        LinearLayout stromstotteContainer = findViewById(R.id.stromstotte_container);
        MaterialSwitch stromstotteSwitch = findViewById(R.id.stromstotte_switch);
        MaterialSwitch vatSwitch = findViewById(R.id.vat_switch);
        TextView vatLabel = findViewById(R.id.vat_label);
        TextInputLayout gridFeeContainer = findViewById(R.id.grid_fee_container);
        TextInputEditText gridFeeInput = findViewById(R.id.grid_fee_input);
        MaterialButtonToggleGroup chartToggleGroup = findViewById(R.id.chart_toggle_group);

        int chartMode = prefs.getInt(PriceUpdateJobService.KEY_CHART_MODE, 0);
        if (chartMode == 0) {
            chartToggleGroup.check(R.id.chart_bars_button);
        } else {
            chartToggleGroup.check(R.id.chart_graph_button);
        }

        chartToggleGroup.setSelectionRequired(true);

        chartToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                int mode = checkedId == R.id.chart_bars_button ? 0 : 1;
                prefs.edit().putInt(PriceUpdateJobService.KEY_CHART_MODE, mode).apply();
                MainWidget.updateAllWidgets(ConfigurationActivity.this);
                ListWidget.updateAllWidgets(ConfigurationActivity.this);
            }
        });

        Button doneButton = findViewById(R.id.done_button);

        doneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finishAffinity();
            }
        });

        // Initial country and area selection
        String selectedCountry = prefs.getString(PriceUpdateJobService.KEY_SELECTED_COUNTRY, "NO");
        currentCountryIndex = RegionConfig.indexOfCountryCode(selectedCountry);
        if (currentCountryIndex < 0) {
            currentCountryIndex = RegionConfig.indexOfCountryCode("NO");
        }
        if (currentCountryIndex < 0) {
            currentCountryIndex = 0;
        }

        RegionConfig.Country currentCountry = countries.get(currentCountryIndex);
        countryDropdown.setText(currentCountry.getDisplayName(), false);

        updateAreaDropdown(areaDropdown, prefs, currentCountry);

        stromstotteSwitch.setChecked(prefs.getBoolean(PriceUpdateJobService.KEY_APPLY_STROMSTOTTE, false));
        boolean isNorway = "NO".equals(currentCountry.getCode());
        stromstotteContainer.setVisibility(isNorway ? View.VISIBLE : View.GONE);
        if (!isNorway) {
            stromstotteSwitch.setChecked(false);
            prefs.edit().putBoolean(PriceUpdateJobService.KEY_APPLY_STROMSTOTTE, false).apply();
        }
        updateRegionVisibility(regionContainer, areaDropdown, currentCountry);
        vatSwitch.setChecked(prefs.getBoolean(PriceUpdateJobService.KEY_APPLY_VAT, false));
        updateVatLabel(vatLabel);
        updateGridFeeUnit(gridFeeContainer, currentCountry.getCode());

        String savedGridFee = prefs.getString(PriceUpdateJobService.KEY_GRID_FEE, "");
        if (savedGridFee == null || savedGridFee.trim().isEmpty()) {
            savedGridFee = "0";
            prefs.edit().putString(PriceUpdateJobService.KEY_GRID_FEE, savedGridFee).apply();
        }
        gridFeeInput.setText(savedGridFee);
        gridFeeInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                prefs.edit().putString(PriceUpdateJobService.KEY_GRID_FEE, s == null ? "" : s.toString()).apply();
                MainWidget.updateAllWidgets(ConfigurationActivity.this);
                ListWidget.updateAllWidgets(ConfigurationActivity.this);
            }
        });
        gridFeeInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                return;
            }
            CharSequence value = gridFeeInput.getText();
            if (value == null || value.toString().trim().isEmpty()) {
                gridFeeInput.setText("0");
            }
        });

        countryDropdown.setOnItemClickListener((parent, view, position, id) -> {
            currentCountryIndex = position;
            RegionConfig.Country selected = countries.get(position);
            String countryCode = selected.getCode();
            prefs.edit().putString(PriceUpdateJobService.KEY_SELECTED_COUNTRY, countryCode).apply();

            updateAreaDropdown(areaDropdown, prefs, selected);

            boolean norway = "NO".equals(countryCode);
            stromstotteContainer.setVisibility(norway ? View.VISIBLE : View.GONE);
            if (!norway) {
                stromstotteSwitch.setChecked(false);
                prefs.edit().putBoolean(PriceUpdateJobService.KEY_APPLY_STROMSTOTTE, false).apply();
            }
            updateRegionVisibility(regionContainer, areaDropdown, selected);
            updateVatLabel(vatLabel);
            updateGridFeeUnit(gridFeeContainer, countryCode);
            PriceUpdateScheduler.schedulePriceUpdateJob(ConfigurationActivity.this);
        });

        countryDropdown.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) countryDropdown.showDropDown();
        });
        countryDropdown.setOnClickListener(v -> countryDropdown.showDropDown());

        areaDropdown.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                List<RegionConfig.Area> areas = countries.get(currentCountryIndex).getAreas();
                String area = areas.get(position).getCode();
                prefs.edit().putString(PriceUpdateJobService.KEY_SELECTED_AREA, area).apply();
                PriceUpdateScheduler.schedulePriceUpdateJob(ConfigurationActivity.this);
            }
        });

        areaDropdown.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && countries.get(currentCountryIndex).hasMultipleAreas()) {
                areaDropdown.showDropDown();
            }
        });
        areaDropdown.setOnClickListener(v -> {
            if (countries.get(currentCountryIndex).hasMultipleAreas()) {
                areaDropdown.showDropDown();
            }
        });

        stromstotteSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(PriceUpdateJobService.KEY_APPLY_STROMSTOTTE, isChecked).apply();
            MainWidget.updateAllWidgets(ConfigurationActivity.this);
            ListWidget.updateAllWidgets(ConfigurationActivity.this);
        });

        vatSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(PriceUpdateJobService.KEY_APPLY_VAT, isChecked).apply();
            MainWidget.updateAllWidgets(ConfigurationActivity.this);
            ListWidget.updateAllWidgets(ConfigurationActivity.this);
        });
    }

    private void updateVatLabel(TextView label) {
        label.setText("VAT (+25 %)");
    }

    private void updateGridFeeUnit(TextInputLayout layout, String countryCode) {
        layout.setSuffixText(PriceDisplayUtils.getUnitText(countryCode));
    }

    private void updateAreaDropdown(AutoCompleteTextView areaDropdown, SharedPreferences prefs, RegionConfig.Country country) {
        List<RegionConfig.Area> areas = country.getAreas();
        List<String> labels = new ArrayList<>(areas.size());
        for (RegionConfig.Area area : areas) {
            labels.add(area.getLabel());
        }
        ArrayAdapter<String> newAdapter = new ArrayAdapter<>(this, R.layout.spinner_dropdown_item, labels);
        areaDropdown.setAdapter(newAdapter);

        String defaultAreaCode = areas.isEmpty() ? null : areas.get(0).getCode();
        String selectedArea = prefs.getString(PriceUpdateJobService.KEY_SELECTED_AREA, defaultAreaCode);
        RegionConfig.Area areaToDisplay = null;
        for (RegionConfig.Area area : areas) {
            if (area.getCode().equals(selectedArea)) {
                areaToDisplay = area;
                break;
            }
        }
        if (areaToDisplay == null && !areas.isEmpty()) {
            areaToDisplay = areas.get(0);
            prefs.edit().putString(PriceUpdateJobService.KEY_SELECTED_AREA, areaToDisplay.getCode()).apply();
        }
        if (areaToDisplay != null) {
            areaDropdown.setText(areaToDisplay.getLabel(), false);
        } else {
            areaDropdown.setText("", false);
        }
    }

    private void updateRegionVisibility(LinearLayout regionContainer, AutoCompleteTextView areaDropdown, RegionConfig.Country country) {
        boolean showRegion = country.hasMultipleAreas();
        regionContainer.setVisibility(showRegion ? View.VISIBLE : View.GONE);
        areaDropdown.setEnabled(showRegion);
        areaDropdown.setFocusable(showRegion);
        areaDropdown.setFocusableInTouchMode(showRegion);
    }
}

