String _two(int n) => n.toString().padLeft(2, '0');

/// "12.10.2026, 15:45"
String formatDateTime(DateTime d) {
  final l = d.toLocal();
  return '${_two(l.day)}.${_two(l.month)}.${l.year}, ${_two(l.hour)}:${_two(l.minute)}';
}

/// "12.10.2026"
String formatDate(DateTime d) {
  final l = d.toLocal();
  return '${_two(l.day)}.${_two(l.month)}.${l.year}';
}

/// Überschrift für Umsatzgruppen: "Heute", "Gestern" oder Datum.
String dayHeader(DateTime d, DateTime now) {
  final l = d.toLocal();
  final day = DateTime(l.year, l.month, l.day);
  final today = DateTime(now.year, now.month, now.day);
  final diff = today.difference(day).inDays;
  if (diff == 0) return 'Heute';
  if (diff == 1) return 'Gestern';
  return formatDate(l);
}
