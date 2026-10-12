import 'package:flutter_test/flutter_test.dart';
import 'package:girokonto_app/features/notifications/background_check.dart';

/// /K070/ Hintergrundabfrage: welche Meldungen sind neu?
void main() {
  final items = [
    {'id': 12, 'title': 'C', 'body': '', 'read': false},
    {'id': 11, 'title': 'B', 'body': '', 'read': true},
    {'id': 10, 'title': 'A', 'body': '', 'read': false},
    {'id': 8, 'title': 'alt', 'body': '', 'read': false},
  ];

  test('Erster Lauf zeigt nichts (keine alten Meldungen)', () {
    expect(BackgroundCheck.selectNew(items, null), isEmpty);
  });

  test('Nur ungelesene, neuer als zuletzt angezeigt, älteste zuerst', () {
    final fresh = BackgroundCheck.selectNew(items, 9);
    expect(fresh.map((n) => n['title']), ['A', 'C']);
  });

  test('Nichts Neues', () {
    expect(BackgroundCheck.selectNew(items, 12), isEmpty);
  });
}
