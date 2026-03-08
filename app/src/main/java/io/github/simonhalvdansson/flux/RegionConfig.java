package io.github.simonhalvdansson.flux;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Centralised configuration for countries, regions and related metadata.
 */
public final class RegionConfig {

    public static final class Area {
        private final String code;
        private final String label;
        private final String eicCode;

        private Area(String code, String label, String eicCode) {
            this.code = code;
            this.label = label;
            this.eicCode = eicCode;
        }

        public String getCode() {
            return code;
        }

        public String getLabel() {
            return label;
        }

        public String getEicCode() {
            return eicCode;
        }
    }

    public static final class Country {
        private final String code;
        private final String displayName;
        private final double vatPercent;
        private final String vatLabelPrefix;
        private final String currency;
        private final ZoneId zoneId;
        private final Locale numberLocale;
        private final double priceDisplayMultiplier;
        private final String unitLabel;
        private final List<Area> areas;

        private Country(String code,
                        String displayName,
                        double vatPercent,
                        String vatLabelPrefix,
                        String currency,
                        ZoneId zoneId,
                        Locale numberLocale,
                        double priceDisplayMultiplier,
                        String unitLabel,
                        List<Area> areas) {
            this.code = code;
            this.displayName = displayName;
            this.vatPercent = vatPercent;
            this.vatLabelPrefix = vatLabelPrefix;
            this.currency = currency;
            this.zoneId = zoneId;
            this.numberLocale = numberLocale;
            this.priceDisplayMultiplier = priceDisplayMultiplier;
            this.unitLabel = unitLabel;
            this.areas = Collections.unmodifiableList(new ArrayList<>(areas));
        }

        public String getCode() {
            return code;
        }

        public String getDisplayName() {
            return displayName;
        }

        public double getVatPercent() {
            return vatPercent;
        }

        public String getVatLabelPrefix() {
            return vatLabelPrefix;
        }

        public String getCurrency() {
            return currency;
        }

        public ZoneId getZoneId() {
            return zoneId;
        }

        public Locale getNumberLocale() {
            return numberLocale;
        }

        public double getPriceDisplayMultiplier() {
            return priceDisplayMultiplier;
        }

        public String getUnitLabel() {
            return unitLabel;
        }

        public List<Area> getAreas() {
            return areas;
        }

        public boolean hasMultipleAreas() {
            return areas.size() > 1;
        }
    }

    private static final List<Country> COUNTRIES;
    private static final Map<String, Country> COUNTRY_BY_CODE;
    private static final Map<String, String> AREA_TO_EIC_CODE;

    static {
        List<Country> countries = new ArrayList<>();

        countries.add(new Country(
                "LV",
                "Latvia",
                21.0,
                "VAT",
                "EUR",
                ZoneId.of("Europe/Riga"),
                Locale.forLanguageTag("lv-LV"),
                1.0,
                "€/kWh",
                List.of(area("LV", "Latvia", "10YLV-1001A00074"))
        ));

        countries.add(new Country(
                "LT",
                "Lithuania",
                21.0,
                "VAT",
                "EUR",
                ZoneId.of("Europe/Vilnius"),
                Locale.forLanguageTag("lt-LT"),
                1.0,
                "€/kWh",
                List.of(area("LT", "Lithuania", "10YLT-1001A0008Q"))
        ));

        countries.add(new Country(
                "DE",
                "Germany",
                19.0,
                "VAT",
                "EUR",
                ZoneId.of("Europe/Berlin"),
                Locale.GERMANY,
                1.0,
                "€/kWh",
                List.of(area("DE-LU", "Germany", "10Y1001A1001A82H"))
        ));

        countries.add(new Country(
                "LU",
                "Luxembourg",
                17.0,
                "VAT",
                "EUR",
                ZoneId.of("Europe/Luxembourg"),
                Locale.GERMANY,
                1.0,
                "€/kWh",
                List.of(area("LU", "Luxembourg", "10Y1001A1001A82H"))
        ));

        countries.add(new Country(
                "EE",
                "Estonia",
                22.0,
                "VAT",
                "EUR",
                ZoneId.of("Europe/Tallinn"),
                Locale.forLanguageTag("et-EE"),
                1.0,
                "€/kWh",
                List.of(area("EE", "Estonia", "10Y1001A1001A39I"))
        ));

        countries.add(new Country(
                "PL",
                "Poland",
                23.0,
                "VAT",
                "PLN",
                ZoneId.of("Europe/Warsaw"),
                Locale.forLanguageTag("pl-PL"),
                1.0,
                "PLN/kWh",
                List.of(area("PL", "Poland", "10YPL-AREA-----S"))
        ));

        countries.add(new Country(
                "RS",
                "Serbia",
                20.0,
                "VAT",
                "RSD",
                ZoneId.of("Europe/Belgrade"),
                Locale.forLanguageTag("sr-RS"),
                1.0,
                "RSD/kWh",
                List.of(area("RS", "Serbia", "10YCS-SERBIATSOV"))
        ));

        countries.add(new Country(
                "BG",
                "Bulgaria",
                20.0,
                "VAT",
                "BGN",
                ZoneId.of("Europe/Sofia"),
                Locale.forLanguageTag("bg-BG"),
                1.0,
                "BGN/kWh",
                List.of(area("BG", "Bulgaria", "10YCA-BULGARIA-R"))
        ));

        countries.add(new Country(
                "RO",
                "Romania",
                19.0,
                "VAT",
                "RON",
                ZoneId.of("Europe/Bucharest"),
                Locale.forLanguageTag("ro-RO"),
                1.0,
                "RON/kWh",
                List.of(area("RO", "Romania", "10YRO-TEL------P"))
        ));

        countries.add(new Country(
                "SK",
                "Slovakia",
                20.0,
                "VAT",
                "EUR",
                ZoneId.of("Europe/Bratislava"),
                Locale.forLanguageTag("sk-SK"),
                1.0,
                "€/kWh",
                List.of(area("SK", "Slovakia", "10YSK-SEPS-----K"))
        ));

        countries.add(new Country(
                "HU",
                "Hungary",
                27.0,
                "VAT",
                "HUF",
                ZoneId.of("Europe/Budapest"),
                Locale.forLanguageTag("hu-HU"),
                1.0,
                "HUF/kWh",
                List.of(area("HU", "Hungary", "10YHU-MAVIR----U"))
        ));

        countries.add(new Country(
                "HR",
                "Croatia",
                25.0,
                "VAT",
                "EUR",
                ZoneId.of("Europe/Zagreb"),
                Locale.forLanguageTag("hr-HR"),
                1.0,
                "€/kWh",
                List.of(area("HR", "Croatia", "10YHR-HEP------M"))
        ));

        countries.add(new Country(
                "SI",
                "Slovenia",
                22.0,
                "VAT",
                "EUR",
                ZoneId.of("Europe/Ljubljana"),
                Locale.forLanguageTag("sl-SI"),
                1.0,
                "€/kWh",
                List.of(area("SI", "Slovenia", "10YSI-ELES-----O"))
        ));

        countries.add(new Country(
                "GR",
                "Greece",
                24.0,
                "VAT",
                "EUR",
                ZoneId.of("Europe/Athens"),
                Locale.forLanguageTag("el-GR"),
                1.0,
                "€/kWh",
                List.of(area("GR", "Greece", "10YGR-HTSO-----Y"))
        ));

        countries.add(new Country(
                "AT",
                "Austria",
                20.0,
                "VAT",
                "EUR",
                ZoneId.of("Europe/Vienna"),
                Locale.forLanguageTag("de-AT"),
                1.0,
                "€/kWh",
                List.of(area("AT", "Austria", "10YAT-APG------L"))
        ));

        countries.add(new Country(
                "CZ",
                "Czech Republic",
                21.0,
                "VAT",
                "CZK",
                ZoneId.of("Europe/Prague"),
                Locale.forLanguageTag("cs-CZ"),
                1.0,
                "CZK/kWh",
                List.of(area("CZ", "Czech Republic", "10YCZ-CEPS-----N"))
        ));

        countries.add(new Country(
                "CH",
                "Switzerland",
                8.1,
                "VAT",
                "CHF",
                ZoneId.of("Europe/Zurich"),
                Locale.forLanguageTag("de-CH"),
                100.0,
                "Rp./kWh",
                List.of(area("CH", "Switzerland", "10YCH-SWISSGRIDZ"))
        ));

        countries.add(new Country(
                "IT",
                "Italy",
                22.0,
                "VAT",
                "EUR",
                ZoneId.of("Europe/Rome"),
                Locale.ITALY,
                1.0,
                "€/kWh",
                Arrays.asList(
                        area("IT-CNOR", "Italy (Centre-North)", "10Y1001A1001A70O"),
                        area("IT-CSUD", "Italy (Centre-South)", "10Y1001A1001A71M"),
                        area("IT-NORD", "Italy (North)", "10Y1001A1001A73I"),
                        area("IT-SARD", "Italy (Sardinia)", "10Y1001A1001A74G"),
                        area("IT-CAL", "Italy (Calabria)", "10Y1001C--00096J"),
                        area("IT-SICI", "Italy (Sicily)", "10Y1001A1001A75E"),
                        area("IT-SUD", "Italy (South)", "10Y1001A1001A788")
                )
        ));

        countries.add(new Country(
                "DK",
                "Denmark",
                25.0,
                "VAT",
                "DKK",
                ZoneId.of("Europe/Copenhagen"),
                Locale.forLanguageTag("da-DK"),
                1.0,
                "kr/kWh",
                Arrays.asList(
                        area("DK1", "DK1 / Vest for Storebælt / Aarhus", "10YDK-1--------W"),
                        area("DK2", "DK2 / Øst for Storebælt / København", "10YDK-2--------M")
                )
        ));

        countries.add(new Country(
                "SE",
                "Sweden",
                25.0,
                "VAT",
                "SEK",
                ZoneId.of("Europe/Stockholm"),
                Locale.forLanguageTag("sv-SE"),
                1.0,
                "kr/kWh",
                Arrays.asList(
                        area("SE1", "SE1 / Luleå", "10Y1001A1001A44P"),
                        area("SE2", "SE2 / Sundsvall", "10Y1001A1001A45N"),
                        area("SE3", "SE3 / Stockholm", "10Y1001A1001A46L"),
                        area("SE4", "SE4 / Malmö", "10Y1001A1001A47J")
                )
        ));

        countries.add(new Country(
                "NL",
                "Netherlands",
                21.0,
                "VAT",
                "EUR",
                ZoneId.of("Europe/Amsterdam"),
                Locale.forLanguageTag("nl-NL"),
                1.0,
                "€/kWh",
                List.of(area("NL", "Netherlands", "10YNL----------L"))
        ));

        countries.add(new Country(
                "BE",
                "Belgium",
                21.0,
                "VAT",
                "EUR",
                ZoneId.of("Europe/Brussels"),
                Locale.forLanguageTag("nl-BE"),
                1.0,
                "€/kWh",
                List.of(area("BE", "Belgium", "10YBE----------2"))
        ));

        countries.add(new Country(
                "PT",
                "Portugal",
                23.0,
                "VAT",
                "EUR",
                ZoneId.of("Europe/Lisbon"),
                Locale.forLanguageTag("pt-PT"),
                1.0,
                "€/kWh",
                List.of(area("PT", "Portugal", "10YPT-REN------W"))
        ));

        countries.add(new Country(
                "ES",
                "Spain",
                21.0,
                "VAT",
                "EUR",
                ZoneId.of("Europe/Madrid"),
                Locale.forLanguageTag("es-ES"),
                1.0,
                "€/kWh",
                List.of(area("ES", "Spain", "10YES-REE------0"))
        ));

        countries.add(new Country(
                "FI",
                "Finland",
                25.5,
                "VAT",
                "EUR",
                ZoneId.of("Europe/Helsinki"),
                Locale.forLanguageTag("fi-FI"),
                100.0,
                "c/kWh",
                List.of(area("FI", "Finland", "10YFI-1--------U"))
        ));

        countries.add(new Country(
                "NO",
                "Norway",
                25.0,
                "VAT",
                "NOK",
                ZoneId.of("Europe/Oslo"),
                Locale.forLanguageTag("nb-NO"),
                1.0,
                "kr/kWh",
                Arrays.asList(
                        area("NO1", "NO1 / Østlandet / Oslo", "10YNO-1--------2"),
                        area("NO2", "NO2 / Sørlandet / Kristiansand", "10YNO-2--------T"),
                        area("NO3", "NO3 / Midt-Norge / Trondheim", "10YNO-3--------J"),
                        area("NO4", "NO4 / Nord-Norge / Tromsø", "10YNO-4--------9"),
                        area("NO5", "NO5 / Vestlandet / Bergen", "10Y1001A1001A48H")
                )
        ));

        countries.add(new Country(
                "FR",
                "France",
                20.0,
                "VAT",
                "EUR",
                ZoneId.of("Europe/Paris"),
                Locale.FRANCE,
                1.0,
                "€/kWh",
                List.of(area("FR", "France", "10YFR-RTE------C"))
        ));

        countries.sort(Comparator.comparing(Country::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        COUNTRIES = Collections.unmodifiableList(countries);

        Map<String, Country> countryMap = new LinkedHashMap<>();
        for (Country country : COUNTRIES) {
            countryMap.put(country.getCode(), country);
        }
        COUNTRY_BY_CODE = Collections.unmodifiableMap(countryMap);

        Map<String, String> areaMap = new LinkedHashMap<>();
        for (Country country : COUNTRIES) {
            for (Area area : country.getAreas()) {
                areaMap.put(area.getCode(), area.getEicCode());
            }
        }
        AREA_TO_EIC_CODE = Collections.unmodifiableMap(areaMap);
    }

    private RegionConfig() {
    }

    private static Area area(String code, String label, String eic) {
        return new Area(code, label, eic);
    }

    public static List<Country> getCountries() {
        return COUNTRIES;
    }

    public static Country getCountry(String countryCode) {
        if (countryCode == null) {
            return null;
        }
        return COUNTRY_BY_CODE.get(countryCode);
    }

    public static List<Area> getAreas(String countryCode) {
        Country country = getCountry(countryCode);
        return country != null ? country.getAreas() : Collections.emptyList();
    }

    public static String getCurrency(String countryCode) {
        Country country = getCountry(countryCode);
        return country != null ? country.getCurrency() : "EUR";
    }

    public static ZoneId getZoneId(String countryCode) {
        Country country = getCountry(countryCode);
        return country != null ? country.getZoneId() : ZoneId.systemDefault();
    }

    public static double getVatMultiplier(String countryCode) {
        Country country = getCountry(countryCode);
        if (country == null) {
            return PriceFetcher.VAT_RATE;
        }
        return 1.0 + (country.getVatPercent() / 100.0);
    }

    public static double getVatPercent(String countryCode) {
        Country country = getCountry(countryCode);
        return country != null ? country.getVatPercent() : 25.0;
    }

    public static String getVatLabel(String countryCode) {
        Country country = getCountry(countryCode);
        if (country == null) {
            return "VAT (+25 %)";
        }
        return country.getVatLabelPrefix() + " (+" + formatPercent(country.getVatPercent()) + " %)";
    }

    public static double getPriceDisplayMultiplier(String countryCode) {
        Country country = getCountry(countryCode);
        return country != null ? country.getPriceDisplayMultiplier() : 1.0;
    }

    public static String getUnitLabel(String countryCode) {
        Country country = getCountry(countryCode);
        if (country == null || country.getUnitLabel() == null) {
            String currency = country != null ? country.getCurrency() : "EUR";
            return currency + "/kWh";
        }
        return country.getUnitLabel();
    }

    public static Locale getNumberLocale(String countryCode) {
        Country country = getCountry(countryCode);
        return country != null ? country.getNumberLocale() : Locale.getDefault();
    }

    public static Map<String, String> getAreaToEicCodeMap() {
        return AREA_TO_EIC_CODE;
    }

    public static String getEicCodeForArea(String areaCode) {
        if (areaCode == null) {
            return null;
        }
        return AREA_TO_EIC_CODE.get(areaCode);
    }

    public static int indexOfCountryCode(String countryCode) {
        if (countryCode == null) {
            return -1;
        }
        for (int i = 0; i < COUNTRIES.size(); i++) {
            if (Objects.equals(COUNTRIES.get(i).getCode(), countryCode)) {
                return i;
            }
        }
        return -1;
    }

    public static String getAreaLabel(String countryCode, String areaCode) {
        for (Area area : getAreas(countryCode)) {
            if (Objects.equals(area.getCode(), areaCode)) {
                return area.getLabel();
            }
        }
        return null;
    }

    private static String formatPercent(double value) {
        if (Math.abs(value - Math.rint(value)) < 1e-3) {
            return String.valueOf((int) Math.rint(value));
        }
        return String.format(Locale.US, "%.1f", value);
    }
}

