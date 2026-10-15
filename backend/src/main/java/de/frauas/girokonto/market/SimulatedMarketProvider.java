package de.frauas.girokonto.market;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Fiktive Anbieter und Beispielkonditionen zum Üben des Vergleichs. Keine echten Banken, keine echten Angebote.
 */
@Component
public class SimulatedMarketProvider implements MarketProvider {

    private static final List<String> ID = List.of("Ausweis (Kopie)");
    private static final List<String> ID_INCOME = List.of("Ausweis (Kopie)", "Gehaltsnachweis der letzten 3 Monate");

    private static MarketProduct p(String id, String category, String provider, String name, long monthly, long annual,
                                   String rate, Integer term, Long min, Long orderFee, List<String> requirements,
                                   List<String> pros, List<String> cons, List<String> documents, Long minIncome) {
        return new MarketProduct(id, category, provider + " (fiktiv)", name, monthly, annual,
                rate == null ? null : new BigDecimal(rate), term, min, orderFee, requirements, pros, cons, documents,
                minIncome);
    }

    private static final List<MarketProduct> PRODUCTS = List.of(
            p("giro-kranich", "GIRO", "Kranich Direktbank", "Girokonto Online", 0, 0, null, null, null, null,
                    List.of("Monatlicher Geldeingang ab 700 €"),
                    List.of("Keine Kontoführungsgebühr bei Geldeingang", "Debitkarte inklusive"),
                    List.of("Keine Filialen", "Bargeld nur 4-mal im Monat kostenlos"), ID_INCOME, 70_000L),
            p("giro-lindenhof", "GIRO", "Lindenhof Bank", "Girokonto Plus", 490, 0, null, null, null, null,
                    List.of(), List.of("Filialen und persönliche Beratung", "Kostenlos Bargeld am eigenen Automaten"),
                    List.of("4,90 € Kontoführung pro Monat"), ID, null),
            p("giro-nordlicht", "GIRO", "Nordlicht Bank", "Konto Basis", 250, 0, null, null, null, null,
                    List.of(), List.of("Ohne Bedingungen", "Günstige Grundgebühr"),
                    List.of("2,50 € pro Monat", "Echtzeitüberweisung kostet extra"), ID, null),
            p("cc-polarstern", "CREDIT_CARD", "Polarstern Karten", "Reise Card", 0, 0, "18.90", null, null, null,
                    List.of("Monatliche Einnahmen ab 1.200 €"),
                    List.of("Keine Jahresgebühr", "Keine Gebühr für Zahlungen in Fremdwährung"),
                    List.of("Teilzahlung teuer: 18,90 % Sollzins"), ID_INCOME, 120_000L),
            p("cc-lindenhof", "CREDIT_CARD", "Lindenhof Bank", "Gold Card", 0, 8_900, "15.90", null, null, null,
                    List.of("Monatliche Einnahmen ab 2.000 €"),
                    List.of("Reiserücktritts- und Auslandskrankenversicherung", "Niedrigerer Sollzins"),
                    List.of("89 € Jahresgebühr"), ID_INCOME, 200_000L),
            p("call-kranich", "CALL", "Kranich Direktbank", "Tagesgeld", 0, 0, "2.75", null, null, null,
                    List.of(), List.of("Täglich verfügbar", "Zinsen monatlich"), List.of("Zinssatz kann sich ändern"),
                    ID, null),
            p("call-mosaik", "CALL", "Mosaik Invest", "Tagesgeld Flex", 0, 0, "3.10", null, 100_000L, null,
                    List.of("Mindestanlage 1.000 €"), List.of("Höchster Zins im Vergleich"),
                    List.of("Mindestanlage 1.000 €", "Nur per App"), ID, null),
            p("call-nordlicht", "CALL", "Nordlicht Bank", "Tagesgeld Classic", 0, 0, "2.20", null, null, null,
                    List.of(), List.of("Ohne Mindestanlage", "Filialen"), List.of("Niedrigster Zins im Vergleich"),
                    ID, null),
            p("fixed-lindenhof-12", "FIXED", "Lindenhof Bank", "Festgeld 12 Monate", 0, 0, "3.00", 12, 500_000L,
                    null, List.of("Mindestanlage 5.000 €"), List.of("Fester Zins für 12 Monate"),
                    List.of("Geld 12 Monate gebunden", "Mindestanlage 5.000 €"), ID, null),
            p("fixed-kranich-24", "FIXED", "Kranich Direktbank", "Festgeld 24 Monate", 0, 0, "2.80", 24, 100_000L,
                    null, List.of("Mindestanlage 1.000 €"), List.of("Zins für 2 Jahre gesichert"),
                    List.of("Geld 24 Monate gebunden"), ID, null),
            p("fixed-nordlicht-6", "FIXED", "Nordlicht Bank", "Festgeld 6 Monate", 0, 0, "2.50", 6, 250_000L, null,
                    List.of("Mindestanlage 2.500 €"), List.of("Kurze Bindung"), List.of("Niedrigerer Zins"), ID, null),
            p("depot-mosaik", "DEPOT", "Mosaik Invest", "App-Depot", 0, 0, null, null, null, 100L,
                    List.of(), List.of("Keine Depotgebühr", "Kauf ab 1 € Gebühr", "ETF-Sparpläne"),
                    List.of("Nur per App", "Kleine Auswahl an Fonds"), ID, null),
            p("depot-lindenhof", "DEPOT", "Lindenhof Bank", "Depot Komfort", 300, 0, null, null, null, 990L,
                    List.of(), List.of("Beratung in der Filiale", "Große Auswahl"),
                    List.of("3 € Depotgebühr pro Monat", "9,90 € je Kauf"), ID, null));

    @Override
    public String id() {
        return "SIMULATED";
    }

    @Override
    public boolean simulated() {
        return true;
    }

    @Override
    public List<MarketProduct> products() {
        return PRODUCTS;
    }
}
