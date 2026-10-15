import '../l10n/l10n.dart';

String _two(int n) => n.toString().padLeft(2, '0');

const _monthNames = [
  'Januar', 'Februar', 'März', 'April', 'Mai', 'Juni',
  'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember',
];
const _shortEn = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

/// "12.10.2026, 15:45" bzw. "12 Oct 2026, 15:45"
String formatDateTime(DateTime d) {
  final l = d.toLocal();
  return '${formatDate(l)}, ${_two(l.hour)}:${_two(l.minute)}';
}

/// "12.10.2026" bzw. "12 Oct 2026"
String formatDate(DateTime d) {
  final l = d.toLocal();
  if (L10n.isEnglish) return '${l.day} ${_shortEn[l.month - 1]} ${l.year}';
  return '${_two(l.day)}.${_two(l.month)}.${l.year}';
}

/// Überschrift für Umsatzgruppen: "Heute", "Gestern" oder Datum.
String dayHeader(DateTime d, DateTime now) {
  final l = d.toLocal();
  final day = DateTime(l.year, l.month, l.day);
  final today = DateTime(now.year, now.month, now.day);
  final diff = today.difference(day).inDays;
  if (diff == 0) return tr('Heute');
  if (diff == 1) return tr('Gestern');
  return formatDate(l);
}

/// Monatsname in der aktuellen Sprache (1 = Januar)
String monthName(int month) => tr(_monthNames[month - 1]);

/// Kurzer Monatsname ("Mär" / "Mar")
String monthShort(int month) =>
    L10n.isEnglish ? _shortEn[month - 1] : _monthNames[month - 1].substring(0, 3);

/// "März 2030"
String formatMonthYear(DateTime d) => '${monthName(d.month)} ${d.year}';
