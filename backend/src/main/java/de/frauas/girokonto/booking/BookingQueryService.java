package de.frauas.girokonto.booking;

import de.frauas.girokonto.common.PageDto;
import de.frauas.girokonto.config.AppProperties;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Liest Umsätze eines Kontos mit Filter, Suche und Seiten (/F120/, /F130/).
 *
 * Die Gegenpartei wird aus der anderen Zeile derselben Buchung ermittelt
 * (bei mehreren Zeilen: die mit dem größten Betrag). SQL wird je nach gesetzten Filtern
 * zusammengebaut; alle Werte gehen als Parameter hinein (kein SQL-Injection-Risiko).
 */
@Service
public class BookingQueryService {

    public static final int MAX_PAGE_SIZE = 50;

    private static final String BASE_SQL = """
            SELECT b.id, b.type, b.description, b.booked_at, l.amount_cents, l.category,
                   cp.owner_id AS cp_owner_id, cp.type AS cp_type, cp.name AS cp_name, cp.iban AS cp_iban,
                   u.first_name AS cp_first_name, u.last_name AS cp_last_name
              FROM booking_line l
              JOIN booking b ON b.id = l.booking_id
              LEFT JOIN LATERAL (
                    SELECT a.owner_id, a.type, a.name, a.iban
                      FROM booking_line l2
                      JOIN account a ON a.id = l2.account_id
                     WHERE l2.booking_id = l.booking_id AND l2.account_id <> l.account_id
                     ORDER BY abs(l2.amount_cents) DESC, l2.id
                     LIMIT 1
                   ) cp ON TRUE
              LEFT JOIN app_user u ON u.id = cp.owner_id
             WHERE l.account_id = :accountId
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final AppProperties props;
    private final Clock clock;

    public BookingQueryService(NamedParameterJdbcTemplate jdbc, AppProperties props, Clock clock) {
        this.jdbc = jdbc;
        this.props = props;
        this.clock = clock;
    }

    /** Das Konto muss vorher als Konto des Nutzers geprüft sein (siehe AccountService.requireOwned). */
    @Transactional(readOnly = true)
    public PageDto<BookingItemDto> find(Long accountId, Long currentUserId, BookingFilter f, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);

        StringBuilder sql = new StringBuilder(BASE_SQL);
        MapSqlParameterSource params = new MapSqlParameterSource("accountId", accountId);

        if (f.from() != null) {
            sql.append(" AND b.booked_at >= :from");
            params.addValue("from", startOfDay(f.from()));
        }
        if (f.to() != null) {
            sql.append(" AND b.booked_at < :to");
            params.addValue("to", startOfDay(f.to().plusDays(1)));
        }
        if (f.direction() == BookingFilter.Direction.IN) {
            sql.append(" AND l.amount_cents > 0");
        } else if (f.direction() == BookingFilter.Direction.OUT) {
            sql.append(" AND l.amount_cents < 0");
        }
        if (f.minCents() != null) {
            sql.append(" AND abs(l.amount_cents) >= :minCents");
            params.addValue("minCents", f.minCents());
        }
        if (f.maxCents() != null) {
            sql.append(" AND abs(l.amount_cents) <= :maxCents");
            params.addValue("maxCents", f.maxCents());
        }
        if (f.type() != null) {
            sql.append(" AND b.type = :type");
            params.addValue("type", f.type().name());
        }
        if (f.q() != null && !f.q().isBlank()) {
            sql.append("""
                     AND (b.description ILIKE :q ESCAPE '\\'
                          OR (u.first_name || ' ' || u.last_name) ILIKE :q ESCAPE '\\'
                          OR cp.name ILIKE :q ESCAPE '\\')
                    """);
            params.addValue("q", "%" + escapeLike(f.q().trim()) + "%");
        }
        // eine Zeile mehr holen, um zu wissen, ob es weitere Seiten gibt
        sql.append(" ORDER BY b.booked_at DESC, b.id DESC LIMIT :limit OFFSET :offset");
        params.addValue("limit", safeSize + 1);
        params.addValue("offset", (long) safePage * safeSize);

        List<BookingItemDto> rows = new ArrayList<>(
                jdbc.query(sql.toString(), params, (rs, i) -> map(rs, currentUserId)));
        boolean hasMore = rows.size() > safeSize;
        if (hasMore) {
            rows.remove(rows.size() - 1);
        }
        return new PageDto<>(rows, safePage, safeSize, hasMore);
    }

    private BookingItemDto map(ResultSet rs, Long currentUserId) throws SQLException {
        long cpOwner = rs.getLong("cp_owner_id");
        boolean hasOwner = !rs.wasNull();
        String cpType = rs.getString("cp_type");
        String cpName = rs.getString("cp_name");
        String name;
        if (cpType == null) {
            name = "";
        } else if ("SYSTEM".equals(cpType)) {
            if ("BANK".equals(cpName)) {
                name = props.bank().name();
            } else {
                // Händler, Arbeitgeber, Lastschrift, Depot, Zinsen: der Verwendungszweck nennt die Gegenpartei
                name = rs.getString("description");
            }
        } else if (hasOwner && currentUserId != null && cpOwner == currentUserId) {
            name = cpName; // eigenes Unterkonto
        } else {
            name = rs.getString("cp_first_name") + " " + rs.getString("cp_last_name");
        }
        OffsetDateTime bookedAt = rs.getObject("booked_at", OffsetDateTime.class);
        String category = rs.getString("category");
        return new BookingItemDto(
                rs.getLong("id"),
                BookingType.valueOf(rs.getString("type")),
                rs.getString("description"),
                bookedAt.toInstant(),
                rs.getLong("amount_cents"),
                name,
                rs.getString("cp_iban"),
                category,
                categoryLabel(category));
    }

    private OffsetDateTime startOfDay(java.time.LocalDate date) {
        return date.atStartOfDay(clock.getZone()).toInstant().atOffset(ZoneOffset.UTC);
    }

    private static String categoryLabel(String category) {
        if (category == null) {
            return null;
        }
        try {
            return de.frauas.girokonto.insights.Category.valueOf(category).label();
        } catch (IllegalArgumentException e) {
            return category;
        }
    }

    static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
