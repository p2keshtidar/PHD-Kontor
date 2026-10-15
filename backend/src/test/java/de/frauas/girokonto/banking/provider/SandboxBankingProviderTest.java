package de.frauas.girokonto.banking.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.frauas.girokonto.banking.BankingProperties;
import de.frauas.girokonto.banking.provider.BankingProvider.ConsentStatus;
import de.frauas.girokonto.banking.provider.BankingProvider.TransactionData;
import de.frauas.girokonto.common.IbanGenerator;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** B01–B08: Sandbox-Bank (Testdaten). */
class SandboxBankingProviderTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    // Freitag, 26.06.2026, 12:00
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 26);

    private final SandboxBankingProvider sandbox = provider(TODAY);

    static SandboxBankingProvider provider(LocalDate today) {
        Clock clock = Clock.fixed(today.atTime(12, 0).atZone(ZONE).toInstant(), ZONE);
        BankingProperties props = new BankingProperties(true, false, 360, 3, 0, 90, "http://localhost/cb",
                new BankingProperties.GoCardless(null, null, null));
        return new SandboxBankingProvider(clock, new ObjectMapper(), props);
    }

    private String linked(String bank) {
        String ref = sandbox.startConsent(bank, null, "ref", "de").consentRef();
        sandbox.decide(ref, true);
        return ref;
    }

    @Test
    void B01_gleicherAbrufGleicheDatenUndEindeutigeKennungen() {
        String ref = linked("SANDBOX_MAINBANK");
        List<TransactionData> a = sandbox.transactions(ref, "SANDBOX_MAINBANK-giro", TODAY.minusDays(90));
        List<TransactionData> b = sandbox.transactions(ref, "SANDBOX_MAINBANK-giro", TODAY.minusDays(90));
        assertThat(a).isNotEmpty().isEqualTo(b);
        Set<String> ids = new HashSet<>();
        a.forEach(t -> assertThat(ids.add(t.externalId())).as(t.externalId()).isTrue());
        assertThat(a).allMatch(t -> t.raw().contains(t.externalId()));
    }

    @Test
    void B02_letzteDreiTageVorgemerktDanachGebuchtMitNeuerKennung() {
        String ref = linked("SANDBOX_MAINBANK");
        List<TransactionData> now = sandbox.transactions(ref, "SANDBOX_MAINBANK-giro", TODAY.minusDays(10));
        assertThat(now).filteredOn(TransactionData::pending)
                .allMatch(t -> !t.bookingDate().isBefore(TODAY.minusDays(2)) && t.externalId().startsWith("P-"));
        assertThat(now).filteredOn(t -> !t.pending())
                .allMatch(t -> t.bookingDate().isBefore(TODAY.minusDays(2)) && t.externalId().startsWith("B-"));

        // 5 Tage später: die frühere Vormerkung der Bäckerei ist gebucht, die Hotel-Reservierung nie
        SandboxBankingProvider later = provider(TODAY.plusDays(5));
        String ref2 = later.startConsent("SANDBOX_MAINBANK", null, "r", "de").consentRef();
        later.decide(ref2, true);
        List<TransactionData> after = later.transactions(ref2, "SANDBOX_MAINBANK-giro", TODAY.minusDays(10));
        assertThat(after).anyMatch(t -> t.externalId().equals("B-giro-10-" + TODAY.toString().replace("-", "")));
        assertThat(after).noneMatch(t -> !t.pending() && t.counterparty().equals("Hotel am Main"));
        assertThat(now).anyMatch(t -> t.pending() && t.counterparty().equals("Hotel am Main"));
    }

    @Test
    void B03_ruecklastschriftJedenViertenMonat() {
        String ref = linked("SANDBOX_MAINBANK");
        List<TransactionData> t = sandbox.transactions(ref, "SANDBOX_MAINBANK-giro", LocalDate.of(2026, 4, 1));
        TransactionData reversal = t.stream().filter(TransactionData::reversal).findFirst().orElseThrow();
        assertThat(reversal.bookingDate()).isEqualTo(LocalDate.of(2026, 4, 13));
        assertThat(reversal.amountCents()).isEqualTo(2_990);
        assertThat(reversal.reversalOf()).isEqualTo("B-giro-5-20260410");
        assertThat(t).anyMatch(x -> x.externalId().equals("B-giro-5-20260410") && x.amountCents() == -2_990);
        assertThat(t).filteredOn(TransactionData::reversal).hasSize(1);   // Mai/Juni: keine
    }

    @Test
    void B04_saldoAusAllenGebuchtenUmsaetzenUndGueltigeIban() {
        String ref = linked("SANDBOX_MAINBANK");
        var account = sandbox.account(ref, "SANDBOX_MAINBANK-tagesgeld");
        long sum = SandboxBankingProvider.openingBalance("tagesgeld");
        for (TransactionData t : sandbox.generate("SANDBOX_MAINBANK", "tagesgeld", SandboxBankingProvider.ANCHOR, TODAY)) {
            if (!t.pending()) {
                sum += t.amountCents();
            }
        }
        assertThat(account.balanceCents()).isEqualTo(sum);
        assertThat(account.name()).isEqualTo("Tagesgeld");
        IbanGenerator check = new IbanGenerator();
        assertThat(check.isValid(account.iban())).isTrue();
        assertThat(account.iban()).startsWith("DE").contains("00099001");   // Test-BLZ, keine echte Bank
        // Sparrate vom Girokonto trägt die IBAN des Tagesgeldkontos (eigene Umbuchung erkennbar)
        assertThat(sandbox.transactions(ref, "SANDBOX_MAINBANK-giro", TODAY.minusDays(40)))
                .anyMatch(t -> account.iban().equals(t.counterpartyIban()));
    }

    @Test
    void B05_freigabeAusstehendBestaetigtAbgelehnt() {
        String ref = sandbox.startConsent("SANDBOX_ONLINEBANK", null, "x", "de").consentRef();
        assertThat(sandbox.consentState(ref).status()).isEqualTo(ConsentStatus.PENDING);
        sandbox.decide(ref, false);
        assertThat(sandbox.consentState(ref).status()).isEqualTo(ConsentStatus.REJECTED);
        sandbox.decide(ref, true);
        var state = sandbox.consentState(ref);
        assertThat(state.status()).isEqualTo(ConsentStatus.LINKED);
        assertThat(state.accountIds()).containsExactly("SANDBOX_ONLINEBANK-online");
        assertThatThrownBy(() -> sandbox.account(ref, "SANDBOX_MAINBANK-giro"))
                .isInstanceOf(ProviderException.class);   // fremdes Konto
        assertThatThrownBy(() -> sandbox.decide("gocardless-123", true)).isInstanceOf(ProviderException.class);
    }

    @Test
    void B06_stoerungZweimalDannErfolgUndAusfallImmer() {
        String flaky = linked("SANDBOX_FLAKY");
        assertThatThrownBy(() -> sandbox.transactions(flaky, "SANDBOX_FLAKY-giro", TODAY.minusDays(5)))
                .isInstanceOf(ProviderException.class).matches(e -> ((ProviderException) e).transientError());
        assertThatThrownBy(() -> sandbox.transactions(flaky, "SANDBOX_FLAKY-giro", TODAY.minusDays(5)))
                .isInstanceOf(ProviderException.class);
        assertThat(sandbox.transactions(flaky, "SANDBOX_FLAKY-giro", TODAY.minusDays(5))).isNotEmpty();

        String down = linked("SANDBOX_DOWN");
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> sandbox.transactions(down, "SANDBOX_DOWN-giro", TODAY.minusDays(5)))
                    .isInstanceOf(ProviderException.class);
        }
        assertThat(sandbox.account(down, "SANDBOX_DOWN-giro").balanceCents()).isNotNull();
    }

    @Test
    void B07_kurzeFreigabeLaeuftAb() {
        String ref = linked("SANDBOX_SHORT");
        assertThat(sandbox.account(ref, "SANDBOX_SHORT-giro")).isNotNull();
        SandboxBankingProvider later = provider(TODAY.plusDays(2));
        assertThatThrownBy(() -> later.transactions(ref, "SANDBOX_SHORT-giro", TODAY))
                .isInstanceOf(ProviderException.class)
                .matches(e -> ((ProviderException) e).kind() == ProviderException.Kind.AUTH_EXPIRED);
        assertThat(later.consentState(ref).status()).isEqualTo(ConsentStatus.EXPIRED);
    }

    @Test
    void B08_kennzeichnungAlsTestdatenUndAbschaltbar() {
        assertThat(sandbox.sandbox()).isTrue();
        assertThat(sandbox.displayName()).contains("keine echte Bank");
        assertThat(sandbox.institutions("DE")).allMatch(i -> i.name().contains("(Testdaten)"));
        assertThat(sandbox.startConsent("SANDBOX_MAINBANK", null, "r", "de").authorizationUrl()).isNull();
        SandboxBankingProvider off = new SandboxBankingProvider(Clock.systemUTC(), new ObjectMapper(),
                new BankingProperties(false, false, 360, 3, 0, 90, null, null));
        assertThat(off.available()).isFalse();
    }
}
