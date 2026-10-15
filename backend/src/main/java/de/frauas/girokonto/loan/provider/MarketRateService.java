package de.frauas.girokonto.loan.provider;

import de.frauas.girokonto.loan.LoanPurpose;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Durchschnittszinsen für neu abgeschlossene Kredite in Deutschland aus der MFI-Zinsstatistik der Deutschen
 * Bundesbank (öffentlicher SDMX-Webservice, keine Zugangsdaten nötig).
 *
 * <p>Die Werte sind eine amtliche Referenz für den Vergleich – <b>keine Angebote</b>. Sie werden nie geschätzt oder
 * erfunden: Ohne erfolgreichen Abruf gibt es keine Referenz. Der letzte erfolgreiche Abruf bleibt gespeichert und
 * wird als veraltet gekennzeichnet, wenn er zu alt ist oder der Berichtsmonat lange zurückliegt.
 */
@Service
public class MarketRateService {

    private static final Logger log = LoggerFactory.getLogger(MarketRateService.class);

    public static final String SOURCE = "Deutsche Bundesbank, MFI-Zinsstatistik (Neugeschäft)";
    public static final String SOURCE_URL =
            "https://www.bundesbank.de/de/statistiken/geld-und-kapitalmaerkte/zinssaetze-und-renditen";
    static final String NOTE = "Durchschnitt neu abgeschlossener Kredite in Deutschland – kein Angebot.";
    private static final Pattern PERIOD = Pattern.compile("\\d{4}-\\d{2}");
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    /**
     * Eine Zeitreihe der Bundesbank.
     *
     * @param overall   effektiver Jahreszins inkl. Kosten über alle Zinsbindungen (sonst: effektiver Zinssatz einer
     *                  Zinsbindungsklasse)
     * @param minMonths kürzeste Zinsbindung (Monate), für die die Reihe passt
     */
    public record Series(String key, String flow, String seriesKey, String label, boolean housing, int minMonths,
                         int maxMonths, boolean overall) {}

    static final List<Series> SERIES = List.of(
            new Series("CONSUMER_ALL", "BBIM1", "M.DE.B.A2B.A.C.A.2250.EUR.N",
                    "Konsumentenkredite: effektiver Jahreszins inkl. Kosten", false, 0, Integer.MAX_VALUE, true),
            new Series("CONSUMER_TO_1Y", "BBIM1", "M.DE.B.A2B.F.R.A.2250.EUR.N",
                    "Konsumentenkredite, Zinsbindung bis 1 Jahr", false, 0, 12, false),
            new Series("CONSUMER_1_5Y", "BBIM1", "M.DE.B.A2B.I.R.A.2250.EUR.N",
                    "Konsumentenkredite, Zinsbindung über 1 bis 5 Jahre", false, 13, 60, false),
            new Series("CONSUMER_OVER_5Y", "BBIM1", "M.DE.B.A2B.J.R.A.2250.EUR.N",
                    "Konsumentenkredite, Zinsbindung über 5 Jahre", false, 61, Integer.MAX_VALUE, false),
            new Series("HOUSING_ALL", "BBIM1", "M.DE.B.A2C.A.C.A.2250.EUR.N",
                    "Wohnungsbaukredite: effektiver Jahreszins inkl. Kosten", true, 0, Integer.MAX_VALUE, true),
            new Series("HOUSING_TO_1Y", "BBIM1", "M.DE.B.A2C.F.R.A.2250.EUR.N",
                    "Wohnungsbaukredite, Zinsbindung bis 1 Jahr", true, 0, 12, false),
            new Series("HOUSING_1_5Y", "BBIM1", "M.DE.B.A2C.I.R.A.2250.EUR.N",
                    "Wohnungsbaukredite, Zinsbindung über 1 bis 5 Jahre", true, 13, 60, false),
            new Series("HOUSING_5_10Y", "BBIM1", "M.DE.B.A2C.O.R.A.2250.EUR.N",
                    "Wohnungsbaukredite, Zinsbindung über 5 bis 10 Jahre", true, 61, 120, false),
            new Series("HOUSING_OVER_10Y", "BBIM1", "M.DE.B.A2C.P.R.A.2250.EUR.N",
                    "Wohnungsbaukredite, Zinsbindung über 10 Jahre", true, 121, Integer.MAX_VALUE, false));

    /**
     * Referenzzins für die Anzeige.
     *
     * @param period Berichtsmonat (JJJJ-MM)
     * @param stale  veraltet: letzter Abruf zu alt oder Berichtsmonat liegt mehr als 4 Monate zurück
     */
    public record ReferenceRate(String key, String label, double ratePercent, String period, boolean overall,
                                String source, String sourceUrl, Instant fetchedAt, boolean stale, String note) {}

    private final RestClient http;
    private final MarketRateProperties props;
    private final MarketRateStore store;
    private final Clock clock;
    private final Map<String, MarketRateStore.Stored> cache = new ConcurrentHashMap<>();
    private volatile boolean loaded;
    private volatile Instant lastAttempt;
    private volatile String lastError;

    @Autowired
    public MarketRateService(RestClient.Builder builder, MarketRateProperties props, MarketRateStore store) {
        this(builder.requestFactory(timeouts()), props, store, Clock.systemUTC());
    }

    /** Für Tests: Builder an einen Mock-Server gebunden, feste Uhr. */
    MarketRateService(RestClient.Builder builder, MarketRateProperties props, MarketRateStore store, Clock clock) {
        this.props = props;
        this.store = store;
        this.clock = clock;   // echte Zeit (nicht die Simulationsuhr): Aktualität echter Daten
        this.http = builder.baseUrl(props.baseUrl()).build();
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        f.setReadTimeout((int) Duration.ofSeconds(15).toMillis());
        return f;
    }

    public boolean enabled() {
        return props.enabled();
    }

    /** Regelmäßig prüfen, ob ein neuer Abruf fällig ist (nur wenn eingeschaltet). */
    @Scheduled(initialDelayString = "${app.loans.market-rates.initial-delay-ms:20000}",
            fixedDelayString = "${app.loans.market-rates.check-interval-ms:3600000}")
    public void scheduled() {
        if (!props.enabled()) {
            return;
        }
        ensureLoaded();
        Instant now = Instant.now(clock);
        if (lastAttempt == null || !now.isBefore(lastAttempt.plus(Duration.ofHours(props.refreshHours())))) {
            refresh();
        }
    }

    /** Alle Zeitreihen abrufen; liefert die Zahl erfolgreich gelesener Reihen. */
    public synchronized int refresh() {
        ensureLoaded();
        Instant now = Instant.now(clock);
        lastAttempt = now;
        int ok = 0;
        String error = null;
        for (Series s : SERIES) {
            try {
                String csv = http.get()
                        .uri(b -> b.path("/{flow}/{key}").queryParam("lastNObservations", 1)
                                .queryParam("format", "csv").build(s.flow(), s.seriesKey()))
                        .header("Accept", "text/csv, application/vnd.sdmx.data+csv;version=1.0.0")
                        .retrieve().body(String.class);
                Optional<MarketRateStore.Stored> v = parse(csv, now);
                if (v.isPresent()) {
                    cache.put(s.key(), v.get());
                    store.save(s.key(), v.get());
                    ok++;
                } else {
                    error = "Antwort ohne Werte";
                }
            } catch (RestClientException | IllegalStateException e) {
                error = e.getClass().getSimpleName();
            }
        }
        lastError = ok == SERIES.size() ? null : error;
        if (lastError != null) {
            log.warn("Bundesbank-Zinsen: {} von {} Reihen gelesen ({})", ok, SERIES.size(), lastError);
        }
        return ok;
    }

    /**
     * Liest den jüngsten Wert aus einer SDMX-CSV-Antwort (Kopfzeile mit TIME_PERIOD und OBS_VALUE; Trennzeichen
     * Komma oder Semikolon, bei Semikolon auch Dezimalkomma).
     */
    static Optional<MarketRateStore.Stored> parse(String csv, Instant fetchedAt) {
        if (csv == null || csv.isBlank()) {
            return Optional.empty();
        }
        String[] lines = csv.replace("﻿", "").split("\\R");
        int header = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("TIME_PERIOD") && lines[i].contains("OBS_VALUE")) {
                header = i;
                break;
            }
        }
        if (header < 0) {
            return parseSimple(lines, fetchedAt);   // Bundesbank-Tabellenformat: "2026-08;8,99;…"
        }
        char delim = count(lines[header], ';') > count(lines[header], ',') ? ';' : ',';
        List<String> cols = split(lines[header], delim);
        int periodCol = cols.indexOf("TIME_PERIOD");
        int valueCol = cols.indexOf("OBS_VALUE");
        String bestPeriod = null;
        Double bestValue = null;
        for (int i = header + 1; i < lines.length; i++) {
            if (lines[i].isBlank()) {
                continue;
            }
            List<String> cells = split(lines[i], delim);
            if (cells.size() <= Math.max(periodCol, valueCol)) {
                continue;
            }
            String period = cells.get(periodCol).trim();
            String raw = cells.get(valueCol).trim();
            if (!PERIOD.matcher(period).matches() || raw.isEmpty() || raw.equals(".")) {
                continue;
            }
            double value;
            try {
                value = Double.parseDouble(delim == ';' ? raw.replace(',', '.') : raw);
            } catch (NumberFormatException e) {
                continue;
            }
            if (!(value >= 0 && value <= 50)) {
                continue;   // kein plausibler Zinssatz (auch NaN)
            }
            if (bestPeriod == null || period.compareTo(bestPeriod) > 0) {
                bestPeriod = period;
                bestValue = value;
            }
        }
        return bestPeriod == null ? Optional.empty()
                : Optional.of(new MarketRateStore.Stored(bestPeriod, bestValue, fetchedAt));
    }

    /** Ohne Kopfzeile: Zeilen "JJJJ-MM;Wert" (Semikolon, Dezimalkomma) oder "JJJJ-MM,Wert". */
    private static Optional<MarketRateStore.Stored> parseSimple(String[] lines, Instant fetchedAt) {
        String bestPeriod = null;
        Double bestValue = null;
        for (String line : lines) {
            char delim = line.indexOf(';') >= 0 ? ';' : ',';
            List<String> cells = split(line, delim);
            if (cells.size() < 2 || !PERIOD.matcher(cells.get(0).trim()).matches()) {
                continue;
            }
            String raw = cells.get(1).trim();
            try {
                double value = Double.parseDouble(delim == ';' ? raw.replace(',', '.') : raw);
                String period = cells.get(0).trim();
                if (value >= 0 && value <= 50 && (bestPeriod == null || period.compareTo(bestPeriod) > 0)) {
                    bestPeriod = period;
                    bestValue = value;
                }
            } catch (NumberFormatException e) {
                // keine Zahl (z. B. "." für fehlende Werte)
            }
        }
        return bestPeriod == null ? Optional.empty()
                : Optional.of(new MarketRateStore.Stored(bestPeriod, bestValue, fetchedAt));
    }

    private static int count(String s, char c) {
        return (int) s.chars().filter(x -> x == c).count();
    }

    private static List<String> split(String line, char delim) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == delim && !quoted) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

    /** Passende Referenzen für Zweck und Laufzeit: Gesamtwert inkl. Kosten und die Zinsbindungsklasse. */
    public List<ReferenceRate> references(LoanPurpose purpose, int termMonths) {
        if (!props.enabled()) {
            return List.of();
        }
        ensureLoaded();
        boolean housing = purpose == LoanPurpose.IMMOBILIE;
        List<ReferenceRate> list = new ArrayList<>();
        for (Series s : SERIES) {
            if (s.housing() != housing || (!s.overall() && (termMonths < s.minMonths() || termMonths > s.maxMonths()))) {
                continue;
            }
            MarketRateStore.Stored v = cache.get(s.key());
            if (v != null) {
                list.add(new ReferenceRate(s.key(), s.label(), v.ratePercent(), v.period(), s.overall(), SOURCE,
                        SOURCE_URL, v.fetchedAt(), stale(v), NOTE));
            }
        }
        return list;
    }

    boolean stale(MarketRateStore.Stored v) {
        Instant now = Instant.now(clock);
        boolean oldFetch = v.fetchedAt().isBefore(now.minus(Duration.ofDays(props.staleDays())));
        boolean oldPeriod = YearMonth.parse(v.period()).isBefore(YearMonth.now(clock.withZone(BERLIN)).minusMonths(4));
        return oldFetch || oldPeriod;
    }

    /** Zustand: OK, STALE, UNAVAILABLE oder DISABLED. */
    public LoanOfferProvider.Status status() {
        if (!props.enabled()) {
            return new LoanOfferProvider.Status("DISABLED", "Abruf der Bundesbank-Zinsen ist abgeschaltet.", null);
        }
        ensureLoaded();
        if (cache.isEmpty()) {
            return new LoanOfferProvider.Status("UNAVAILABLE", lastAttempt == null
                    ? "Noch nicht abgerufen." : "Bundesbank nicht erreichbar – keine Referenzzinsen.", lastAttempt);
        }
        Instant newest = cache.values().stream().map(MarketRateStore.Stored::fetchedAt).max(Instant::compareTo).orElse(null);
        String period = cache.values().stream().map(MarketRateStore.Stored::period).max(String::compareTo).orElse("");
        boolean anyStale = cache.values().stream().anyMatch(this::stale);
        if (anyStale || lastError != null) {
            return new LoanOfferProvider.Status("STALE", "Daten möglicherweise veraltet (Stand " + period + ").", newest);
        }
        return new LoanOfferProvider.Status("OK", "Stand " + period + ".", newest);
    }

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        synchronized (this) {
            if (!loaded) {
                try {
                    store.load().forEach(cache::putIfAbsent);
                    loaded = true;
                } catch (RuntimeException e) {
                    log.warn("Gespeicherte Bundesbank-Zinsen nicht lesbar: {}", e.getClass().getSimpleName());
                }
            }
        }
    }
}
