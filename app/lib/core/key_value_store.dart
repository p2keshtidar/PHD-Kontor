import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Kleiner Schlüssel-Wert-Speicher für Tokens und Einstellungen.
///
/// * Android/iOS: verschlüsselt (Keystore bzw. Keychain) über flutter_secure_storage
/// * Browser: localStorage über shared_preferences. Die sichere Variante (WebCrypto) funktioniert
///   nur unter https oder localhost – auf dem Handy über http://<IP-des-PCs>:8080 nicht.
/// * Klappt beides nicht, bleiben die Werte wenigstens bis zum Schließen der App im Speicher.
class KeyValueStore {
  KeyValueStore._();
  static final instance = KeyValueStore._();

  static const _secure = FlutterSecureStorage();
  final Map<String, String> _memory = {};

  /// Nur für Tests: alles bleibt im Arbeitsspeicher (keine Plattform-Kanäle).
  @visibleForTesting
  static bool memoryOnly = false;

  Future<String?> read(String key) async {
    if (memoryOnly) return _memory[key];
    try {
      if (kIsWeb) return (await SharedPreferences.getInstance()).getString(key);
      return await _secure.read(key: key);
    } catch (_) {
      return _memory[key];
    }
  }

  Future<void> write(String key, String value) async {
    _memory[key] = value;
    if (memoryOnly) return;
    try {
      if (kIsWeb) {
        await (await SharedPreferences.getInstance()).setString(key, value);
      } else {
        await _secure.write(key: key, value: value);
      }
    } catch (_) {}
  }

  Future<void> delete(String key) async {
    _memory.remove(key);
    if (memoryOnly) return;
    try {
      if (kIsWeb) {
        await (await SharedPreferences.getInstance()).remove(key);
      } else {
        await _secure.delete(key: key);
      }
    } catch (_) {}
  }
}
