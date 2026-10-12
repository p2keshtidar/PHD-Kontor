import 'package:flutter/foundation.dart';

import 'key_value_store.dart';

/// Adresse des Backends.
///
/// Reihenfolge:
///  1. in der App gespeicherte Adresse (Login-Seite -> Zahnrad "Server-Adresse")
///  2. --dart-define=API_URL=...
///  3. Standard je Plattform:
///     * Browser: dieselbe Adresse, von der die Web-App geladen wurde (Backend liefert sie aus);
///       beim Entwickeln mit "flutter run -d chrome" http://localhost:8080
///     * Android: http://10.0.2.2:8080 (Emulator -> eigener PC)
///     * iOS/Desktop: http://localhost:8080
///
/// Echtes Handy im WLAN: Server-Adresse auf http://<IP-deines-PCs>:8080 setzen.
class AppConfig {
  static const _key = 'server_url';
  static const _fromDefine = String.fromEnvironment('API_URL');

  static String apiBaseUrl = defaultUrl;

  static String get defaultUrl {
    if (_fromDefine.isNotEmpty) return _fromDefine;
    if (kIsWeb) {
      final base = Uri.base;
      final local = base.host == 'localhost' || base.host == '127.0.0.1';
      if (local && base.port != 8080) return 'http://localhost:8080';
      return base.origin;
    }
    if (defaultTargetPlatform == TargetPlatform.android) return 'http://10.0.2.2:8080';
    return 'http://localhost:8080';
  }

  /// Beim App-Start: gespeicherte Adresse laden.
  static Future<void> load() async {
    final saved = await KeyValueStore.instance.read(_key);
    if (saved != null && saved.isNotEmpty) apiBaseUrl = saved;
  }

  /// Neue Adresse setzen; leer = Standard.
  static Future<void> save(String url) async {
    var u = url.trim();
    while (u.endsWith('/')) {
      u = u.substring(0, u.length - 1);
    }
    if (u.isNotEmpty && !u.startsWith('http')) u = 'http://$u';
    apiBaseUrl = u.isEmpty ? defaultUrl : u;
    if (u.isEmpty) {
      await KeyValueStore.instance.delete(_key);
    } else {
      await KeyValueStore.instance.write(_key, u);
    }
  }
}
