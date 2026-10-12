package de.frauas.girokonto.insights;

import de.frauas.girokonto.booking.BookingType;
import java.util.List;

/**
 * Beispielhafte Haushalte für die Beispielmonate (/F590/, Pflichtenheft Kap. 8.6).
 * variable = true: Betrag schwankt reproduzierbar um ±25 %.
 */
public enum DemoProfile {

    STUDENT("Studierende", List.of(
            Item.income(1, "Lohn Nebenjob – Café Mainblick", 65000),
            Item.income(1, "BAföG – Amt für Ausbildungsförderung", 52000),
            Item.debit(1, "Semesterticket", 3800),
            Item.debit(3, "Miete WG-Zimmer – Hausverwaltung Main", 42000),
            Item.debit(5, "Handy-Vertrag Funkfix", 1500),
            Item.debit(5, "Streaming-Abo", 1299),
            Item.card(4, "Supermarkt Frisch", 7000),
            Item.card(8, "Bäckerei Schmidt", 1200),
            Item.card(11, "Supermarkt Frisch", 7000),
            Item.card(14, "Kino am Main", 2200),
            Item.card(16, "Online-Shop Elektro", 6000),
            Item.card(18, "Supermarkt Frisch", 7000),
            Item.card(21, "Pizzeria Bella", 2800),
            Item.card(25, "Supermarkt Frisch", 7000),
            Item.card(27, "Drogerie Blume", 2500))),

    EINSTEIGER("Berufseinsteiger", List.of(
            Item.income(1, "Gehalt – Muster GmbH", 265000),
            Item.debit(2, "Fitnessstudio Mitgliedschaft", 3000),
            Item.debit(3, "Miete – Hausverwaltung Main", 85000),
            Item.debit(3, "Strom – Mainstrom", 5500),
            Item.debit(5, "Haftpflichtversicherung", 4000),
            Item.debit(5, "Handy-Vertrag Funkfix", 2500),
            Item.card(4, "Supermarkt Frisch", 8500),
            Item.card(7, "Tankstelle Nord", 6500),
            Item.card(9, "Supermarkt Frisch", 8500),
            Item.card(10, "Drogerie Blume", 3000),
            Item.card(12, "Restaurant Main-Terrasse", 4500),
            Item.card(15, "Supermarkt Frisch", 8500),
            Item.card(17, "Mode-Shop Zeil", 8000),
            Item.card(19, "Kino am Main", 2400),
            Item.card(20, "Tankstelle Nord", 6500),
            Item.card(22, "Supermarkt Frisch", 8500),
            Item.card(24, "Online-Shop Elektro", 9000),
            Item.card(26, "Bar Zum Hafen", 3500),
            Item.card(28, "Supermarkt Frisch", 8500))),

    FAMILIE("Familie", List.of(
            Item.income(1, "Gehalt – Rhein-Main Logistik AG", 390000),
            Item.income(1, "Kindergeld – Familienkasse", 25500),
            Item.debit(3, "Miete – Hausverwaltung Main", 135000),
            Item.debit(3, "Strom – Mainstrom", 11000),
            Item.debit(5, "Versicherungen – Allround Versicherung", 12000),
            Item.debit(5, "Internet – Netzwerk24", 4500),
            Item.card(2, "Supermarkt Frisch", 11000),
            Item.card(6, "Supermarkt Frisch", 11000),
            Item.card(8, "Tankstelle Nord", 9000),
            Item.card(10, "Supermarkt Frisch", 11000),
            Item.card(11, "Kinderkleidung Mode-Shop", 9000),
            Item.card(13, "Zoo-Besuch Freizeitpark", 6500),
            Item.card(14, "Supermarkt Frisch", 11000),
            Item.card(18, "Supermarkt Frisch", 11000),
            Item.card(20, "Restaurant Main-Terrasse", 7500),
            Item.card(22, "Supermarkt Frisch", 11000),
            Item.card(23, "Tankstelle Nord", 9000),
            Item.card(25, "Drogerie Blume", 6000),
            Item.card(26, "Supermarkt Frisch", 11000)));

    /** Eine wiederkehrende Buchung des Profils. */
    public record Item(int day, BookingType type, String description, long cents, boolean variable) {
        static Item income(int day, String text, long cents) {
            return new Item(day, BookingType.INCOME, text, cents, false);
        }

        static Item debit(int day, String text, long cents) {
            return new Item(day, BookingType.DIRECT_DEBIT, text, cents, false);
        }

        static Item card(int day, String text, long cents) {
            return new Item(day, BookingType.CARD, text, cents, true);
        }
    }

    private final String label;
    private final List<Item> items;

    DemoProfile(String label, List<Item> items) {
        this.label = label;
        this.items = items;
    }

    public String label() { return label; }
    public List<Item> items() { return items; }
}
