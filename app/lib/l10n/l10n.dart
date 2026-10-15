import 'package:flutter/foundation.dart';

import 'strings_en.dart';

/// Zweisprachigkeit (Deutsch/Englisch).
///
/// Texte stehen im Code auf Deutsch und werden mit [tr] übersetzt; die englischen Texte stehen zentral
/// in `strings_en.dart`. Platzhalter: `tr('Hallo {0}!', [name])`.
/// Die gewählte Sprache wird lokal gespeichert und beim Nutzerkonto hinterlegt (siehe language_controller.dart).
class L10n {
  L10n._();

  static const supported = ['de', 'en'];
  static const names = {'de': 'Deutsch', 'en': 'English'};

  /// Aktuelle Sprache. Die App baut sich neu auf, wenn sich der Wert ändert.
  static final language = ValueNotifier<String>('de');

  static String get lang => language.value;
  static bool get isEnglish => language.value == 'en';

  /// Erste Sprache: Gerätesprache, falls unterstützt, sonst Deutsch.
  static String fromDevice(String? languageCode) =>
      languageCode != null && supported.contains(languageCode) ? languageCode : 'de';
}

/// Übersetzt einen deutschen Text in die aktuelle Sprache. Fehlt eine Übersetzung, bleibt der deutsche Text.
String tr(String german, [List<Object?> args = const []]) {
  var s = L10n.isEnglish ? (stringsEn[german] ?? german) : german;
  for (var i = 0; i < args.length; i++) {
    s = s.replaceAll('{$i}', '${args[i]}');
  }
  return s;
}

/// Dezimalzahl in der Schreibweise der Sprache: "4,2" bzw. "4.2".
extension LocalDecimal on String {
  String get localDecimal => L10n.isEnglish ? this : replaceAll('.', ',');
}

/// Für Tests: Sprache setzen.
@visibleForTesting
void setLanguageForTest(String lang) => L10n.language.value = lang;
