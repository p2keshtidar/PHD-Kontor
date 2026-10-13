package de.frauas.girokonto.insights;

import static org.assertj.core.api.Assertions.assertThat;

import de.frauas.girokonto.booking.BookingType;
import org.junit.jupiter.api.Test;

class CategoryServiceTest {

    private final CategoryService service = new CategoryService();

    @Test
    void nachBuchungstyp() {
        assertThat(service.categorize(BookingType.INITIAL, "Startguthaben", 100)).isEqualTo(Category.STARTGUTHABEN);
        assertThat(service.categorize(BookingType.SAVING_RATE, "Sparrate", -100)).isEqualTo(Category.SPAREN);
        assertThat(service.categorize(BookingType.LOAN_RATE, "Kreditrate", -100)).isEqualTo(Category.KREDIT);
        assertThat(service.categorize(BookingType.LOAN_PAYOUT, "Auszahlung", 100)).isEqualTo(Category.KREDIT_AUSZAHLUNG);
    }

    @Test
    void einnahmen() {
        assertThat(service.categorize(BookingType.INCOME, "Gehalt – Muster GmbH", 100)).isEqualTo(Category.GEHALT);
        assertThat(service.categorize(BookingType.TRANSFER, "Pizza", 100)).isEqualTo(Category.SONSTIGE_EINNAHMEN);
    }

    @Test
    void ausgabenNachSchluesselwort() {
        assertThat(service.categorize(BookingType.DIRECT_DEBIT, "Miete – Hausverwaltung Main", -1)).isEqualTo(Category.WOHNEN);
        assertThat(service.categorize(BookingType.CARD, "Supermarkt Frisch", -1)).isEqualTo(Category.LEBENSMITTEL);
        // eigene Einträge: typische Bezeichnungen
        assertThat(service.categorize(BookingType.ENTRY_EXPENSE, "Urlaub Mallorca", -1)).isEqualTo(Category.FREIZEIT);
        assertThat(service.categorize(BookingType.ENTRY_EXPENSE, "Flug nach Teheran", -1)).isEqualTo(Category.FREIZEIT);
        assertThat(service.categorize(BookingType.ENTRY_EXPENSE, "Miete", -1)).isEqualTo(Category.WOHNEN);
        assertThat(service.categorize(BookingType.ENTRY_EXPENSE, "Kfz-Versicherung", -1)).isEqualTo(Category.VERTRAEGE);
        assertThat(service.categorize(BookingType.CARD, "Tankstelle Nord", -1)).isEqualTo(Category.MOBILITAET);
        assertThat(service.categorize(BookingType.CARD, "Kino am Main", -1)).isEqualTo(Category.FREIZEIT);
        assertThat(service.categorize(BookingType.CARD, "Online-Shop Elektro", -1)).isEqualTo(Category.SHOPPING);
        assertThat(service.categorize(BookingType.DIRECT_DEBIT, "Streaming-Abo", -1)).isEqualTo(Category.VERTRAEGE);
        assertThat(service.categorize(BookingType.TRANSFER, "Geschenk", -1)).isEqualTo(Category.SONSTIGES);
    }

    @Test
    void alleDemoBuchungenHabenSinnvolleKategorien() {
        for (DemoProfile p : DemoProfile.values()) {
            for (DemoProfile.Item item : p.items()) {
                long sign = item.type() == BookingType.INCOME ? 1 : -1;
                Category c = service.categorize(item.type(), item.description(), sign * item.cents());
                if (item.type() == BookingType.INCOME) {
                    assertThat(c.isIncome()).as(item.description()).isTrue();
                } else {
                    assertThat(c).as(item.description()).isNotEqualTo(Category.SONSTIGES);
                }
            }
        }
    }
}
