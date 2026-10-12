package de.frauas.girokonto.common;

import java.util.List;

/** Eine Seite einer Liste. hasMore = es gibt weitere Einträge (App lädt beim Scrollen nach). */
public record PageDto<T>(List<T> items, int page, int size, boolean hasMore) {
}
