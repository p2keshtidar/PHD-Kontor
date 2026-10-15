package de.frauas.girokonto.banking;

import de.frauas.girokonto.audit.AuditLog;
import de.frauas.girokonto.banking.BankingDtos.AccountDto;
import de.frauas.girokonto.banking.BankingDtos.AccountsOverview;
import de.frauas.girokonto.banking.BankingDtos.CompleteResult;
import de.frauas.girokonto.banking.BankingDtos.ConnectResult;
import de.frauas.girokonto.banking.BankingDtos.ConnectionDto;
import de.frauas.girokonto.banking.BankingDtos.InstitutionDto;
import de.frauas.girokonto.banking.BankingDtos.ProviderDto;
import de.frauas.girokonto.banking.BankingDtos.RevokeResult;
import de.frauas.girokonto.banking.BankingDtos.SyncResult;
import de.frauas.girokonto.banking.BankingDtos.TransactionDto;
import de.frauas.girokonto.banking.BankingDtos.UpdateTransactionRequest;
import de.frauas.girokonto.banking.provider.BankingProvider;
import de.frauas.girokonto.banking.provider.BankingProvider.AccountData;
import de.frauas.girokonto.banking.provider.BankingProvider.ConsentState;
import de.frauas.girokonto.banking.provider.BankingProvider.Institution;
import de.frauas.girokonto.banking.provider.BankingProvider.TransactionData;
import de.frauas.girokonto.banking.provider.ProviderException;
import de.frauas.girokonto.banking.provider.SandboxBankingProvider;
import de.frauas.girokonto.booking.BookingType;
import de.frauas.girokonto.card.CardCrypto;
import de.frauas.girokonto.common.ApiException;
import de.frauas.girokonto.common.PageDto;
import de.frauas.girokonto.insights.Category;
import de.frauas.girokonto.insights.CategoryService;
import de.frauas.girokonto.notification.NotificationService;
import de.frauas.girokonto.notification.NotificationType;
import de.frauas.girokonto.simulation.DailyJob;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Mehrere echte Bankkonten verbinden (Open Banking/PSD2, nur Lesezugriff): Freigabe, erster Import,
 * regelmäßiger Abgleich, Vormerkungen/Stornos/Rückbuchungen, eigene Bearbeitungen, Widerruf.
 *
 * <p>Grundsätze: Keine Bank-Passwörter (Anmeldung bei der Bank), Freigabe-Kennung nur verschlüsselt,
 * Originaldaten der Bank unverändert (verschlüsselt) und eigene Bearbeitungen getrennt davon,
 * keine Zahlungen – es werden ausschließlich Kontoinformationen gelesen.
 */
@Service
public class BankingService implements DailyJob {

    private static final Logger log = LoggerFactory.getLogger(BankingService.class);

    static final int MAX_CONNECTIONS = 10;
    /** Vormerkungen werden bis zu so vielen Tagen später gebucht (Abgleich). */
    static final int PENDING_MATCH_DAYS = 7;
    /** Überlappung beim inkrementellen Abruf (späte Buchungen der Bank). */
    static final int OVERLAP_DAYS = 5;
    static final List<String> PERMISSIONS = List.of("Kontodetails lesen", "Salden lesen", "Umsätze lesen");
    private static final Set<Category> USER_CATEGORIES;

    static {
        Set<Category> s = new LinkedHashSet<>();
        for (Category c : Category.values()) {
            if (c.isUserSelectable()) {
                s.add(c);
            }
        }
        s.add(Category.UMBUCHUNG);
        s.add(Category.SPAREN);
        USER_CATEGORIES = Set.copyOf(s);
    }

    private final JdbcTemplate jdbc;
    private final Map<String, BankingProvider> providers = new LinkedHashMap<>();
    private final CardCrypto crypto;
    private final CategoryService categories;
    private final NotificationService notifications;
    private final AuditLog audit;
    private final BankingProperties props;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final Map<String, CachedInstitutions> institutionCache = new ConcurrentHashMap<>();

    private record CachedInstitutions(Instant loaded, List<Institution> list) {}

    public BankingService(JdbcTemplate jdbc, List<BankingProvider> providerList, CardCrypto crypto,
                          CategoryService categories, NotificationService notifications, AuditLog audit,
                          BankingProperties props, Clock clock, TransactionTemplate tx) {
        this.jdbc = jdbc;
        providerList.forEach(p -> providers.put(p.id(), p));
        this.crypto = crypto;
        this.categories = categories;
        this.notifications = notifications;
        this.audit = audit;
        this.props = props;
        this.clock = clock;
        this.tx = tx;
    }

    // ================================================================== Anbieter und Banken

    public List<ProviderDto> providers() {
        return providers.values().stream()
                .filter(BankingProvider::available)
                .map(p -> new ProviderDto(p.id(), p.displayName(), p.sandbox(), true, p.sandbox()
                        ? "Simulierte Banken mit Testdaten – keine echte Bank, kein echtes Geld."
                        : "Echte Bank über einen zugelassenen Kontoinformationsdienst (PSD2). Nur Lesezugriff."))
                .toList();
    }

    public List<InstitutionDto> institutions(String providerId, String country) {
        BankingProvider p = provider(providerId);
        return institutionList(p, country).stream()
                .map(i -> new InstitutionDto(i.id(), i.name(), i.bic(), i.historyDays(), i.accessDays(), i.logo(),
                        p.sandbox()))
                .toList();
    }

    private List<Institution> institutionList(BankingProvider p, String country) {
        String key = p.id() + "/" + (country == null ? "DE" : country.toUpperCase(Locale.ROOT));
        CachedInstitutions c = institutionCache.get(key);
        if (c == null || c.loaded().isBefore(Instant.now().minus(Duration.ofHours(12)))) {
            c = new CachedInstitutions(Instant.now(), withProvider(() -> p.institutions(country)));
            institutionCache.put(key, c);
        }
        return c.list();
    }

    private BankingProvider provider(String id) {
        BankingProvider p = id == null ? null : providers.get(id.toUpperCase(Locale.ROOT));
        if (p == null || !p.available()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PROVIDER_UNAVAILABLE",
                    "Dieser Anbieter ist nicht verfügbar.");
        }
        return p;
    }

    // ================================================================== Verbinden

    public ConnectResult connect(Long userId, String providerId, String institutionId, String language) {
        BankingProvider p = provider(providerId);
        Institution inst = institutionList(p, "DE").stream().filter(i -> i.id().equals(institutionId)).findFirst()
                .orElseThrow(() -> bad("UNKNOWN_INSTITUTION", "Diese Bank wird nicht unterstützt."));

        // dieselbe Bank schon vorhanden? → aktive Verbindung: Fehler; abgelaufene/abgelehnte: erneuern
        List<Conn> same = jdbc.query("""
                SELECT * FROM bank_connection WHERE user_id = ? AND provider = ? AND institution_id = ?
                 ORDER BY id DESC
                """, this::conn, userId, p.id(), inst.id());
        for (Conn c : same) {
            if (c.status.equals("ACTIVE")) {
                throw new ApiException(HttpStatus.CONFLICT, "ALREADY_CONNECTED", "Diese Bank ist bereits verbunden.");
            }
        }
        if (!same.isEmpty()) {
            return startConsent(userId, same.get(0), p, inst.id(), language);
        }
        Integer open = jdbc.queryForObject(
                "SELECT count(*) FROM bank_connection WHERE user_id = ? AND status <> 'REVOKED'", Integer.class, userId);
        if (open != null && open >= MAX_CONNECTIONS) {
            throw new ApiException(HttpStatus.CONFLICT, "TOO_MANY_CONNECTIONS",
                    "Höchstens 10 Bankverbindungen möglich. Bitte zuerst eine entfernen.");
        }
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement("""
                    INSERT INTO bank_connection (user_id, provider, institution_id, institution_name, status, created_at)
                    VALUES (?, ?, ?, ?, 'PENDING', ?)
                    """, new String[] {"id"});
            ps.setLong(1, userId);
            ps.setString(2, p.id());
            ps.setString(3, inst.id());
            ps.setString(4, cut(inst.name(), 150));
            ps.setTimestamp(5, Timestamp.from(Instant.now(clock)));
            return ps;
        }, key);
        Conn c = load(userId, key.getKey().longValue());
        return startConsent(userId, c, p, inst.id(), language);
    }

    /** Erneuert die Freigabe einer abgelaufenen, abgelehnten oder widerrufenen Verbindung (Daten bleiben). */
    public ConnectResult renew(Long userId, Long connectionId, String language) {
        Conn c = load(userId, connectionId);
        if (c.status.equals("ACTIVE")) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_CONNECTED", "Diese Bank ist bereits verbunden.");
        }
        return startConsent(userId, c, provider(c.provider), c.institutionId, language);
    }

    private ConnectResult startConsent(Long userId, Conn c, BankingProvider p, String institutionId, String language) {
        String reference = "gk-" + c.id + "-" + UUID.randomUUID();
        BankingProvider.ConsentStart start;
        try {
            start = p.startConsent(institutionId, props.redirectUrl(), reference, language);
        } catch (ProviderException e) {
            log.warn("Freigabe bei {} nicht angelegt: {}", p.id(), e.getMessage());
            if (c.status.equals("PENDING") && c.lastSyncAt == null) {
                jdbc.update("DELETE FROM bank_connection WHERE id = ? AND status = 'PENDING' AND last_sync_at IS NULL", c.id);
            }
            throw unreachable();
        }
        jdbc.update("""
                UPDATE bank_connection SET status = 'PENDING', consent_ref_enc = ?, consent_expires_at = NULL,
                       expiry_notified = FALSE, revoked_at = NULL WHERE id = ?
                """, crypto.encrypt(start.consentRef()), c.id);
        audit.record(userId, "BANK_CONSENT_STARTED", "connection:" + c.id, p.id() + " " + institutionId);
        String next = p.sandbox()
                ? "Sandbox: Es öffnet sich keine echte Bank. Bestätige die Test-Freigabe in der App."
                : "Melde dich auf der Seite deiner Bank an und bestätige den Lesezugriff. Danach zurück zur App.";
        return new ConnectResult(dto(load(userId, c.id)), start.authorizationUrl(), p.sandbox(), next);
    }

    /** Nach der Freigabe bei der Bank: Status prüfen, Konten übernehmen, erster Import. */
    public CompleteResult complete(Long userId, Long connectionId, Boolean approve) {
        Conn c = load(userId, connectionId);
        if (!c.status.equals("PENDING") || c.consentRefEnc == null) {
            throw new ApiException(HttpStatus.CONFLICT, "NOT_PENDING", "Diese Verbindung wartet nicht auf eine Freigabe.");
        }
        BankingProvider p = provider(c.provider);
        String ref = crypto.decrypt(c.consentRefEnc);
        if (p instanceof SandboxBankingProvider sandbox && approve != null) {
            sandbox.decide(ref, approve);
        }
        ConsentState state = withProvider(() -> p.consentState(ref));
        switch (state.status()) {
            case PENDING -> throw new ApiException(HttpStatus.CONFLICT, "CONSENT_PENDING",
                    "Die Freigabe bei der Bank ist noch nicht abgeschlossen.");
            case REJECTED, EXPIRED -> {
                bestEffortRevoke(p, ref);
                String status = state.status() == BankingProvider.ConsentStatus.REJECTED ? "REJECTED" : "EXPIRED";
                jdbc.update("UPDATE bank_connection SET status = ?, consent_ref_enc = NULL WHERE id = ?", status, c.id);
                audit.record(userId, "BANK_CONSENT_" + status, "connection:" + c.id, null);
                return new CompleteResult(dto(load(userId, c.id)), null,
                        status.equals("REJECTED") ? "Die Freigabe wurde bei der Bank abgelehnt."
                                : "Die Freigabe ist abgelaufen. Bitte erneut verbinden.");
            }
            default -> {
                // LINKED
            }
        }
        List<AccountData> accounts = new ArrayList<>();
        for (String accountId : state.accountIds()) {
            accounts.add(withProvider(() -> p.account(ref, accountId)));
        }
        tx.executeWithoutResult(s -> {
            for (AccountData a : accounts) {
                upsertAccount(c, a);
            }
            jdbc.update("""
                    UPDATE bank_connection SET status = 'ACTIVE', consent_expires_at = ?, expiry_notified = FALSE,
                           failed_syncs = 0, last_error = NULL WHERE id = ?
                    """, state.expiresAt() == null ? null : Timestamp.from(state.expiresAt()), c.id);
        });
        audit.record(userId, "BANK_CONNECTED", "connection:" + c.id, accounts.size() + " Konto/Konten");
        SyncResult sync = doSync(load(userId, c.id), false);
        String msg = sync.status().equals("OK")
                ? "Bank verbunden: " + accounts.size() + " Konto/Konten, " + sync.newTransactions() + " Umsätze importiert."
                : "Bank verbunden. Der erste Abruf der Umsätze ist fehlgeschlagen und wird später wiederholt.";
        return new CompleteResult(dto(load(userId, c.id)), sync, msg);
    }

    private void upsertAccount(Conn c, AccountData a) {
        String hash = a.iban() == null ? null : crypto.fingerprint(normalizeIban(a.iban()));
        List<Long> existing = jdbc.queryForList("""
                SELECT id FROM external_account WHERE connection_id = ?
                   AND (external_id = ? OR (iban_hash IS NOT NULL AND iban_hash = ?)) ORDER BY id LIMIT 1
                """, Long.class, c.id, a.externalId(), hash);
        Timestamp at = a.balanceAt() == null ? null : Timestamp.from(a.balanceAt());
        if (existing.isEmpty()) {
            jdbc.update("""
                    INSERT INTO external_account (connection_id, user_id, external_id, iban_masked, iban_hash, name,
                           currency, balance_cents, balance_type, balance_at, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, c.id, c.userId, a.externalId(), maskIban(a.iban()), hash, cut(a.name(), 150),
                    a.currency() == null ? "EUR" : a.currency(), a.balanceCents(), a.balanceType(), at,
                    Timestamp.from(Instant.now(clock)));
        } else {
            jdbc.update("""
                    UPDATE external_account SET external_id = ?, iban_masked = ?, iban_hash = ?, name = ?, currency = ?,
                           balance_cents = ?, balance_type = ?, balance_at = ? WHERE id = ?
                    """, a.externalId(), maskIban(a.iban()), hash, cut(a.name(), 150),
                    a.currency() == null ? "EUR" : a.currency(), a.balanceCents(), a.balanceType(), at,
                    existing.get(0));
        }
    }

    // ================================================================== Abgleich (Synchronisierung)

    public SyncResult sync(Long userId, Long connectionId) {
        Conn c = load(userId, connectionId);
        if (c.status.equals("EXPIRED")) {
            throw new ApiException(HttpStatus.CONFLICT, "CONSENT_EXPIRED",
                    "Die Freigabe ist abgelaufen. Bitte die Bank neu verbinden.");
        }
        if (!c.status.equals("ACTIVE")) {
            throw new ApiException(HttpStatus.CONFLICT, "NOT_ACTIVE", "Diese Verbindung ist nicht aktiv.");
        }
        BankingProvider p = provider(c.provider);
        if (tooSoon(c.lastSyncAt, Instant.now(clock), p.minSyncInterval())) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "SYNC_TOO_SOON",
                    "Die Bank erlaubt nur wenige Abrufe pro Tag. Bitte später erneut versuchen.");
        }
        return doSync(c, false);
    }

    static boolean tooSoon(Instant lastSync, Instant now, Duration minInterval) {
        return lastSync != null && !minInterval.isZero() && now.isBefore(lastSync.plus(minInterval));
    }

    /** Hintergrund-Abruf aller fälligen Verbindungen (siehe {@link BankSyncScheduler}). */
    public int syncDue() {
        Instant now = Instant.now(clock);
        int done = 0;
        for (Conn c : jdbc.query("SELECT * FROM bank_connection WHERE status = 'ACTIVE' ORDER BY last_sync_at NULLS FIRST",
                this::conn)) {
            BankingProvider p = providers.get(c.provider);
            if (p == null || !p.available()) {
                continue;
            }
            Duration interval = Duration.ofMinutes(Math.max(props.syncIntervalMinutes(), 1));
            if (p.minSyncInterval().compareTo(interval) > 0) {
                interval = p.minSyncInterval();
            }
            if (c.lastSyncAt == null || !now.isBefore(c.lastSyncAt.plus(interval))) {
                try {
                    doSync(c, true);
                    done++;
                } catch (RuntimeException e) {
                    log.warn("Hintergrund-Abruf Verbindung {} fehlgeschlagen: {}", c.id, e.getClass().getSimpleName());
                }
            }
        }
        return done;
    }

    private record Fetched(Long accountId, AccountData account, List<TransactionData> transactions, LocalDate from) {}

    SyncResult doSync(Conn c, boolean background) {
        BankingProvider p = provider(c.provider);
        Instant now = Instant.now(clock);
        if (c.consentRefEnc == null) {
            throw new ApiException(HttpStatus.CONFLICT, "NOT_ACTIVE", "Diese Verbindung ist nicht aktiv.");
        }
        if (c.consentExpiresAt != null && now.isAfter(c.consentExpiresAt)) {
            markExpired(c);
            return new SyncResult(c.id, "EXPIRED", 0, 0, 0,
                    "Die Freigabe ist abgelaufen. Bitte die Bank neu verbinden.", now);
        }
        String ref = crypto.decrypt(c.consentRefEnc);
        LocalDate today = LocalDate.now(clock);
        List<Long[]> accounts = jdbc.query("SELECT id FROM external_account WHERE connection_id = ? ORDER BY id",
                (rs, i) -> new Long[] {rs.getLong("id")}, c.id);
        List<Fetched> fetched = new ArrayList<>();
        ProviderException failure = null;
        for (Long[] row : accounts) {
            Long accountId = row[0];
            String externalId = jdbc.queryForObject("SELECT external_id FROM external_account WHERE id = ?",
                    String.class, accountId);
            LocalDate from = fetchFrom(accountId, today);
            try {
                AccountData a = withRetry(() -> p.account(ref, externalId));
                List<TransactionData> t = withRetry(() -> p.transactions(ref, externalId, from));
                fetched.add(new Fetched(accountId, a, t, from));
            } catch (ProviderException e) {
                failure = e;
                if (e.kind() == ProviderException.Kind.AUTH_EXPIRED) {
                    break;
                }
            }
        }
        if (failure != null && failure.kind() == ProviderException.Kind.AUTH_EXPIRED) {
            markExpired(c);
            return new SyncResult(c.id, "EXPIRED", 0, 0, 0,
                    "Die Freigabe ist abgelaufen. Bitte die Bank neu verbinden.", now);
        }
        int[] counts = new int[2];
        Set<String> own = ownIbanHashes(c.userId);
        tx.executeWithoutResult(s -> {
            // Abrufe derselben Verbindung nacheinander schreiben (manuell und Hintergrund gleichzeitig)
            jdbc.queryForObject("SELECT id FROM bank_connection WHERE id = ? FOR UPDATE", Long.class, c.id);
            for (Fetched f : fetched) {
                int[] r = store(c, f, own);
                counts[0] += r[0];
                counts[1] += r[1];
            }
        });
        String status;
        String message;
        if (failure == null) {
            status = "OK";
            message = counts[0] == 0 ? "Alles aktuell – keine neuen Umsätze."
                    : counts[0] == 1 ? "1 neuer Umsatz." : counts[0] + " neue Umsätze.";
            jdbc.update("""
                    UPDATE bank_connection SET last_sync_at = ?, last_sync_status = 'OK', last_error = NULL,
                           failed_syncs = 0 WHERE id = ?
                    """, Timestamp.from(now), c.id);
        } else {
            status = fetched.isEmpty() ? "FAILED" : "PARTIAL";
            message = switch (failure.kind()) {
                case RATE_LIMITED -> "Abruf-Grenze der Bank erreicht. Nächster Versuch später.";
                case TRANSIENT -> "Die Bank war nicht erreichbar. Nächster Versuch später.";
                default -> "Abruf fehlgeschlagen. Bitte die Verbindung prüfen.";
            };
            int failed = c.failedSyncs + 1;
            jdbc.update("""
                    UPDATE bank_connection SET last_sync_at = ?, last_sync_status = ?, last_error = ?,
                           failed_syncs = ? WHERE id = ?
                    """, Timestamp.from(now), status, message, failed, c.id);
            log.info("Abruf Verbindung {}: {} ({})", c.id, status, failure.kind());
            if (background && failed == 1) {
                notifications.send(c.userId, NotificationType.BANK, "Bankabruf gestört",
                        c.institutionName + ": " + message, null);
            }
        }
        return new SyncResult(c.id, status, fetched.size(), counts[0], counts[1], message, now);
    }

    /** Erster Abruf: die letzten N Tage; danach ab der ältesten Vormerkung bzw. letzten Buchung minus Überlappung. */
    private LocalDate fetchFrom(Long accountId, LocalDate today) {
        LocalDate earliest = today.minusDays(Math.max(props.initialHistoryDays(), 1));
        LocalDate lastBooked = jdbc.queryForObject("""
                SELECT max(booking_date) FROM external_transaction WHERE external_account_id = ? AND status <> 'PENDING'
                """, LocalDate.class, accountId);
        LocalDate oldestPending = jdbc.queryForObject("""
                SELECT min(booking_date) FROM external_transaction WHERE external_account_id = ? AND status = 'PENDING'
                """, LocalDate.class, accountId);
        if (lastBooked == null && oldestPending == null) {
            return earliest;
        }
        LocalDate from = lastBooked == null ? oldestPending : lastBooked.minusDays(OVERLAP_DAYS);
        if (oldestPending != null && oldestPending.isBefore(from)) {
            from = oldestPending;
        }
        return from.isBefore(earliest) ? earliest : from;
    }

    private record Existing(Long id, String status, long amountCents, LocalDate bookingDate, String counterparty,
                            String remittance) {}

    /** Schreibt Saldo und Umsätze eines Kontos. Rückgabe: {neu, geändert}. */
    private int[] store(Conn c, Fetched f, Set<String> ownHashes) {
        AccountData a = f.account();
        jdbc.update("UPDATE external_account SET balance_cents = ?, balance_type = ?, balance_at = ?, name = ? WHERE id = ?",
                a.balanceCents(), a.balanceType(), a.balanceAt() == null ? null : Timestamp.from(a.balanceAt()),
                cut(a.name(), 150), f.accountId());

        Map<String, Existing> existing = new HashMap<>();
        jdbc.query("""
                SELECT id, external_id, status, amount_cents, booking_date, counterparty, remittance
                  FROM external_transaction WHERE external_account_id = ? AND (booking_date >= ? OR status = 'PENDING')
                """, rs -> {
            existing.put(rs.getString("external_id"), new Existing(rs.getLong("id"), rs.getString("status"),
                    rs.getLong("amount_cents"), rs.getDate("booking_date").toLocalDate(), rs.getString("counterparty"),
                    rs.getString("remittance")));
        }, f.accountId(), Date.valueOf(f.from().minusDays(1)));

        int inserted = 0;
        int updated = 0;
        Set<String> incoming = new HashSet<>();
        Instant now = Instant.now(clock);
        for (TransactionData t : f.transactions()) {
            if (!incoming.add(t.externalId())) {
                continue;   // doppelt in derselben Antwort
            }
            String status = t.pending() ? "PENDING" : "BOOKED";
            Existing e = existing.get(t.externalId());
            if (e == null) {
                e = jdbc.query("SELECT id, status, amount_cents, booking_date, counterparty, remittance FROM external_transaction"
                                + " WHERE external_account_id = ? AND external_id = ?",
                        rs -> rs.next() ? existingOf(rs) : null, f.accountId(), t.externalId());
            }
            if (e == null) {
                Category cat = autoCategory(t, ownHashes);
                int n = jdbc.update("""
                        INSERT INTO external_transaction (external_account_id, user_id, external_id, status, booking_date,
                               value_date, amount_cents, currency, counterparty, remittance, bank_code, is_reversal,
                               auto_category, raw_enc, first_seen_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (external_account_id, external_id) DO NOTHING
                        """, f.accountId(), c.userId, cut(t.externalId(), 160), status, Date.valueOf(t.bookingDate()),
                        t.valueDate() == null ? null : Date.valueOf(t.valueDate()), t.amountCents(),
                        t.currency() == null ? "EUR" : t.currency(), cut(t.counterparty(), 200), cut(t.remittance(), 500),
                        cut(t.bankCode(), 60), t.reversal(), cat.name(), crypto.encrypt(t.raw() == null ? "{}" : t.raw()),
                        Timestamp.from(now), Timestamp.from(now));
                inserted += n;
            } else {
                // Status, den Girokonto selbst gesetzt hat (Rückbuchung erkannt), bleibt erhalten
                String newStatus = e.status().equals("REVERSED") && status.equals("BOOKED") ? "REVERSED" : status;
                boolean changed = !newStatus.equals(e.status()) || e.amountCents() != t.amountCents()
                        || !e.bookingDate().equals(t.bookingDate()) || !Objects.equals(e.counterparty(), cut(t.counterparty(), 200))
                        || !Objects.equals(e.remittance(), cut(t.remittance(), 500));
                if (changed) {
                    jdbc.update("""
                            UPDATE external_transaction SET status = ?, booking_date = ?, value_date = ?, amount_cents = ?,
                                   currency = ?, counterparty = ?, remittance = ?, bank_code = ?, is_reversal = ?,
                                   raw_enc = ?, updated_at = ?, related_id = CASE WHEN ? THEN related_id ELSE NULL END
                             WHERE id = ?
                            """, newStatus, Date.valueOf(t.bookingDate()),
                            t.valueDate() == null ? null : Date.valueOf(t.valueDate()), t.amountCents(),
                            t.currency() == null ? "EUR" : t.currency(), cut(t.counterparty(), 200),
                            cut(t.remittance(), 500), cut(t.bankCode(), 60), t.reversal(),
                            crypto.encrypt(t.raw() == null ? "{}" : t.raw()), Timestamp.from(now),
                            newStatus.equals("REVERSED") || t.reversal(), e.id());
                    updated++;
                }
            }
        }

        updated += reconcilePending(f, incoming);
        updated += linkReversals(f);
        return new int[] {inserted, updated};
    }

    private static Existing existingOf(ResultSet rs) throws SQLException {
        return new Existing(rs.getLong("id"), rs.getString("status"), rs.getLong("amount_cents"),
                rs.getDate("booking_date").toLocalDate(), rs.getString("counterparty"), rs.getString("remittance"));
    }

    /**
     * Vormerkungen, die die Bank nicht mehr liefert: wurde daraus eine Buchung (gleicher Betrag, gleicher
     * Empfänger, bis 7 Tage später) → SUPERSEDED, sonst → CANCELLED (z. B. aufgehobene Kartenreservierung).
     */
    private int reconcilePending(Fetched f, Set<String> incoming) {
        record Pending(Long id, String externalId, long amount, LocalDate date, String counterparty) {}
        List<Pending> vanished = new ArrayList<>();
        jdbc.query("""
                SELECT id, external_id, amount_cents, booking_date, counterparty FROM external_transaction
                 WHERE external_account_id = ? AND status = 'PENDING' AND booking_date >= ?
                 ORDER BY booking_date, id
                """, rs -> {
            if (!incoming.contains(rs.getString("external_id"))) {
                vanished.add(new Pending(rs.getLong("id"), rs.getString("external_id"), rs.getLong("amount_cents"),
                        rs.getDate("booking_date").toLocalDate(), rs.getString("counterparty")));
            }
        }, f.accountId(), Date.valueOf(f.from()));
        int changed = 0;
        for (Pending p : vanished) {
            List<Long> match = jdbc.queryForList("""
                    SELECT b.id FROM external_transaction b
                     WHERE b.external_account_id = ? AND b.status IN ('BOOKED', 'REVERSED') AND b.amount_cents = ?
                       AND b.booking_date BETWEEN ? AND ?
                       AND COALESCE(lower(b.counterparty), '') = COALESCE(lower(CAST(? AS VARCHAR)), '')
                       AND NOT EXISTS (SELECT 1 FROM external_transaction s
                                        WHERE s.status = 'SUPERSEDED' AND s.related_id = b.id)
                     ORDER BY abs(b.booking_date - ?::date), b.id LIMIT 1
                    """, Long.class, f.accountId(), p.amount(), Date.valueOf(p.date().minusDays(1)),
                    Date.valueOf(p.date().plusDays(PENDING_MATCH_DAYS)), p.counterparty(), Date.valueOf(p.date()));
            if (match.isEmpty()) {
                jdbc.update("UPDATE external_transaction SET status = 'CANCELLED', updated_at = ? WHERE id = ?",
                        Timestamp.from(Instant.now(clock)), p.id());
            } else {
                jdbc.update("UPDATE external_transaction SET status = 'SUPERSEDED', related_id = ?, updated_at = ? WHERE id = ?",
                        match.get(0), Timestamp.from(Instant.now(clock)), p.id());
                // eigene Bearbeitungen der Vormerkung auf die Buchung übertragen (falls dort noch keine sind)
                jdbc.update("""
                        INSERT INTO external_transaction_edit (transaction_id, user_id, category, note, tags, excluded, updated_at)
                        SELECT ?, user_id, category, note, tags, excluded, updated_at FROM external_transaction_edit
                         WHERE transaction_id = ? ON CONFLICT (transaction_id) DO NOTHING
                        """, match.get(0), p.id());
            }
            changed++;
        }
        return changed;
    }

    /** Gebuchte Rückbuchungen mit dem ursprünglichen Umsatz verknüpfen; dieser wird REVERSED. */
    private int linkReversals(Fetched f) {
        Map<String, String> reversalOf = new HashMap<>();
        for (TransactionData t : f.transactions()) {
            if (t.reversal() && t.reversalOf() != null) {
                reversalOf.put(t.externalId(), t.reversalOf());
            }
        }
        record Rev(Long id, String externalId, long amount, LocalDate date, String counterparty) {}
        List<Rev> open = jdbc.query("""
                SELECT id, external_id, amount_cents, booking_date, counterparty FROM external_transaction
                 WHERE external_account_id = ? AND is_reversal AND status = 'BOOKED' AND related_id IS NULL
                """, (rs, i) -> new Rev(rs.getLong("id"), rs.getString("external_id"), rs.getLong("amount_cents"),
                rs.getDate("booking_date").toLocalDate(), rs.getString("counterparty")), f.accountId());
        int changed = 0;
        for (Rev r : open) {
            List<Long> original;
            String ofId = reversalOf.get(r.externalId());
            if (ofId != null) {
                original = jdbc.queryForList("""
                        SELECT id FROM external_transaction WHERE external_account_id = ? AND external_id = ?
                           AND status = 'BOOKED' AND NOT is_reversal
                        """, Long.class, f.accountId(), ofId);
            } else {
                original = jdbc.queryForList("""
                        SELECT id FROM external_transaction
                         WHERE external_account_id = ? AND status = 'BOOKED' AND NOT is_reversal AND amount_cents = ?
                           AND booking_date BETWEEN ? AND ?
                           AND (counterparty IS NULL OR CAST(? AS VARCHAR) IS NULL
                                OR lower(counterparty) = lower(CAST(? AS VARCHAR)))
                         ORDER BY booking_date DESC, id DESC LIMIT 1
                        """, Long.class, f.accountId(), -r.amount(), Date.valueOf(r.date().minusDays(60)),
                        Date.valueOf(r.date()), r.counterparty(), r.counterparty());
            }
            if (original.isEmpty()) {
                continue;
            }
            Long o = original.get(0);
            Timestamp ts = Timestamp.from(Instant.now(clock));
            jdbc.update("UPDATE external_transaction SET status = 'REVERSED', related_id = ?, updated_at = ? WHERE id = ?",
                    r.id(), ts, o);
            jdbc.update("""
                    UPDATE external_transaction SET related_id = ?, updated_at = ?,
                           auto_category = (SELECT auto_category FROM external_transaction WHERE id = ?) WHERE id = ?
                    """, o, ts, o, r.id());
            changed += 2;
        }
        return changed;
    }

    private Category autoCategory(TransactionData t, Set<String> ownHashes) {
        if (t.counterpartyIban() != null && ownHashes.contains(crypto.fingerprint(normalizeIban(t.counterpartyIban())))) {
            return Category.UMBUCHUNG;   // Übertrag zwischen eigenen Konten
        }
        String text = ((t.counterparty() == null ? "" : t.counterparty()) + " "
                + (t.remittance() == null ? "" : t.remittance())).trim();
        return categories.categorize(t.amountCents() > 0 ? BookingType.INCOME : BookingType.DIRECT_DEBIT, text,
                t.amountCents());
    }

    /** Prüfwerte aller eigenen IBANs: verbundene Konten und Girokonto der Projektbank. */
    private Set<String> ownIbanHashes(Long userId) {
        Set<String> s = new HashSet<>(jdbc.queryForList(
                "SELECT iban_hash FROM external_account WHERE user_id = ? AND iban_hash IS NOT NULL", String.class, userId));
        for (String iban : jdbc.queryForList("SELECT iban FROM account WHERE owner_id = ? AND iban IS NOT NULL",
                String.class, userId)) {
            s.add(crypto.fingerprint(normalizeIban(iban)));
        }
        return s;
    }

    private void markExpired(Conn c) {
        int n = jdbc.update("UPDATE bank_connection SET status = 'EXPIRED' WHERE id = ? AND status = 'ACTIVE'", c.id);
        if (n > 0) {
            audit.record(c.userId, "BANK_CONSENT_EXPIRED", "connection:" + c.id, null);
            notifications.send(c.userId, NotificationType.BANK, "Bankfreigabe abgelaufen",
                    "Die Freigabe für " + c.institutionName + " ist abgelaufen. Verbinde die Bank neu, um weiter Umsätze zu sehen.",
                    null);
        }
    }

    // ================================================================== Täglich: Ablauf der Freigaben

    @Override
    public int order() {
        return 40;
    }

    @Override
    public void runFor(LocalDate date) {
        Instant endOfDay = date.plusDays(1).atStartOfDay(clock.getZone()).toInstant();
        for (Conn c : jdbc.query("""
                SELECT * FROM bank_connection WHERE status = 'ACTIVE' AND consent_expires_at IS NOT NULL
                   AND consent_expires_at < ?
                """, this::conn, Timestamp.from(endOfDay.plus(Duration.ofDays(7))))) {
            if (c.consentExpiresAt.isBefore(endOfDay)) {
                if (c.consentExpiresAt.isBefore(Instant.now(clock))) {
                    markExpired(c);
                }
            } else if (!c.expiryNotified) {
                long days = Math.max(1, ChronoUnit.DAYS.between(date, LocalDate.ofInstant(c.consentExpiresAt, clock.getZone())));
                jdbc.update("UPDATE bank_connection SET expiry_notified = TRUE WHERE id = ?", c.id);
                notifications.send(c.userId, NotificationType.BANK, "Bankfreigabe läuft bald ab",
                        "Die Freigabe für " + c.institutionName + " endet in " + days
                                + (days == 1 ? " Tag" : " Tagen") + ". Erneuere sie in der App unter Bankkonten.", null);
            }
        }
    }

    // ================================================================== Widerruf

    public RevokeResult revoke(Long userId, Long connectionId, boolean deleteData) {
        Conn c = load(userId, connectionId);
        boolean atProvider = true;
        if (c.consentRefEnc != null) {
            BankingProvider p = providers.get(c.provider);
            atProvider = p != null && bestEffortRevoke(p, crypto.decrypt(c.consentRefEnc));
        }
        if (deleteData) {
            jdbc.update("DELETE FROM bank_connection WHERE id = ? AND user_id = ?", c.id, userId);
        } else {
            jdbc.update("""
                    UPDATE bank_connection SET status = 'REVOKED', consent_ref_enc = NULL, consent_expires_at = NULL,
                           revoked_at = ? WHERE id = ?
                    """, Timestamp.from(Instant.now(clock)), c.id);
        }
        audit.record(userId, "BANK_REVOKED", "connection:" + c.id,
                c.institutionName + (deleteData ? ", Daten gelöscht" : ", Daten behalten"));
        String msg = atProvider
                ? (deleteData ? "Freigabe widerrufen und alle Daten dieser Bank gelöscht."
                        : "Freigabe widerrufen. Bisherige Umsätze bleiben gespeichert.")
                : "Freigabe bei uns gelöscht. Der Anbieter war nicht erreichbar – widerrufe den Zugriff zur Sicherheit auch bei deiner Bank.";
        return new RevokeResult(c.id, atProvider, deleteData, msg);
    }

    private boolean bestEffortRevoke(BankingProvider p, String ref) {
        try {
            withRetry(() -> {
                p.revoke(ref);
                return Boolean.TRUE;
            });
            return true;
        } catch (ProviderException e) {
            log.warn("Widerruf beim Anbieter {} fehlgeschlagen: {}", p.id(), e.kind());
            return false;
        }
    }

    // ================================================================== Anzeigen

    public List<ConnectionDto> connections(Long userId) {
        return jdbc.query("SELECT * FROM bank_connection WHERE user_id = ? ORDER BY id", this::conn, userId)
                .stream().map(this::dto).toList();
    }

    public ConnectionDto connection(Long userId, Long id) {
        return dto(load(userId, id));
    }

    public AccountsOverview accounts(Long userId) {
        List<AccountDto> list = jdbc.query("""
                SELECT a.*, c.institution_name, c.status AS c_status, c.last_sync_at, c.last_sync_status, c.provider
                  FROM external_account a JOIN bank_connection c ON c.id = a.connection_id
                 WHERE a.user_id = ? AND c.status IN ('ACTIVE', 'EXPIRED', 'ERROR')
                 ORDER BY c.id, a.id
                """, (rs, i) -> accountDto(rs), userId);
        long total = list.stream().filter(a -> "EUR".equals(a.currency()) && a.balanceCents() != null)
                .mapToLong(AccountDto::balanceCents).sum();
        YearMonth ym = YearMonth.now(clock);
        long[] sums = new long[2];
        jdbc.query("""
                SELECT t.amount_cents, COALESCE(e.category, t.auto_category) AS category
                  FROM external_transaction t
                  LEFT JOIN external_transaction_edit e ON e.transaction_id = t.id
                 WHERE t.user_id = ? AND t.status = 'BOOKED' AND NOT t.is_reversal AND t.currency = 'EUR'
                   AND COALESCE(e.excluded, FALSE) = FALSE AND t.booking_date >= ? AND t.booking_date < ?
                """, rs -> {
            Category cat = Category.valueOf(rs.getString("category"));
            if (cat.isIncome()) {
                sums[0] += rs.getLong("amount_cents");
            } else if (cat.isExpense()) {
                sums[1] -= rs.getLong("amount_cents");
            }
        }, userId, Date.valueOf(ym.atDay(1)), Date.valueOf(ym.plusMonths(1).atDay(1)));
        boolean stale = list.stream().anyMatch(a -> !"ACTIVE".equals(a.connectionStatus())
                || !"OK".equals(a.lastSyncStatus()));
        return new AccountsOverview(list, total, sums[0], sums[1], list.stream().anyMatch(AccountDto::sandbox), stale);
    }

    private AccountDto accountDto(ResultSet rs) throws SQLException {
        Timestamp sync = rs.getTimestamp("last_sync_at");
        Timestamp bal = rs.getTimestamp("balance_at");
        long b = rs.getLong("balance_cents");
        Long balance = rs.wasNull() ? null : b;
        BankingProvider p = providers.get(rs.getString("provider"));
        return new AccountDto(rs.getLong("id"), rs.getLong("connection_id"), rs.getString("institution_name"),
                rs.getString("name"), rs.getString("iban_masked"), rs.getString("currency"), balance,
                rs.getString("balance_type"), bal == null ? null : bal.toInstant(), sync == null ? null : sync.toInstant(),
                rs.getString("last_sync_status"), rs.getString("c_status"), p != null && p.sandbox());
    }

    public PageDto<TransactionDto> transactions(Long userId, Long accountId, String query, String category,
                                                String status, String tag, LocalDate from, LocalDate to,
                                                int page, int size) {
        int s = Math.min(Math.max(size, 1), 100);
        int pg = Math.max(page, 0);
        StringBuilder where = new StringBuilder(" WHERE t.user_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(userId);
        if (accountId != null) {
            where.append(" AND t.external_account_id = ?");
            args.add(accountId);
        }
        if (status != null && !status.isBlank()) {
            List<String> st = Arrays.stream(status.split(",")).map(x -> x.trim().toUpperCase(Locale.ROOT)).toList();
            for (String x : st) {
                if (!Set.of("PENDING", "BOOKED", "CANCELLED", "SUPERSEDED", "REVERSED").contains(x)) {
                    throw bad("INVALID_STATUS", "Unbekannter Status.");
                }
            }
            where.append(" AND t.status IN (").append(String.join(",", st.stream().map(x -> "?").toList())).append(")");
            args.addAll(st);
        } else {
            where.append(" AND t.status <> 'SUPERSEDED'");   // ersetzte Vormerkungen nur auf Wunsch
        }
        if (category != null && !category.isBlank()) {
            where.append(" AND COALESCE(e.category, t.auto_category) = ?");
            args.add(parseCategory(category).name());
        }
        if (query != null && !query.isBlank()) {
            String q = "%" + query.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%")
                    .replace("_", "\\_") + "%";
            where.append(" AND (lower(COALESCE(t.counterparty, '')) LIKE ? OR lower(COALESCE(t.remittance, '')) LIKE ?"
                    + " OR lower(COALESCE(e.note, '')) LIKE ?)");
            args.add(q);
            args.add(q);
            args.add(q);
        }
        if (tag != null && !tag.isBlank()) {
            where.append(" AND (',' || COALESCE(e.tags, '') || ',') LIKE ?");
            args.add("%," + normalizeTag(tag) + ",%");
        }
        if (from != null) {
            where.append(" AND t.booking_date >= ?");
            args.add(Date.valueOf(from));
        }
        if (to != null) {
            where.append(" AND t.booking_date <= ?");
            args.add(Date.valueOf(to));
        }
        args.add(s + 1);
        args.add(pg * s);
        List<TransactionDto> rows = jdbc.query(TX_SELECT + where + " ORDER BY t.booking_date DESC, t.id DESC LIMIT ? OFFSET ?",
                (rs, i) -> txDto(rs), args.toArray());
        boolean more = rows.size() > s;
        return new PageDto<>(more ? rows.subList(0, s) : rows, pg, s, more);
    }

    private static final String TX_SELECT = """
            SELECT t.*, a.name AS account_name, c.institution_name, c.provider, e.category AS e_category, e.note,
                   e.tags, e.excluded, e.transaction_id AS e_id
              FROM external_transaction t
              JOIN external_account a ON a.id = t.external_account_id
              JOIN bank_connection c ON c.id = a.connection_id
              LEFT JOIN external_transaction_edit e ON e.transaction_id = t.id
            """;

    private TransactionDto txDto(ResultSet rs) throws SQLException {
        Category auto = Category.valueOf(rs.getString("auto_category"));
        String ec = rs.getString("e_category");
        Category effective = ec == null ? auto : Category.valueOf(ec);
        Date value = rs.getDate("value_date");
        String tags = rs.getString("tags");
        long related = rs.getLong("related_id");
        Long relatedId = rs.wasNull() ? null : related;
        rs.getLong("e_id");
        boolean edited = !rs.wasNull();
        BankingProvider p = providers.get(rs.getString("provider"));
        String status = rs.getString("status");
        return new TransactionDto(rs.getLong("id"), rs.getLong("external_account_id"), rs.getString("account_name"),
                rs.getString("institution_name"), status, statusLabel(status), rs.getDate("booking_date").toLocalDate(),
                value == null ? null : value.toLocalDate(), rs.getLong("amount_cents"), rs.getString("currency"),
                rs.getString("counterparty"), rs.getString("remittance"), rs.getBoolean("is_reversal"), relatedId,
                auto, effective, effective.label(), rs.getString("note"),
                tags == null || tags.isBlank() ? List.of() : List.of(tags.split(",")), rs.getBoolean("excluded"),
                edited, p != null && p.sandbox());
    }

    static String statusLabel(String status) {
        return switch (status) {
            case "PENDING" -> "Vorgemerkt";
            case "BOOKED" -> "Gebucht";
            case "CANCELLED" -> "Storniert";
            case "SUPERSEDED" -> "Durch Buchung ersetzt";
            case "REVERSED" -> "Zurückgebucht";
            default -> status;
        };
    }

    public TransactionDto transaction(Long userId, Long id) {
        List<TransactionDto> r = jdbc.query(TX_SELECT + " WHERE t.id = ? AND t.user_id = ?", (rs, i) -> txDto(rs), id, userId);
        if (r.isEmpty()) {
            throw notFound();
        }
        return r.get(0);
    }

    /** Eigene Bearbeitung speichern – die Originaldaten der Bank bleiben unverändert. */
    public TransactionDto updateTransaction(Long userId, Long id, UpdateTransactionRequest r) {
        TransactionDto current = transaction(userId, id);
        Map<String, Object> old = jdbc.query("SELECT * FROM external_transaction_edit WHERE transaction_id = ?", rs -> {
            if (!rs.next()) {
                return null;
            }
            Map<String, Object> m = new HashMap<>();
            m.put("category", rs.getString("category"));
            m.put("note", rs.getString("note"));
            m.put("tags", rs.getString("tags"));
            m.put("excluded", rs.getBoolean("excluded"));
            return m;
        }, id);
        String category = old == null ? null : (String) old.get("category");
        String note = old == null ? null : (String) old.get("note");
        String tags = old == null ? null : (String) old.get("tags");
        boolean excluded = old != null && (Boolean) old.get("excluded");
        if (r.category() != null) {
            category = r.category().isBlank() ? null : parseCategory(r.category()).name();
            if (category != null && category.equals(current.autoCategory().name())) {
                category = null;   // entspricht der automatischen Kategorie
            }
        }
        if (r.note() != null) {
            note = r.note().isBlank() ? null : r.note().trim();
        }
        if (r.tags() != null) {
            Set<String> t = new LinkedHashSet<>();
            for (String tag : r.tags()) {
                String n = normalizeTag(tag);
                if (!n.isEmpty()) {
                    t.add(n);
                }
            }
            if (t.size() > 10) {
                throw bad("TOO_MANY_TAGS", "Höchstens 10 Schlagwörter.");
            }
            tags = t.isEmpty() ? null : String.join(",", t);
        }
        if (r.excluded() != null) {
            excluded = r.excluded();
        }
        if (category == null && note == null && tags == null && !excluded) {
            jdbc.update("DELETE FROM external_transaction_edit WHERE transaction_id = ?", id);
        } else {
            jdbc.update("""
                    INSERT INTO external_transaction_edit (transaction_id, user_id, category, note, tags, excluded, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (transaction_id) DO UPDATE SET category = EXCLUDED.category, note = EXCLUDED.note,
                           tags = EXCLUDED.tags, excluded = EXCLUDED.excluded, updated_at = EXCLUDED.updated_at
                    """, id, userId, category, note, tags, excluded, Timestamp.from(Instant.now(clock)));
        }
        return transaction(userId, id);
    }

    /** Originaldaten der Bank (unverändert, wie geliefert). */
    public String original(Long userId, Long id) {
        List<String> enc = jdbc.queryForList("SELECT raw_enc FROM external_transaction WHERE id = ? AND user_id = ?",
                String.class, id, userId);
        if (enc.isEmpty()) {
            throw notFound();
        }
        return crypto.decrypt(enc.get(0));
    }

    // ================================================================== Hilfen

    /** Interne Sicht einer Verbindung (mit verschlüsselter Freigabe-Kennung, nie nach außen geben). */
    static final class Conn {
        Long id;
        Long userId;
        String provider;
        String institutionId;
        String institutionName;
        String status;
        String consentRefEnc;
        Instant consentExpiresAt;
        boolean expiryNotified;
        Instant lastSyncAt;
        String lastSyncStatus;
        String lastError;
        int failedSyncs;
        Instant createdAt;
        Instant revokedAt;
    }

    private Conn conn(ResultSet rs, int i) throws SQLException {
        Conn c = new Conn();
        c.id = rs.getLong("id");
        c.userId = rs.getLong("user_id");
        c.provider = rs.getString("provider");
        c.institutionId = rs.getString("institution_id");
        c.institutionName = rs.getString("institution_name");
        c.status = rs.getString("status");
        c.consentRefEnc = rs.getString("consent_ref_enc");
        c.consentExpiresAt = instant(rs, "consent_expires_at");
        c.expiryNotified = rs.getBoolean("expiry_notified");
        c.lastSyncAt = instant(rs, "last_sync_at");
        c.lastSyncStatus = rs.getString("last_sync_status");
        c.lastError = rs.getString("last_error");
        c.failedSyncs = rs.getInt("failed_syncs");
        c.createdAt = instant(rs, "created_at");
        c.revokedAt = instant(rs, "revoked_at");
        return c;
    }

    private static Instant instant(ResultSet rs, String col) throws SQLException {
        Timestamp t = rs.getTimestamp(col);
        return t == null ? null : t.toInstant();
    }

    private Conn load(Long userId, Long id) {
        List<Conn> r = jdbc.query("SELECT * FROM bank_connection WHERE id = ? AND user_id = ?", this::conn, id, userId);
        if (r.isEmpty()) {
            throw notFound();   // fremde Verbindungen sind "nicht gefunden"
        }
        return r.get(0);
    }

    private ConnectionDto dto(Conn c) {
        BankingProvider p = providers.get(c.provider);
        List<AccountDto> accounts = jdbc.query("""
                SELECT a.*, c.institution_name, c.status AS c_status, c.last_sync_at, c.last_sync_status, c.provider
                  FROM external_account a JOIN bank_connection c ON c.id = a.connection_id
                 WHERE a.connection_id = ? ORDER BY a.id
                """, (rs, i) -> accountDto(rs), c.id);
        Integer daysLeft = c.consentExpiresAt == null || !c.status.equals("ACTIVE") ? null
                : (int) Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(clock),
                        LocalDate.ofInstant(c.consentExpiresAt, clock.getZone())));
        return new ConnectionDto(c.id, c.provider, p != null && p.sandbox(), c.institutionId, c.institutionName,
                c.status, connectionStatusLabel(c.status), c.status.equals("ACTIVE") ? PERMISSIONS : List.of(),
                c.consentExpiresAt, daysLeft, c.lastSyncAt, c.lastSyncStatus, c.lastError, c.createdAt, c.revokedAt,
                accounts);
    }

    static String connectionStatusLabel(String status) {
        return switch (status) {
            case "PENDING" -> "Freigabe ausstehend";
            case "ACTIVE" -> "Verbunden";
            case "EXPIRED" -> "Freigabe abgelaufen";
            case "REVOKED" -> "Widerrufen";
            case "REJECTED" -> "Freigabe abgelehnt";
            default -> "Fehler";
        };
    }

    /** Ruft den Anbieter auf und wiederholt bei vorübergehenden Fehlern (mit wachsender Wartezeit). */
    <T> T withRetry(Supplier<T> call) {
        int attempts = Math.max(props.retryAttempts(), 1);
        for (int i = 1; ; i++) {
            try {
                return call.get();
            } catch (ProviderException e) {
                if (!e.transientError() || i >= attempts) {
                    throw e;
                }
                long wait = props.retryBackoffMs() * (1L << (i - 1));
                if (wait > 0) {
                    try {
                        Thread.sleep(Math.min(wait, 10_000));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                }
            }
        }
    }

    private <T> T withProvider(Supplier<T> call) {
        try {
            return withRetry(call);
        } catch (ProviderException e) {
            log.warn("Anbieter-Fehler: {} ({})", e.getMessage(), e.kind());
            throw unreachable();
        }
    }

    private static ApiException unreachable() {
        return new ApiException(HttpStatus.BAD_GATEWAY, "PROVIDER_ERROR",
                "Die Bank bzw. der Anbieter ist gerade nicht erreichbar. Bitte später erneut versuchen.");
    }

    private static Category parseCategory(String s) {
        try {
            Category c = Category.valueOf(s.trim().toUpperCase(Locale.ROOT));
            if (USER_CATEGORIES.contains(c)) {
                return c;
            }
        } catch (IllegalArgumentException ignored) {
            // unten
        }
        throw bad("INVALID_CATEGORY", "Unbekannte Kategorie.");
    }

    static String normalizeTag(String tag) {
        String t = tag == null ? "" : tag.trim().toLowerCase(Locale.ROOT).replace(",", " ").replaceAll("\\s+", " ");
        return t.length() > 30 ? t.substring(0, 30) : t;
    }

    static String normalizeIban(String iban) {
        return iban.replace(" ", "").toUpperCase(Locale.ROOT);
    }

    /** "DE89 •••• 3000": Land, Prüfziffern und die letzten 4 Stellen. */
    static String maskIban(String iban) {
        if (iban == null) {
            return null;
        }
        String s = normalizeIban(iban);
        if (s.length() < 8) {
            return "••••";
        }
        return s.substring(0, 4) + " •••• " + s.substring(s.length() - 4);
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    private static ApiException bad(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Nicht gefunden.");
    }
}
