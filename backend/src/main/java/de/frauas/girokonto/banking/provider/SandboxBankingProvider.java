package de.frauas.girokonto.banking.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.frauas.girokonto.banking.BankingProperties;
import java.math.BigInteger;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Sandbox: simulierte Banken mit festen Testdaten – KEINE echte Bank, kein echtes Geld.
 *
 * <p>Die Umsätze werden aus Vorlagen erzeugt und hängen nur vom (Simulations-)Datum ab. Dadurch liefert
 * jeder Abruf für denselben Tag exakt dieselben Daten mit denselben Kennungen – ideal, um doppelte Umsätze,
 * Vormerkungen, Stornos und Rückbuchungen zu testen:
 * <ul>
 *   <li>Umsätze der letzten 3 Tage sind vorgemerkt ("P-…"), danach gebucht ("B-…", neue Kennung wie bei vielen Banken).</li>
 *   <li>Eine Hotel-Reservierung wird nur vorgemerkt und nie gebucht (Storno).</li>
 *   <li>Jeden 4. Monat wird ein Lastschrift-Einzug zurückgebucht (Rücklastschrift).</li>
 *   <li>SANDBOX_FLAKY scheitert bei jedem Abruf zweimal (Wiederholung), SANDBOX_DOWN immer,
 *       SANDBOX_SHORT gibt nur 1 Tag Zugriff (Ablauf der Freigabe).</li>
 * </ul>
 */
@Component
public class SandboxBankingProvider implements BankingProvider {

    public static final String ID = "SANDBOX";

    /** Ab diesem Tag werden Umsätze erzeugt (für einen gleichbleibenden Kontostand). */
    static final LocalDate ANCHOR = LocalDate.of(2025, 1, 1);
    static final int PENDING_DAYS = 3;
    private static final DateTimeFormatter ID_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM.yyyy");

    private record Bank(String id, String name, int accessDays, List<String> accounts) {}

    private static final List<Bank> BANKS = List.of(
            new Bank("SANDBOX_MAINBANK", "Sandbox-Hausbank (Testdaten)", 90, List.of("giro", "tagesgeld")),
            new Bank("SANDBOX_ONLINEBANK", "Sandbox-Onlinebank (Testdaten)", 90, List.of("online")),
            new Bank("SANDBOX_FLAKY", "Sandbox-Bank mit Störungen (Testdaten)", 90, List.of("giro")),
            new Bank("SANDBOX_DOWN", "Sandbox-Bank nicht erreichbar (Testdaten)", 90, List.of("giro")),
            new Bank("SANDBOX_SHORT", "Sandbox-Bank mit 1-Tag-Freigabe (Testdaten)", 1, List.of("giro")));

    private final Clock clock;
    private final ObjectMapper json;
    private final boolean enabled;
    /** simulierte Entscheidung "bei der Bank": Freigabe-Kennung → bestätigt? */
    private final Map<String, Boolean> decisions = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();

    public SandboxBankingProvider(Clock clock, ObjectMapper json, BankingProperties props) {
        this.clock = clock;
        this.json = json;
        this.enabled = props.sandboxEnabled();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Sandbox (Testdaten, keine echte Bank)";
    }

    @Override
    public boolean sandbox() {
        return true;
    }

    @Override
    public boolean available() {
        return enabled;
    }

    @Override
    public Duration minSyncInterval() {
        return Duration.ZERO;
    }

    @Override
    public List<Institution> institutions(String country) {
        return BANKS.stream()
                .map(b -> new Institution(b.id(), b.name(), "SANDDEFFXXX", 730, b.accessDays(), null))
                .toList();
    }

    @Override
    public ConsentStart startConsent(String institutionId, String redirectUrl, String reference, String language) {
        Bank bank = bank(institutionId);
        long expires = Instant.now(clock).plus(Duration.ofDays(bank.accessDays())).getEpochSecond();
        String ref = "sbx_" + bank.id() + "_" + UUID.randomUUID() + "_" + expires;
        decisions.remove(ref);
        // keine echte Bankseite: die App zeigt einen deutlich gekennzeichneten Test-Dialog
        return new ConsentStart(ref, null);
    }

    /** Simuliert die Entscheidung des Nutzers auf der (nicht vorhandenen) Bankseite. */
    public void decide(String consentRef, boolean approve) {
        parse(consentRef);
        decisions.put(consentRef, approve);
    }

    @Override
    public ConsentState consentState(String consentRef) {
        Ref r = parse(consentRef);
        Boolean decision = decisions.get(consentRef);
        if (Instant.now(clock).isAfter(r.expires())) {
            return new ConsentState(ConsentStatus.EXPIRED, List.of(), r.expires());
        }
        if (decision == null) {
            return new ConsentState(ConsentStatus.PENDING, List.of(), r.expires());
        }
        if (!decision) {
            return new ConsentState(ConsentStatus.REJECTED, List.of(), r.expires());
        }
        List<String> ids = r.bank().accounts().stream().map(a -> r.bank().id() + "-" + a).toList();
        return new ConsentState(ConsentStatus.LINKED, ids, r.expires());
    }

    @Override
    public AccountData account(String consentRef, String accountId) {
        Ref r = check(consentRef);
        String acc = accountKey(r, accountId);
        LocalDate today = LocalDate.now(clock);
        long balance = openingBalance(acc);
        for (TransactionData t : generate(r.bank().id(), acc, ANCHOR, today)) {
            if (!t.pending()) {
                balance += t.amountCents();
            }
        }
        String name = switch (acc) {
            case "tagesgeld" -> "Tagesgeld";
            case "online" -> "Online-Konto";
            default -> "Girokonto";
        };
        return new AccountData(accountId, iban(r.bank().id(), acc), name, "EUR", balance, "closingBooked",
                Instant.now(clock));
    }

    @Override
    public List<TransactionData> transactions(String consentRef, String accountId, LocalDate from) {
        Ref r = check(consentRef);
        String acc = accountKey(r, accountId);
        if (r.bank().id().equals("SANDBOX_DOWN")) {
            throw new ProviderException(ProviderException.Kind.TRANSIENT, "Sandbox-Bank nicht erreichbar (Test)");
        }
        if (r.bank().id().equals("SANDBOX_FLAKY")
                && calls.computeIfAbsent(consentRef, k -> new AtomicInteger()).incrementAndGet() % 3 != 0) {
            throw new ProviderException(ProviderException.Kind.TRANSIENT, "Sandbox-Bank: vorübergehender Fehler (Test)");
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate start = from.isBefore(ANCHOR) ? ANCHOR : from;
        return generate(r.bank().id(), acc, start, today);
    }

    @Override
    public void revoke(String consentRef) {
        decisions.remove(consentRef);
        calls.remove(consentRef);
    }

    // ------------------------------------------------------------------ Testdaten

    /** Alle Umsätze eines Kontos zwischen from und today (beide einschließlich). */
    List<TransactionData> generate(String bankId, String acc, LocalDate from, LocalDate today) {
        List<TransactionData> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
            boolean pending = !d.isBefore(today.minusDays(PENDING_DAYS - 1L));
            switch (acc) {
                case "tagesgeld" -> tagesgeld(bankId, d, pending, out);
                case "online" -> online(bankId, d, pending, out);
                default -> giro(bankId, d, pending, out);
            }
        }
        return out;
    }

    private void giro(String bankId, LocalDate d, boolean pending, List<TransactionData> out) {
        String m = d.format(MONTH);
        int day = d.getDayOfMonth();
        if (day == 1) {
            add(out, bankId, "giro", 1, d, pending, 265_000, "Muster GmbH", iban("PARTNER", "arbeitgeber"),
                    "Lohn/Gehalt " + m, "PMNT-RCDT-ESCT", false, null);
        }
        if (day == 2) {
            add(out, bankId, "giro", 2, d, pending, -85_000, "Hausverwaltung Schmidt", iban("PARTNER", "vermieter"),
                    "Miete " + m, "PMNT-ICDT-STDO", false, null);
        }
        if (day == 3 && bankId.equals("SANDBOX_MAINBANK")) {
            add(out, bankId, "giro", 3, d, pending, -80_000, "Max Muster", iban(bankId, "tagesgeld"),
                    "Sparrate Tagesgeld", "PMNT-ICDT-STDO", false, null);
        }
        if (day == 5) {
            add(out, bankId, "giro", 4, d, pending, -6_200, "Stadtwerke Frankfurt", iban("PARTNER", "stadtwerke"),
                    "Abschlag Strom " + m, "PMNT-RDDT-ESDD", false, null);
        }
        if (day == 10) {
            add(out, bankId, "giro", 5, d, pending, -2_990, "FitX Fitnessstudio", iban("PARTNER", "fitness"),
                    "Mitgliedsbeitrag " + m, "PMNT-RDDT-ESDD", false, null);
        }
        if (day == 13 && d.getMonthValue() % 4 == 0) {
            // Rücklastschrift des Mitgliedsbeitrags vom 10.
            add(out, bankId, "giro", 6, d, pending, 2_990, "FitX Fitnessstudio", iban("PARTNER", "fitness"),
                    "Rücklastschrift Mitgliedsbeitrag " + m, "PMNT-RDDT-UPDD", true,
                    "B-giro-5-" + d.withDayOfMonth(10).format(ID_DATE));
        }
        if (day == 15) {
            add(out, bankId, "giro", 7, d, pending, -1_999, "Mobilfunk AG", iban("PARTNER", "mobilfunk"),
                    "Handyvertrag " + m, "PMNT-RDDT-ESDD", false, null);
        }
        if (day == 20 && d.getMonthValue() % 2 == 0) {
            add(out, bankId, "giro", 8, d, pending, -2_400, "Kino Metropolis", null,
                    "Kartenzahlung", "PMNT-CCRD-POSD", false, null);
        }
        if (d.getDayOfWeek() == DayOfWeek.SATURDAY) {
            long amount = -(2_500 + Math.floorMod(d.toEpochDay() * 37, 4_000));
            add(out, bankId, "giro", 9, d, pending, amount, "REWE Markt", null,
                    "Kartenzahlung", "PMNT-CCRD-POSD", false, null);
        }
        add(out, bankId, "giro", 10, d, pending, -320, "Bäckerei Huber", null,
                "Kartenzahlung", "PMNT-CCRD-POSD", false, null);
        if (pending && Math.floorMod(d.toEpochDay(), 3) == 0) {
            // wird nur vorgemerkt und nie gebucht → Storno
            add(out, bankId, "giro", 11, d, true, -12_000, "Hotel am Main", null,
                    "Kartenreservierung", "PMNT-CCRD-POSD", false, null);
        }
    }

    private void tagesgeld(String bankId, LocalDate d, boolean pending, List<TransactionData> out) {
        if (d.getDayOfMonth() == 3) {
            add(out, bankId, "tagesgeld", 1, d, pending, 80_000, "Max Muster", iban(bankId, "giro"),
                    "Sparrate Tagesgeld", "PMNT-RCDT-ESCT", false, null);
        }
        if (d.equals(d.withDayOfMonth(d.lengthOfMonth()))) {
            add(out, bankId, "tagesgeld", 2, d, pending, 125, "Sandbox-Hausbank", null,
                    "Zinsgutschrift", "ACMT-MDOP-INTR", false, null);
        }
    }

    private void online(String bankId, LocalDate d, boolean pending, List<TransactionData> out) {
        if (d.getDayOfMonth() == 1) {
            add(out, bankId, "online", 1, d, pending, 45_000, "Café Linde", iban("PARTNER", "cafe"),
                    "Lohn Nebenjob", "PMNT-RCDT-ESCT", false, null);
        }
        if (d.getDayOfMonth() == 12) {
            add(out, bankId, "online", 2, d, pending, -1_299, "Streaming Plus", null,
                    "Abo Streaming", "PMNT-RDDT-ESDD", false, null);
        }
        if (d.getDayOfWeek() == DayOfWeek.TUESDAY || d.getDayOfWeek() == DayOfWeek.FRIDAY) {
            long amount = -(800 + Math.floorMod(d.toEpochDay() * 13, 2_500));
            add(out, bankId, "online", 3, d, pending, amount, "Online-Shop Elektro", null,
                    "Bestellung", "PMNT-CCRD-POSD", false, null);
        }
        if (d.getDayOfMonth() == 25) {
            add(out, bankId, "online", 4, d, pending, -5_500, "Aral Tankstelle", null,
                    "Tanken", "PMNT-CCRD-POSD", false, null);
        }
    }

    private void add(List<TransactionData> out, String bankId, String acc, int template, LocalDate d, boolean pending,
                     long cents, String counterparty, String cpIban, String remittance, String code, boolean reversal,
                     String reversalOf) {
        String id = (pending ? "P-" : "B-") + acc + "-" + template + "-" + d.format(ID_DATE);
        ObjectNode raw = json.createObjectNode();
        raw.put(pending ? "internalTransactionId" : "transactionId", id);
        if (!pending) {
            raw.put("bookingDate", d.toString());
        }
        raw.put("valueDate", d.toString());
        ObjectNode amount = raw.putObject("transactionAmount");
        amount.put("amount", String.format(java.util.Locale.ROOT, "%.2f", cents / 100.0));
        amount.put("currency", "EUR");
        raw.put(cents < 0 ? "creditorName" : "debtorName", counterparty);
        if (cpIban != null) {
            raw.putObject(cents < 0 ? "creditorAccount" : "debtorAccount").put("iban", cpIban);
        }
        raw.put("remittanceInformationUnstructured", remittance);
        raw.put("bankTransactionCode", code);
        raw.put("sandbox", true);
        out.add(new TransactionData(id, pending, d, d, cents, "EUR", counterparty, cpIban, remittance, code,
                reversal, reversalOf, raw.toString()));
    }

    static long openingBalance(String acc) {
        return switch (acc) {
            case "tagesgeld" -> 500_000;
            case "online" -> 30_000;
            default -> 150_000;
        };
    }

    /** Gültige Test-IBAN (Prüfziffern korrekt) mit einer Bankleitzahl, die keiner echten Bank gehört. */
    static String iban(String bankId, String acc) {
        long number = Math.floorMod((bankId + "/" + acc).hashCode(), 9_000_000_000L) + 1_000_000_000L;
        String bban = "00099001" + String.format("%010d", number);
        int check = 98 - new BigInteger(bban + "131400").mod(BigInteger.valueOf(97)).intValue();
        return "DE" + String.format("%02d", check) + bban;
    }

    // ------------------------------------------------------------------ Freigabe-Kennung

    private record Ref(Bank bank, Instant expires) {}

    private Ref parse(String consentRef) {
        if (consentRef == null || !consentRef.startsWith("sbx_")) {
            throw new ProviderException(ProviderException.Kind.PERMANENT, "Unbekannte Sandbox-Freigabe");
        }
        int last = consentRef.lastIndexOf('_');
        String withoutExpiry = consentRef.substring(0, last);
        String bankId = withoutExpiry.substring(4, withoutExpiry.lastIndexOf('_'));
        return new Ref(bank(bankId), Instant.ofEpochSecond(Long.parseLong(consentRef.substring(last + 1))));
    }

    private Ref check(String consentRef) {
        Ref r = parse(consentRef);
        if (Instant.now(clock).isAfter(r.expires())) {
            throw new ProviderException(ProviderException.Kind.AUTH_EXPIRED, "Freigabe abgelaufen (Sandbox)");
        }
        return r;
    }

    private static String accountKey(Ref r, String accountId) {
        String prefix = r.bank().id() + "-";
        if (accountId == null || !accountId.startsWith(prefix) || !r.bank().accounts().contains(accountId.substring(prefix.length()))) {
            throw new ProviderException(ProviderException.Kind.PERMANENT, "Unbekanntes Sandbox-Konto");
        }
        return accountId.substring(prefix.length());
    }

    private static Bank bank(String id) {
        return BANKS.stream().filter(b -> b.id().equals(id)).findFirst()
                .orElseThrow(() -> new ProviderException(ProviderException.Kind.PERMANENT, "Unbekannte Sandbox-Bank"));
    }
}
