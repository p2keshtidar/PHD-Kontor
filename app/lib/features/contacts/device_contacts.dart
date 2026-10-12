import 'package:flutter/foundation.dart';
import 'package:flutter_contacts/flutter_contacts.dart';

/// Zugriff auf die Kontakte des Handys (/F220/). Nur iOS und Android; im Browser nicht verfügbar.
/// Die Nummern werden nur zum Abgleich ans Backend geschickt und dort nicht gespeichert (/NF080/).
class DeviceContacts {
  static bool get isSupported =>
      !kIsWeb &&
      (defaultTargetPlatform == TargetPlatform.android ||
          defaultTargetPlatform == TargetPlatform.iOS);

  /// Fragt nach der Erlaubnis und liefert alle Telefonnummern.
  /// Gibt null zurück, wenn der Zugriff verweigert wurde.
  static Future<List<String>?> loadPhoneNumbers() async {
    if (!isSupported) return const [];
    final granted = await FlutterContacts.requestPermission(readonly: true);
    if (!granted) return null;
    final contacts = await FlutterContacts.getContacts(withProperties: true);
    final numbers = <String>{};
    for (final c in contacts) {
      for (final p in c.phones) {
        if (p.number.trim().isNotEmpty) numbers.add(p.number.trim());
      }
    }
    return numbers.take(1000).toList();
  }
}
