package de.frauas.girokonto.group;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** W01–W02: Ausgleichsvorschläge ohne Datenbank. */
class GroupMathTest {

    @Test
    void W01_vorschlaegeGehenGenauAuf() {
        Map<Long, Long> bal = new LinkedHashMap<>();
        bal.put(1L, 10_666L);
        bal.put(2L, -3_833L);
        bal.put(3L, -6_833L);
        List<GroupService.Suggestion> s = GroupService.suggest(bal);
        assertThat(s).hasSize(2);
        assertThat(s.get(0).fromUserId()).isEqualTo(3L);
        assertThat(s.get(0).amountCents()).isEqualTo(6_833L);
        assertThat(s.stream().mapToLong(GroupService.Suggestion::amountCents).sum()).isEqualTo(10_666L);
    }

    @Test
    void W02_mehrereGlaeubigerUndAusgeglichen() {
        Map<Long, Long> bal = new LinkedHashMap<>();
        bal.put(1L, 5_000L);
        bal.put(2L, 3_000L);
        bal.put(3L, -8_000L);
        bal.put(4L, 0L);
        List<GroupService.Suggestion> s = GroupService.suggest(bal);
        assertThat(s).extracting(GroupService.Suggestion::toUserId).containsExactly(1L, 2L);
        assertThat(s).allMatch(x -> x.fromUserId() == 3L);
        assertThat(GroupService.suggest(Map.of(1L, 0L, 2L, 0L))).isEmpty();
    }
}
