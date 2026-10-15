package de.frauas.girokonto.loan.provider;

import static org.assertj.core.api.Assertions.assertThat;

import de.frauas.girokonto.loan.CreditClass;
import de.frauas.girokonto.loan.LoanMath;
import de.frauas.girokonto.loan.LoanPurpose;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** K01–K05: Anbieter "fiktive Banken" (Simulation) – Rechenbeispiele, Bonität, Kennzeichnung. */
class SimulatedBanksProviderTest {

    private final SimulatedBanksProvider provider = new SimulatedBanksProvider();

    private Map<String, LoanOfferProvider.Quote> quotes(LoanPurpose p, long principal, int term, CreditClass c) {
        return provider.quotes(new LoanOfferProvider.Query(p, principal, 0, term, c)).stream()
                .collect(Collectors.toMap(LoanOfferProvider.Quote::bankCode, q -> q));
    }

    @Test
    void K01_bonitaetGutEntsprichtDenBisherigenKonditionen() {
        var q = quotes(LoanPurpose.AUTO, 2_000_000, 60, CreditClass.GUT);
        assertThat(q).hasSize(4);
        var nordlicht = q.get("NORDLICHT");
        assertThat(nordlicht.interestRate()).isEqualByComparingTo("5.59");
        assertThat(nordlicht.monthlyRateCents()).isEqualTo(38_286);
        assertThat(nordlicht.totalCents()).isEqualTo(2_000_000 + nordlicht.totalInterestCents());
        assertThat(nordlicht.totalInterestCents()).isEqualTo(LoanMath.schedule(2_000_000, new BigDecimal("5.59"), 60)
                .stream().mapToLong(LoanMath.Row::interestCents).sum());
        assertThat(q.values()).allMatch(LoanOfferProvider.Quote::eligible).allMatch(x -> x.feesCents() == 0);
    }

    @Test
    void K02_effektiverJahreszinsAusDemSollzins() {
        assertThat(SimulatedBanksProvider.effectiveRate(new BigDecimal("5.59"))).isEqualByComparingTo("5.74");
        assertThat(SimulatedBanksProvider.effectiveRate(new BigDecimal("5.49"))).isEqualByComparingTo("5.63");
        assertThat(SimulatedBanksProvider.effectiveRate(new BigDecimal("3.79"))).isEqualByComparingTo("3.86");
        var q = quotes(LoanPurpose.SONSTIGES, 500_000, 24, CreditClass.GUT);
        assertThat(q.values()).allMatch(x -> x.effectiveRate().compareTo(x.interestRate()) > 0);
    }

    @Test
    void K03_schwaechereBonitaetKostetMehrUndSchliesstBankenAus() {
        var gut = quotes(LoanPurpose.AUTO, 1_000_000, 48, CreditClass.GUT);
        var mittel = quotes(LoanPurpose.AUTO, 1_000_000, 48, CreditClass.MITTEL);
        var schwach = quotes(LoanPurpose.AUTO, 1_000_000, 48, CreditClass.SCHWACH);
        var sehrGut = quotes(LoanPurpose.AUTO, 1_000_000, 48, CreditClass.SEHR_GUT);
        assertThat(mittel.get("NORDLICHT").interestRate().subtract(gut.get("NORDLICHT").interestRate()))
                .isEqualByComparingTo("1.50");
        assertThat(sehrGut.get("NORDLICHT").interestRate().subtract(gut.get("NORDLICHT").interestRate()))
                .isEqualByComparingTo("-0.50");
        assertThat(mittel.get("MAINUFER").eligible()).isFalse();
        assertThat(mittel.get("MAINUFER").ineligibleReason()).isEqualTo("Die Mainufer Bank bietet erst ab Bonität „gut“ an.");
        assertThat(mittel.get("NORDLICHT").eligible()).isTrue();
        assertThat(schwach.values().stream().filter(LoanOfferProvider.Quote::eligible).map(LoanOfferProvider.Quote::bankCode))
                .containsExactly("TAUNUS");
        // Immobilie: kleinerer Aufschlag
        var haus = quotes(LoanPurpose.IMMOBILIE, 20_000_000, 240, CreditClass.MITTEL);
        var hausGut = quotes(LoanPurpose.IMMOBILIE, 20_000_000, 240, CreditClass.GUT);
        assertThat(haus.get("TAUNUS").interestRate().subtract(hausGut.get("TAUNUS").interestRate()))
                .isEqualByComparingTo("0.60");
    }

    @Test
    void K04_alsSimulationUndUnverbindlichGekennzeichnet() {
        assertThat(provider.simulation()).isTrue();
        assertThat(provider.supportsApplications()).isTrue();
        assertThat(provider.status().state()).isEqualTo("OK");
        assertThat(provider.status().message()).contains("keine echten Angebote");
        var q = quotes(LoanPurpose.AUTO, 2_000_000, 60, CreditClass.GUT).get("NORDLICHT");
        assertThat(q.exampleText()).isEqualTo("Unverbindliches Rechenbeispiel (Simulation, fiktive Bank): "
                + "Nettodarlehensbetrag 20.000,00 €, Laufzeit 60 Monate, gebundener Sollzins 5,59 % p. a., "
                + "effektiver Jahreszins 5,74 %, monatliche Rate 382,86 €, Gesamtbetrag "
                + de.frauas.girokonto.common.Money.format(q.totalCents()) + ".");
        assertThat(q.conditions()).contains("Keine Bearbeitungsgebühr", "Sondertilgungen jederzeit kostenlos");
        assertThat(q.requirements()).first().asString().isEqualTo("Bonität mindestens „mittel“ (Selbsteinschätzung)");
        assertThat(q.requirements()).contains("Rate höchstens 70 % des frei verfügbaren Betrags");
    }

    @Test
    void K05_bonitaetsklassenEinlesen() {
        assertThat(CreditClass.parse(null)).isEqualTo(CreditClass.GUT);
        assertThat(CreditClass.parse("SCHWACH")).isEqualTo(CreditClass.SCHWACH);
        assertThat(CreditClass.parse("EXZELLENT")).isNull();
        assertThat(CreditClass.SEHR_GUT.atLeast(CreditClass.GUT)).isTrue();
        assertThat(CreditClass.MITTEL.atLeast(CreditClass.GUT)).isFalse();
        assertThat(List.of(CreditClass.values())).extracting(CreditClass::label)
                .containsExactly("sehr gut", "gut", "mittel", "schwach");
    }
}
