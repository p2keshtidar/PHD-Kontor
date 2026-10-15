package de.frauas.girokonto.market;

import de.frauas.girokonto.audit.AuditLog;
import de.frauas.girokonto.common.ApiException;
import de.frauas.girokonto.common.Money;
import de.frauas.girokonto.insights.OverviewDto;
import de.frauas.girokonto.notification.NotificationService;
import de.frauas.girokonto.notification.NotificationType;
import de.frauas.girokonto.savings.SavingsPlanService;
import de.frauas.girokonto.simulation.DailyJob;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finanz-Marktplatz: Angebote vergleichen (nur simulierte Anbieter), Favoriten, Vergleichslisten und simulierte
 * Anträge mit Unterlagen. Eine „Zusage“ ist eine Simulation – es entsteht kein Vertrag, kein Konto, keine Karte.
 */
@Service
public class MarketService implements DailyJob {

    static final String DISCLAIMER = "Simulation: fiktive Anbieter mit Beispielkonditionen – keine echten Angebote, "
            + "keine Anlageberatung. Echte Anbieter werden erst mit Vertrag und Freigabe angebunden.";
    static final Map<String, String> CATEGORIES = new LinkedHashMap<>();

    static {
        CATEGORIES.put("GIRO", "Girokonten");
        CATEGORIES.put("CREDIT_CARD", "Kreditkarten");
        CATEGORIES.put("CALL", "Tagesgeld");
        CATEGORIES.put("FIXED", "Festgeld");
        CATEGORIES.put("DEPOT", "Depots");
    }

    // ------------------------------------------------------------------ DTOs

    /**
     * Ein Angebot mit transparenten Vergleichswerten für einen Beispielbetrag.
     *
     * @param exampleCents Kosten pro Jahr (Konto, Karte, Depot mit 12 Käufen) bzw. Zinsertrag (Tagesgeld: 1 Jahr,
     *                     Festgeld: Laufzeit) für den Beispielbetrag
     * @param eligible     Mindestanlage erfüllt (bei Geldanlagen)
     */
    public record ProductView(String id, String category, String categoryLabel, String provider, String name,
                              long monthlyFeeCents, long annualFeeCents, BigDecimal interestPercent, Integer termMonths,
                              Long minAmountCents, Long orderFeeCents, List<String> requirements, List<String> pros,
                              List<String> cons, List<String> documents, long exampleCents, String exampleText,
                              boolean eligible, boolean favorite, boolean simulation) {}

    public record Catalog(String category, long amountCents, List<ProductView> products, Map<String, String> categories,
                          String disclaimer, String sortExplanation) {}

    public record CompareRow(String label, List<String> values) {}

    public record Comparison(List<ProductView> products, List<CompareRow> rows, String disclaimer) {}

    public record DocumentDto(String key, String label, boolean provided) {}

    public record ApplicationDto(Long id, String productId, String productName, String provider, String category,
                                 String status, String statusLabel, String statusReason, String note,
                                 List<DocumentDto> documents, Instant createdAt, Instant decidedAt, boolean simulation) {}

    private final MarketProvider provider;
    private final JdbcTemplate jdbc;
    private final SavingsPlanService plans;
    private final NotificationService notifications;
    private final AuditLog audit;
    private final Clock clock;

    public MarketService(MarketProvider provider, JdbcTemplate jdbc, SavingsPlanService plans,
                         NotificationService notifications, AuditLog audit, Clock clock) {
        this.provider = provider;
        this.jdbc = jdbc;
        this.plans = plans;
        this.notifications = notifications;
        this.audit = audit;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ Vergleich

    @Transactional(readOnly = true)
    public Catalog catalog(Long userId, String category, Long amountCents, Long maxYearlyFeeCents,
                           BigDecimal minInterest, Integer termMonths) {
        String cat = category == null || category.isBlank() ? "GIRO" : category.trim().toUpperCase(Locale.ROOT);
        if (!CATEGORIES.containsKey(cat)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CATEGORY",
                    "Unbekannte Kategorie. Möglich: Girokonten, Kreditkarten, Tagesgeld, Festgeld, Depots.");
        }
        long amount = amountCents == null ? 1_000_000 : Math.min(Math.max(amountCents, 100), 100_000_000);
        Set<String> favorites = favorites(userId);
        List<ProductView> views = new ArrayList<>();
        for (MarketProduct p : provider.products()) {
            if (!p.category().equals(cat)) {
                continue;
            }
            if (maxYearlyFeeCents != null && p.yearlyFeeCents() > maxYearlyFeeCents) {
                continue;
            }
            if (minInterest != null && (p.interestPercent() == null || p.interestPercent().compareTo(minInterest) < 0)) {
                continue;
            }
            if (termMonths != null && p.termMonths() != null && !p.termMonths().equals(termMonths)) {
                continue;
            }
            views.add(view(p, amount, favorites.contains(p.id())));
        }
        boolean yield = cat.equals("CALL") || cat.equals("FIXED");
        views.sort(yield
                ? Comparator.comparing(ProductView::eligible).reversed()
                        .thenComparing(Comparator.comparingLong(ProductView::exampleCents).reversed())
                : Comparator.comparingLong(ProductView::exampleCents));
        return new Catalog(cat, amount, views, CATEGORIES, DISCLAIMER, yield
                ? "Sortiert nach Zinsertrag für " + Money.format(amount) + " (Mindestanlage erfüllt zuerst)."
                : "Sortiert nach Kosten pro Jahr" + (cat.equals("DEPOT") ? " bei 12 Käufen." : "."));
    }

    ProductView view(MarketProduct p, long amount, boolean favorite) {
        long example;
        String text;
        boolean eligible = p.minAmountCents() == null || amount >= p.minAmountCents();
        switch (p.category()) {
            case "CALL" -> {
                example = interest(amount, p.interestPercent(), 12);
                text = "Zinsen in 12 Monaten für " + Money.format(amount) + ": " + Money.format(example);
            }
            case "FIXED" -> {
                example = interest(amount, p.interestPercent(), p.termMonths());
                text = "Zinsen in " + p.termMonths() + " Monaten für " + Money.format(amount) + ": " + Money.format(example);
            }
            case "DEPOT" -> {
                example = p.yearlyFeeCents() + 12 * (p.orderFeeCents() == null ? 0 : p.orderFeeCents());
                text = "Kosten pro Jahr bei 12 Käufen: " + Money.format(example);
            }
            default -> {
                example = p.yearlyFeeCents();
                text = "Kosten pro Jahr: " + Money.format(example);
            }
        }
        if (!eligible) {
            text += " (Mindestanlage " + Money.format(p.minAmountCents()) + ")";
        }
        return new ProductView(p.id(), p.category(), CATEGORIES.get(p.category()), p.provider(), p.name(),
                p.monthlyFeeCents(), p.annualFeeCents(), p.interestPercent(), p.termMonths(), p.minAmountCents(),
                p.orderFeeCents(), p.requirements(), p.pros(), p.cons(), p.documents(), example, text, eligible,
                favorite, provider.simulated());
    }

    /** Einfache Zinsen (ohne Zinseszins) auf Cent gerundet. */
    static long interest(long amount, BigDecimal percent, int months) {
        if (percent == null) {
            return 0;
        }
        return BigDecimal.valueOf(amount).multiply(percent).multiply(BigDecimal.valueOf(months))
                .divide(BigDecimal.valueOf(1200), 0, RoundingMode.HALF_EVEN).longValueExact();
    }

    MarketProduct product(String id) {
        return provider.products().stream().filter(p -> p.id().equals(id)).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Angebot nicht gefunden."));
    }

    @Transactional(readOnly = true)
    public ProductView detail(Long userId, String id, Long amountCents) {
        return view(product(id), amountCents == null ? 1_000_000 : amountCents, favorites(userId).contains(id));
    }

    @Transactional(readOnly = true)
    public Comparison compare(Long userId, List<String> ids, Long amountCents) {
        if (ids == null || ids.size() < 2 || ids.size() > 4) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "COMPARE_COUNT", "Bitte 2 bis 4 Angebote vergleichen.");
        }
        long amount = amountCents == null ? 1_000_000 : amountCents;
        Set<String> fav = favorites(userId);
        List<ProductView> views = ids.stream().distinct().map(id -> view(product(id), amount, fav.contains(id))).toList();
        List<CompareRow> rows = new ArrayList<>();
        rows.add(new CompareRow("Anbieter", views.stream().map(ProductView::provider).toList()));
        rows.add(new CompareRow("Art", views.stream().map(ProductView::categoryLabel).toList()));
        rows.add(new CompareRow("Gebühr pro Monat", views.stream().map(v -> Money.format(v.monthlyFeeCents())).toList()));
        rows.add(new CompareRow("Jahresgebühr", views.stream().map(v -> Money.format(v.annualFeeCents())).toList()));
        rows.add(new CompareRow("Zinssatz", views.stream().map(v -> v.interestPercent() == null ? "–"
                : v.interestPercent().setScale(2, RoundingMode.HALF_EVEN).toPlainString().replace('.', ',') + " %").toList()));
        rows.add(new CompareRow("Laufzeit", views.stream().map(v -> v.termMonths() == null ? "–"
                : v.termMonths() + " Monate").toList()));
        rows.add(new CompareRow("Mindestbetrag", views.stream().map(v -> v.minAmountCents() == null ? "–"
                : Money.format(v.minAmountCents())).toList()));
        rows.add(new CompareRow("Beispiel", views.stream().map(ProductView::exampleText).toList()));
        rows.add(new CompareRow("Voraussetzungen", views.stream().map(v -> v.requirements().isEmpty() ? "keine"
                : String.join(", ", v.requirements())).toList()));
        return new Comparison(views, rows, DISCLAIMER);
    }

    // ------------------------------------------------------------------ Favoriten

    private Set<String> favorites(Long userId) {
        return new HashSet<>(jdbc.queryForList("SELECT product_id FROM market_favorite WHERE user_id = ?",
                String.class, userId));
    }

    @Transactional(readOnly = true)
    public List<ProductView> favoriteList(Long userId) {
        Set<String> fav = favorites(userId);
        return provider.products().stream().filter(p -> fav.contains(p.id())).map(p -> view(p, 1_000_000, true)).toList();
    }

    @Transactional
    public void favorite(Long userId, String productId, boolean on) {
        product(productId);
        if (on) {
            jdbc.update("INSERT INTO market_favorite (user_id, product_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                    userId, productId);
        } else {
            jdbc.update("DELETE FROM market_favorite WHERE user_id = ? AND product_id = ?", userId, productId);
        }
    }

    // ------------------------------------------------------------------ Simulierte Anträge

    static String statusLabel(String s) {
        return switch (s) {
            case "DOCUMENTS_MISSING" -> "Unterlagen fehlen";
            case "SUBMITTED" -> "Eingereicht – wird geprüft";
            case "APPROVED" -> "Simulierte Zusage";
            case "DECLINED" -> "Simulierte Ablehnung";
            case "WITHDRAWN" -> "Zurückgezogen";
            default -> s;
        };
    }

    @Transactional
    public ApplicationDto apply(Long userId, String productId, boolean consent) {
        if (!consent) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CONSENT_REQUIRED",
                    "Bitte bestätigen, dass es sich um eine Simulation handelt.");
        }
        MarketProduct p = product(productId);
        Integer open = jdbc.queryForObject("""
                SELECT count(*) FROM market_application WHERE user_id = ? AND product_id = ?
                   AND status IN ('DOCUMENTS_MISSING', 'SUBMITTED')
                """, Integer.class, userId, productId);
        if (open != null && open > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "APPLICATION_OPEN", "Für dieses Angebot läuft schon ein Antrag.");
        }
        String status = p.documents().isEmpty() ? "SUBMITTED" : "DOCUMENTS_MISSING";
        Long id = jdbc.queryForObject("""
                INSERT INTO market_application (user_id, product_id, product_name, provider_name, category, status,
                                                created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, userId, p.id(), p.name(), p.provider(), p.category(), status,
                Timestamp.from(Instant.now(clock)), Timestamp.from(Instant.now(clock)));
        int i = 0;
        for (String doc : p.documents()) {
            jdbc.update("INSERT INTO market_application_document (application_id, doc_key, label) VALUES (?, ?, ?)",
                    id, "doc" + (++i), doc);
        }
        audit.record(userId, "MARKET_APPLICATION", "application:" + id, p.id());
        return application(userId, id);
    }

    @Transactional(readOnly = true)
    public List<ApplicationDto> applications(Long userId) {
        return jdbc.queryForList("SELECT id FROM market_application WHERE user_id = ? ORDER BY id DESC", Long.class,
                userId).stream().map(id -> application(userId, id)).toList();
    }

    @Transactional(readOnly = true)
    public ApplicationDto application(Long userId, Long id) {
        List<ApplicationDto> r = jdbc.query("SELECT * FROM market_application WHERE id = ? AND user_id = ?", (rs, n) -> {
            List<DocumentDto> docs = jdbc.query("""
                    SELECT doc_key, label, provided_at FROM market_application_document
                     WHERE application_id = ? ORDER BY doc_key
                    """, (d, m) -> new DocumentDto(d.getString("doc_key"), d.getString("label"),
                    d.getTimestamp("provided_at") != null), id);
            Timestamp decided = rs.getTimestamp("decided_at");
            String status = rs.getString("status");
            return new ApplicationDto(rs.getLong("id"), rs.getString("product_id"), rs.getString("product_name"),
                    rs.getString("provider_name"), rs.getString("category"), status, statusLabel(status),
                    rs.getString("status_reason"), rs.getString("note"), docs,
                    rs.getTimestamp("created_at").toInstant(), decided == null ? null : decided.toInstant(), true);
        }, id, userId);
        if (r.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "APPLICATION_NOT_FOUND", "Antrag nicht gefunden.");
        }
        return r.get(0);
    }

    /** Unterlage als bereitgestellt markieren (Upload folgt mit der Dokumentenverwaltung). */
    @Transactional
    public ApplicationDto provide(Long userId, Long id, String docKey) {
        ApplicationDto a = application(userId, id);
        if (!a.status().equals("DOCUMENTS_MISSING")) {
            throw new ApiException(HttpStatus.CONFLICT, "APPLICATION_CLOSED", "Der Antrag braucht keine Unterlagen mehr.");
        }
        int n = jdbc.update("UPDATE market_application_document SET provided_at = ? WHERE application_id = ? AND doc_key = ?",
                Timestamp.from(Instant.now(clock)), id, docKey);
        if (n == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "Unterlage nicht gefunden.");
        }
        Integer missing = jdbc.queryForObject("SELECT count(*) FROM market_application_document WHERE application_id = ?"
                + " AND provided_at IS NULL", Integer.class, id);
        if (missing != null && missing == 0) {
            jdbc.update("UPDATE market_application SET status = 'SUBMITTED', updated_at = ? WHERE id = ?",
                    Timestamp.from(Instant.now(clock)), id);
        }
        return application(userId, id);
    }

    @Transactional
    public ApplicationDto withdraw(Long userId, Long id) {
        ApplicationDto a = application(userId, id);
        if (!a.status().equals("DOCUMENTS_MISSING") && !a.status().equals("SUBMITTED")) {
            throw new ApiException(HttpStatus.CONFLICT, "APPLICATION_CLOSED", "Der Antrag ist schon abgeschlossen.");
        }
        jdbc.update("UPDATE market_application SET status = 'WITHDRAWN', updated_at = ? WHERE id = ?",
                Timestamp.from(Instant.now(clock)), id);
        return application(userId, id);
    }

    /** Eigene Entscheidung oder Notiz festhalten. */
    @Transactional
    public ApplicationDto note(Long userId, Long id, String note) {
        application(userId, id);
        String text = note == null || note.isBlank() ? null : note.strip();
        jdbc.update("UPDATE market_application SET note = ?, updated_at = ? WHERE id = ?", text,
                Timestamp.from(Instant.now(clock)), id);
        return application(userId, id);
    }

    @Override
    public int order() {
        return 55;
    }

    /**
     * Simulierte Prüfung eingereichter Anträge am Folgetag: Voraussetzung „Einnahmen ab …“ wird mit dem Schnitt aus
     * dem Finanzüberblick verglichen. Ergebnis ist eine Simulation – es entsteht kein Vertrag.
     */
    @Override
    public void runFor(LocalDate date) {
        Timestamp startOfDay = Timestamp.from(date.atStartOfDay(clock.getZone()).toInstant());
        record Open(long id, long userId, String productId, String name, String category) {}
        List<Open> open = jdbc.query("SELECT id, user_id, product_id, product_name, category FROM market_application"
                + " WHERE status = 'SUBMITTED' AND updated_at < ?", (rs, n) -> new Open(rs.getLong("id"),
                rs.getLong("user_id"), rs.getString("product_id"), rs.getString("product_name"),
                rs.getString("category")), startOfDay);
        for (Open a : open) {
            MarketProduct p = provider.products().stream().filter(x -> x.id().equals(a.productId())).findFirst()
                    .orElse(null);
            boolean approved = true;
            String reason = null;
            if (p != null && p.minIncomeCents() != null) {
                OverviewDto o = plans.overview(a.userId());
                long income = o.hasData() ? o.avgIncomeCents() : 0;
                approved = income >= p.minIncomeCents();
                if (!approved) {
                    reason = "Simulierte Ablehnung: Voraussetzung „Einnahmen ab " + Money.format(p.minIncomeCents())
                            + " pro Monat“ laut deinem Finanzüberblick nicht erfüllt (" + Money.format(income) + ").";
                }
            }
            if (approved) {
                reason = "Simulierte Zusage – es entsteht kein echter Vertrag. " + (a.category().equals("CREDIT_CARD")
                        ? "Es wird keine Karte bestellt oder ausgestellt." : "Es wird kein echtes Konto eröffnet.");
            }
            int n = jdbc.update("UPDATE market_application SET status = ?, status_reason = ?, decided_at = ?, updated_at = ?"
                            + " WHERE id = ? AND status = 'SUBMITTED'", approved ? "APPROVED" : "DECLINED", reason,
                    Timestamp.from(Instant.now(clock)), Timestamp.from(Instant.now(clock)), a.id());
            if (n == 1) {
                notifications.send(a.userId(), NotificationType.INFO, approved ? "Simulierte Zusage" : "Simulierte Ablehnung",
                        "„" + a.name() + "“: " + reason, null);
            }
        }
    }
}
