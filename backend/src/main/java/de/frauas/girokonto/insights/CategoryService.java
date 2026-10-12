package de.frauas.girokonto.insights;

import de.frauas.girokonto.booking.BookingType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Ordnet Buchungszeilen automatisch einer Kategorie zu (/F560/, Kap. 8.4).
 * Reihenfolge: zuerst der Buchungstyp, dann Schlüsselwörter im Verwendungszweck/Händlernamen.
 */
@Component
public class CategoryService {

    private static final Map<Category, List<String>> KEYWORDS = new LinkedHashMap<>();

    static {
        KEYWORDS.put(Category.WOHNEN, List.of("miete", "strom", "nebenkosten", "gas", "wasser", "hausverwaltung"));
        KEYWORDS.put(Category.VERTRAEGE, List.of("versicherung", "handy", "mobilfunk", "internet", "streaming",
                "abo", "mitglied", "fitness", "semesterticket", "rundfunk", "netflix", "spotify", "telekom", "vodafone"));
        KEYWORDS.put(Category.LEBENSMITTEL, List.of("supermarkt", "bäckerei", "baeckerei", "markt", "lebensmittel",
                "metzgerei", "bio", "rewe", "lidl", "aldi", "edeka", "netto", "penny", "kaufland"));
        KEYWORDS.put(Category.MOBILITAET, List.of("tank", "bahn", "ticket", "bus", "parken", "taxi", "werkstatt", "aral", "shell", "esso"));
        KEYWORDS.put(Category.FREIZEIT, List.of("kino", "restaurant", "café", "cafe", "bar", "konzert", "pizz",
                "imbiss", "theater", "bowling", "zoo", "freizeit"));
        KEYWORDS.put(Category.SHOPPING, List.of("shop", "elektro", "mode", "kleidung", "drogerie", "buch", "möbel", "amazon", "zalando"));
    }

    public Category categorize(BookingType type, String description, long amountCents) {
        switch (type) {
            case INITIAL:
                return Category.STARTGUTHABEN;
            case INTERNAL:
                return Category.UMBUCHUNG;
            case SAVING_RATE:
            case DEPOT:
                return Category.SPAREN;
            case LOAN_PAYOUT:
                return Category.KREDIT_AUSZAHLUNG;
            case LOAN_RATE:
                return Category.KREDIT;
            case LOAN_EXTRA:
                return Category.SONDERTILGUNG;
            default:
                break;
        }
        String text = description == null ? "" : description.toLowerCase(Locale.ROOT);
        if (amountCents > 0) {
            if (type == BookingType.INCOME && (text.contains("gehalt") || text.contains("lohn"))) {
                return Category.GEHALT;
            }
            return Category.SONSTIGE_EINNAHMEN;
        }
        for (Map.Entry<Category, List<String>> e : KEYWORDS.entrySet()) {
            for (String keyword : e.getValue()) {
                if (text.contains(keyword)) {
                    return e.getKey();
                }
            }
        }
        return Category.SONSTIGES;
    }
}
