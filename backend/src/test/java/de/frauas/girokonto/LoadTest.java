package de.frauas.girokonto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * T137: einfacher Lasttest – viele Nutzer rufen gleichzeitig die wichtigsten Lese-Endpunkte auf.
 * Gemessen wird die Antwortzeit je Endpunkt (Median, 95-%-Wert, Maximum); die Tabelle steht im Testprotokoll.
 * Grenzen sind bewusst großzügig (CI-Rechner sind unterschiedlich schnell) – es geht um Ausreißer und Fehler.
 */
class LoadTest extends FlowTestSupport {

    static final String[] ENDPOINTS = {"/api/accounts", "/api/dashboard", "/api/transactions?size=50",
            "/api/insights/months?count=6", "/api/forecast", "/api/budgets", "/api/health-score", "/api/notifications"};

    @Test
    void T137_parallelLesenOhneFehlerUndOhneAusreisser() throws Exception {
        int users = 12;
        int rounds = 4;
        List<TestUser> list = new ArrayList<>();
        for (int i = 0; i < users; i++) {
            list.add(userWithDemo("Last" + i));
        }
        Map<String, List<Long>> times = new ConcurrentHashMap<>();
        List<String> errors = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(16);
        List<Future<?>> futures = new ArrayList<>();
        for (TestUser u : list) {
            for (int r = 0; r < rounds; r++) {
                for (String e : ENDPOINTS) {
                    futures.add(pool.submit(() -> {
                        long t0 = System.nanoTime();
                        int s = mvc.perform(get(e).header("Authorization", u.bearer())).andReturn().getResponse().getStatus();
                        long ms = (System.nanoTime() - t0) / 1_000_000;
                        times.computeIfAbsent(e, k -> Collections.synchronizedList(new ArrayList<>())).add(ms);
                        if (s != 200) {
                            errors.add(e + " → " + s);
                        }
                        return null;
                    }));
                }
            }
        }
        for (Future<?> f : futures) {
            f.get(2, TimeUnit.MINUTES);
        }
        pool.shutdown();

        Map<String, long[]> stats = new LinkedHashMap<>();
        StringBuilder table = new StringBuilder("\nLasttest: " + users + " Nutzer × " + rounds + " Runden, 16 Threads\n");
        table.append(String.format("%-32s %6s %8s %8s %8s%n", "Endpunkt", "Anz.", "Median", "p95", "Max"));
        for (String e : ENDPOINTS) {
            List<Long> l = new ArrayList<>(times.get(e));
            Collections.sort(l);
            long median = l.get(l.size() / 2);
            long p95 = l.get((int) Math.ceil(l.size() * 0.95) - 1);
            long max = l.get(l.size() - 1);
            stats.put(e, new long[] {median, p95, max});
            table.append(String.format("%-32s %6d %6d ms %5d ms %5d ms%n", e, l.size(), median, p95, max));
        }
        System.out.println(table);

        assertThat(errors).as("Fehler unter Last").isEmpty();
        stats.forEach((e, s) -> assertThat(s[1]).as("p95 von " + e + " in ms").isLessThan(3_000));
    }
}
