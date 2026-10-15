package de.frauas.girokonto.i18n;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Übersetzt Texte des Servers (Fehlermeldungen, Benachrichtigungen, Bezeichnungen) ins Englische.
 *
 * <p>Die Texte entstehen auf Deutsch im Code; {@code i18n/en.tsv} enthält die englischen Gegenstücke.
 * Einträge mit Platzhaltern ({0}, {1} …) werden als Muster verglichen, die eingesetzten Werte werden
 * ebenfalls übersetzt (z. B. Kategorie-Namen). Zum Schluss werden Beträge, Datumsangaben und Prozente
 * in englische Schreibweise gebracht ("1.234,56 €" → "€1,234.56", "01.11.2026" → "1 Nov 2026").
 * Unbekannte Texte bleiben unverändert (z. B. Namen und Verwendungszwecke der Nutzer).
 */
@Component
public class ServerTexts {

    private record Rule(Pattern pattern, int[] groups, String english, int literalLength) {}

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)}");
    private static final Pattern MONEY = Pattern.compile("(-?)(\\d{1,3}(?:\\.\\d{3})*),(\\d{2}) €");
    private static final Pattern DATE = Pattern.compile("\\b(\\d{2})\\.(\\d{2})\\.(\\d{4})\\b");
    private static final Pattern DAY_MONTH = Pattern.compile("\\b(\\d{2})\\.(\\d{2})\\.(?!\\d)");
    private static final Pattern PERCENT = Pattern.compile("(\\d+),(\\d+) %|(\\d+) %");
    private static final String[] MONTHS_DE = {"Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August",
            "September", "Oktober", "November", "Dezember"};
    private static final String[] MONTHS_EN = {"January", "February", "March", "April", "May", "June", "July",
            "August", "September", "October", "November", "December"};
    private static final String[] SHORT_EN = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct",
            "Nov", "Dec"};
    private static final Pattern MONTH_YEAR = Pattern.compile("\\b(" + String.join("|", MONTHS_DE) + ") (\\d{4})\\b");

    private final Map<String, String> exact = new HashMap<>();
    private final List<Rule> rules = new ArrayList<>();

    public ServerTexts() {
        try (InputStream in = ServerTexts.class.getResourceAsStream("/i18n/en.tsv")) {
            if (in == null) {
                throw new IllegalStateException("i18n/en.tsv fehlt");
            }
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                int tab = line.indexOf('\t');
                if (tab < 0) {
                    throw new IllegalStateException("Zeile ohne Tabulator in en.tsv: " + line);
                }
                add(line.substring(0, tab), line.substring(tab + 1));
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        rules.sort(Comparator.comparingInt(Rule::literalLength).reversed());
    }

    private void add(String german, String english) {
        Matcher m = PLACEHOLDER.matcher(german);
        if (!m.find()) {
            exact.put(german, english);
            return;
        }
        m.reset();
        StringBuilder regex = new StringBuilder();
        List<Integer> groups = new ArrayList<>();
        int last = 0;
        int literal = 0;
        while (m.find()) {
            regex.append(Pattern.quote(german.substring(last, m.start()))).append("(.+?)");
            literal += m.start() - last;
            groups.add(Integer.parseInt(m.group(1)));
            last = m.end();
        }
        regex.append(Pattern.quote(german.substring(last)));
        literal += german.length() - last;
        rules.add(new Rule(Pattern.compile(regex.toString(), Pattern.DOTALL),
                groups.stream().mapToInt(Integer::intValue).toArray(), english, literal));
    }

    /** Möchte der Aufrufer Englisch? (Accept-Language beginnt mit "en") */
    public static boolean wantsEnglish(String acceptLanguage) {
        return acceptLanguage != null && acceptLanguage.trim().toLowerCase(Locale.ROOT).startsWith("en");
    }

    /** Übersetzt einen Text ins Englische; unbekannte Texte bleiben (bis auf Zahlenformate) unverändert. */
    public String toEnglish(String german) {
        if (german == null || german.isEmpty()) {
            return german;
        }
        return formats(translate(german, 0));
    }

    /** Gibt es einen Eintrag (genau oder als Muster)? Für Tests. */
    public boolean knows(String german) {
        if (exact.containsKey(german)) {
            return true;
        }
        return rules.stream().anyMatch(r -> r.pattern().matcher(german).matches());
    }

    private String translate(String s, int depth) {
        String e = exact.get(s);
        if (e != null) {
            return e;
        }
        if (depth < 3) {
            for (Rule r : rules) {
                Matcher m = r.pattern().matcher(s);
                if (m.matches()) {
                    String out = r.english();
                    for (int i = 0; i < r.groups().length; i++) {
                        out = out.replace("{" + r.groups()[i] + "}", translate(m.group(i + 1), depth + 1));
                    }
                    return out;
                }
            }
        }
        return s;
    }

    static String formats(String s) {
        s = replace(MONEY, s, m -> m.group(1) + "€" + m.group(2).replace('.', ',') + "." + m.group(3));
        s = replace(DATE, s, m -> Integer.parseInt(m.group(1)) + " " + SHORT_EN[Integer.parseInt(m.group(2)) - 1]
                + " " + m.group(3));
        s = replace(DAY_MONTH, s, m -> {
            int month = Integer.parseInt(m.group(2));
            return month >= 1 && month <= 12 ? Integer.parseInt(m.group(1)) + " " + SHORT_EN[month - 1] : m.group();
        });
        s = replace(PERCENT, s, m -> m.group(3) != null ? m.group(3) + "%" : m.group(1) + "." + m.group(2) + "%");
        s = replace(MONTH_YEAR, s, m -> {
            for (int i = 0; i < MONTHS_DE.length; i++) {
                if (MONTHS_DE[i].equals(m.group(1))) {
                    return MONTHS_EN[i] + " " + m.group(2);
                }
            }
            return m.group();
        });
        return s;
    }

    private static String replace(Pattern p, String s, java.util.function.Function<Matcher, String> f) {
        Matcher m = p.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(f.apply(m)));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
