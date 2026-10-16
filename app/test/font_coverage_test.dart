import 'dart:io';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:girokonto_app/core/theme.dart';

/// Schriften (FC01–FC03): Jedes Zeichen, das App oder Server anzeigen, steckt in einer mitgelieferten Schrift.
/// Sonst lädt die Web-App eine Ersatzschrift von Google nach und zeigt ohne Internet Kästchen (z. B. bei „→“).

const _roboto = 'assets/fonts/Roboto-Regular.ttf';
const _symbols = 'assets/fonts/GirokontoSymbols.ttf';
const _serverTexts = '../backend/src/main/resources/i18n/en.tsv';

/// Zeichen einer Schrift aus ihrer cmap-Tabelle (Format 4 und 12 – mehr nutzen Roboto und DejaVu nicht).
Set<int> _codePoints(String path) {
  final b = ByteData.sublistView(File(path).readAsBytesSync());
  int? cmap;
  for (var i = 0; i < b.getUint16(4); i++) {
    final rec = 12 + 16 * i;
    if (String.fromCharCodes([for (var k = 0; k < 4; k++) b.getUint8(rec + k)]) == 'cmap') cmap = b.getUint32(rec + 8);
  }
  if (cmap == null) throw StateError('keine cmap-Tabelle in $path');
  final result = <int>{};
  for (var i = 0; i < b.getUint16(cmap + 2); i++) {
    final rec = cmap + 4 + 8 * i;
    final platform = b.getUint16(rec);
    if (platform != 0 && platform != 3) continue; // nur Unicode-Tabellen
    final sub = cmap + b.getUint32(rec + 4);
    final format = b.getUint16(sub);
    if (format == 4) {
      final segX2 = b.getUint16(sub + 6);
      final ends = sub + 14, starts = ends + segX2 + 2, deltas = starts + segX2, ranges = deltas + segX2;
      for (var s = 0; s < segX2 ~/ 2; s++) {
        final start = b.getUint16(starts + 2 * s), end = b.getUint16(ends + 2 * s);
        final delta = b.getInt16(deltas + 2 * s), rangeOffset = b.getUint16(ranges + 2 * s);
        for (var c = start; c <= end && c != 0xFFFF; c++) {
          var glyph = (c + delta) & 0xFFFF;
          if (rangeOffset != 0) {
            final g = b.getUint16(ranges + 2 * s + rangeOffset + 2 * (c - start));
            glyph = g == 0 ? 0 : (g + delta) & 0xFFFF;
          }
          if (glyph != 0) result.add(c);
        }
      }
    } else if (format == 12) {
      for (var g = 0; g < b.getUint32(sub + 12); g++) {
        final rec2 = sub + 16 + 12 * g;
        for (var c = b.getUint32(rec2); c <= b.getUint32(rec2 + 4); c++) {
          result.add(c);
        }
      }
    }
  }
  return result;
}

/// Zeile ohne Kommentar am Ende („// …“ außerhalb von Anführungszeichen).
String _withoutComment(String line) {
  for (var i = line.indexOf('//'); i >= 0; i = line.indexOf('//', i + 2)) {
    final before = line.substring(0, i);
    if ("'".allMatches(before).length.isEven && '"'.allMatches(before).length.isEven) return before;
  }
  return line;
}

/// Nicht-ASCII-Zeichen, die keine mitgelieferte Schrift enthält, mit Fundstelle.
List<String> _uncovered(Iterable<MapEntry<String, String>> lines, Set<int> covered) => [
      for (final e in lines)
        for (final r in e.value.runes.toSet())
          if (r > 0x7F && !covered.contains(r)) '${e.key}: „${String.fromCharCode(r)}“ (U+${r.toRadixString(16).toUpperCase()})',
    ];

void main() {
  late Set<int> covered;
  setUpAll(() => covered = {..._codePoints(_roboto), ..._codePoints(_symbols)});

  test('FC01 Theme nutzt die mitgelieferte Symbolschrift als Ersatz', () {
    for (final b in Brightness.values) {
      expect(buildTheme(b).textTheme.bodyMedium!.fontFamilyFallback, contains(symbolFontFamily), reason: '$b');
    }
    expect(File('pubspec.yaml').readAsStringSync(), contains('family: $symbolFontFamily'));
    expect(_codePoints(_symbols), containsAll(['→', '⚠', '✓', '☐'].map((s) => s.runes.single)));
  });

  test('FC02 jedes Zeichen in den App-Texten steckt in Roboto oder der Symbolschrift', () {
    final lines = <MapEntry<String, String>>[];
    for (final f in Directory('lib').listSync(recursive: true).whereType<File>().where((f) => f.path.endsWith('.dart'))) {
      final all = f.readAsLinesSync();
      for (var i = 0; i < all.length; i++) {
        if (all[i].trimLeft().startsWith('//')) continue;
        lines.add(MapEntry('${f.path}:${i + 1}', _withoutComment(all[i])));
      }
    }
    expect(lines, isNotEmpty);
    final missing = _uncovered(lines, covered);
    expect(missing, isEmpty, reason: 'Zeichen ohne mitgelieferte Schrift:\n${missing.join('\n')}');
  });

  test('FC03 jedes Zeichen in den Server-Texten (Deutsch und Englisch) ist abgedeckt', () {
    final all = File(_serverTexts).readAsLinesSync();
    final missing = _uncovered([for (var i = 0; i < all.length; i++) MapEntry('en.tsv:${i + 1}', all[i])], covered);
    expect(missing, isEmpty, reason: 'Zeichen ohne mitgelieferte Schrift:\n${missing.join('\n')}');
  }, skip: File(_serverTexts).existsSync() ? false : 'Backend nicht im Arbeitsverzeichnis');
}
