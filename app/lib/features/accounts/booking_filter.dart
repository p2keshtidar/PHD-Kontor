/// Filter der Umsatzliste (/F130/). Alle Felder optional und kombinierbar.
class BookingFilter {
  const BookingFilter({
    this.from,
    this.to,
    this.direction,
    this.minCents,
    this.maxCents,
    this.type,
    this.query = '',
  });

  final DateTime? from;
  final DateTime? to;

  /// 'IN' oder 'OUT'
  final String? direction;
  final int? minCents;
  final int? maxCents;
  final String? type;
  final String query;

  /// Anzahl gesetzter Filter (ohne Suchtext), für das Abzeichen am Filter-Symbol.
  int get activeCount => [from ?? to, direction, minCents ?? maxCents, type]
      .where((e) => e != null)
      .length;

  BookingFilter withQuery(String q) => BookingFilter(
        from: from,
        to: to,
        direction: direction,
        minCents: minCents,
        maxCents: maxCents,
        type: type,
        query: q,
      );

  BookingFilter withoutFilters() => BookingFilter(query: query);

  Map<String, dynamic> toQueryParameters() {
    String date(DateTime d) =>
        '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';
    return {
      if (from != null) 'from': date(from!),
      if (to != null) 'to': date(to!),
      if (direction != null) 'direction': direction,
      if (minCents != null) 'minCents': minCents,
      if (maxCents != null) 'maxCents': maxCents,
      if (type != null) 'type': type,
      if (query.trim().isNotEmpty) 'q': query.trim(),
    };
  }
}
