import '../l10n/l10n.dart';

/// Formatiert einen Betrag in Cent als deutschen Euro-Betrag, z. B. 123456 -> "1.234,56 €".
String formatCents(int cents) {
  final negative = cents < 0;
  final abs = cents.abs();
  final euros = (abs ~/ 100).toString();
  final rest = (abs % 100).toString().padLeft(2, '0');

  final grouped = StringBuffer();
  for (var i = 0; i < euros.length; i++) {
    final remaining = euros.length - i;
    if (i > 0 && remaining % 3 == 0) grouped.write('.');
    grouped.write(euros[i]);
  }
  if (L10n.isEnglish) {
    return '${negative ? '-' : ''}€${grouped.toString().replaceAll('.', ',')}.$rest';
  }
  return '${negative ? '-' : ''}$grouped,$rest €';
}

/// Formatiert eine IBAN in Vierergruppen: "DE23 0001 0001 1000 0000 01".
String formatIban(String iban) {
  final clean = iban.replaceAll(' ', '');
  final parts = <String>[];
  for (var i = 0; i < clean.length; i += 4) {
    parts.add(clean.substring(i, i + 4 > clean.length ? clean.length : i + 4));
  }
  return parts.join(' ');
}

/// Liest eine Euro-Eingabe und gibt Cent zurück, z. B. "25,50" -> 2550, "1.234,5" -> 123450.
/// Gibt null zurück bei ungültiger Eingabe oder mehr als zwei Nachkommastellen.
int? parseEuroToCents(String input) {
  var s = input.trim().replaceAll('€', '').replaceAll(' ', '');
  if (s.isEmpty) return null;
  if (L10n.isEnglish && (s.contains('.') || RegExp(r'^\d{1,3}(,\d{3})+$').hasMatch(s))) {
    // englisches Format: Kommas sind Tausendertrenner ("1,234.50")
    s = s.replaceAll(',', '');
  } else if (s.contains(',')) {
    // deutsches Format: Punkte sind Tausendertrenner
    s = s.replaceAll('.', '').replaceAll(',', '.');
  }
  if (!RegExp(r'^\d+(\.\d{1,2})?$').hasMatch(s)) return null;
  final parts = s.split('.');
  final euros = int.parse(parts[0]);
  final cents = parts.length == 2 ? int.parse(parts[1].padRight(2, '0')) : 0;
  return euros * 100 + cents;
}
