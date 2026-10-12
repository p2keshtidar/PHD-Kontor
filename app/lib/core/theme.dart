import 'package:flutter/material.dart';

const _seed = Color(0xFF0F8B8D);

ThemeData buildTheme(Brightness brightness) {
  final scheme = ColorScheme.fromSeed(seedColor: _seed, brightness: brightness);
  return ThemeData(
    useMaterial3: true,
    colorScheme: scheme,
    inputDecorationTheme: const InputDecorationTheme(border: OutlineInputBorder()),
  );
}
