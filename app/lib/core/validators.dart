import '../l10n/l10n.dart';

// Eingabeprüfungen, die auch das Backend macht. In der App sorgen sie für schnelle Rückmeldung.

const minAge = 18;
const minPasswordLength = 10;

/// true, wenn die Person am Tag [today] mindestens 18 Jahre alt ist.
bool isAdult(DateTime birthDate, DateTime today) {
  var age = today.year - birthDate.year;
  final hadBirthday = today.month > birthDate.month ||
      (today.month == birthDate.month && today.day >= birthDate.day);
  if (!hadBirthday) age--;
  return age >= minAge;
}

String? validateEmail(String? value) {
  final v = value?.trim() ?? '';
  if (v.isEmpty) return tr('Bitte E-Mail eingeben');
  if (!RegExp(r'^[^@\s]+@[^@\s]+\.[^@\s]+$').hasMatch(v)) {
    return tr('E-Mail ist ungültig');
  }
  return null;
}

String? validateRequired(String? value, String label) {
  if (value == null || value.trim().isEmpty) return tr('{0} fehlt', [label]);
  return null;
}

String? validatePhone(String? value) {
  final v = value?.trim() ?? '';
  if (v.isEmpty) return tr('Telefonnummer fehlt');
  if (!RegExp(r'^\+?[0-9 ]{6,20}$').hasMatch(v)) {
    return tr('Telefonnummer ist ungültig');
  }
  return null;
}

String? validateNewPassword(String? value) {
  if (value == null || value.length < minPasswordLength) {
    return tr('Mindestens {0} Zeichen', [minPasswordLength]);
  }
  return null;
}

/// Datum als "yyyy-MM-dd" für das Backend.
String toIsoDate(DateTime d) =>
    '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

/// Datum als "dd.MM.yyyy" für die Anzeige.
String toGermanDate(DateTime d) =>
    '${d.day.toString().padLeft(2, '0')}.${d.month.toString().padLeft(2, '0')}.${d.year}';
