import 'package:flutter/material.dart';

const _seed = Color(0xFF0F8B8D);

ThemeData buildTheme(Brightness brightness) {
  final scheme = ColorScheme.fromSeed(seedColor: _seed, brightness: brightness);
  return ThemeData(
    useMaterial3: true,
    fontFamily: 'Roboto', // mitgeliefert (assets/fonts), kein Download nötig
    colorScheme: scheme,
    inputDecorationTheme: const InputDecorationTheme(border: OutlineInputBorder()),
  );
}

bool _dark(BuildContext context) => Theme.of(context).brightness == Brightness.dark;

/// Farbe für positive Beträge und „gut“. Im dunklen Modus heller, damit Text mindestens 4,5 : 1 Kontrast hat
/// (ein einziges Grün schafft das nicht auf Weiß und auf dunklem Grund zugleich).
Color positiveColor(BuildContext context) => _dark(context) ? const Color(0xFF81C784) : const Color(0xFF2E7D32);

/// Farbe für „knapp/Warnung“ (Text-tauglich in beiden Modi).
Color warningColor(BuildContext context) => _dark(context) ? const Color(0xFFFFD54F) : const Color(0xFF8A5A00);
