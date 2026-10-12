import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:workmanager/workmanager.dart';

import '../../core/api_client.dart';
import '../../core/config.dart';
import '../../core/key_value_store.dart';
import '../../core/token_storage.dart';
import 'local_notifications.dart';

/// /K070/ Benachrichtigungen bei geschlossener App – ohne Push-Dienst (Firebase/APNs).
///
/// Android führt mit WorkManager ungefähr alle 15 Minuten eine Hintergrundaufgabe aus. Sie holt die
/// neuesten ungelesenen Meldungen vom Server und zeigt neue als Systembenachrichtigung an.
/// Echte Push-Nachrichten (sofort) bräuchten Firebase Cloud Messaging bzw. Apple Push Notification
/// Service – siehe docs/technik/push.md.
class BackgroundCheck {
  static const taskName = 'girokonto-benachrichtigungen';
  static const _lastKey = 'last_notified_id';

  static bool get isSupported => !kIsWeb && defaultTargetPlatform == TargetPlatform.android;

  /// Nach dem Anmelden: Hintergrundaufgabe einplanen (bleibt auch nach Neustart des Handys bestehen).
  static Future<void> start() async {
    if (!isSupported) return;
    try {
      await Workmanager().initialize(callbackDispatcher);
      await Workmanager().registerPeriodicTask(
        taskName,
        taskName,
        frequency: const Duration(minutes: 15),
        constraints: Constraints(networkType: NetworkType.connected),
        existingWorkPolicy: ExistingPeriodicWorkPolicy.keep,
      );
    } catch (e) {
      debugPrint('Hintergrundabfrage nicht verfügbar: $e');
    }
  }

  /// Beim Abmelden: keine Abfragen mehr.
  static Future<void> stop() async {
    if (!isSupported) return;
    try {
      await Workmanager().cancelByUniqueName(taskName);
      await KeyValueStore.instance.delete(_lastKey);
    } catch (_) {}
  }

  /// Meldung wurde schon angezeigt (z. B. live in der App) – im Hintergrund nicht noch einmal.
  static Future<void> markShown(int id) async {
    final last = int.tryParse(await KeyValueStore.instance.read(_lastKey) ?? '') ?? 0;
    if (id > last) await KeyValueStore.instance.write(_lastKey, '$id');
  }

  /// Neue ungelesene Meldungen holen und anzeigen. Gibt die Anzahl angezeigter Meldungen zurück.
  static Future<int> checkOnce() async {
    await AppConfig.load();
    final storage = TokenStorage();
    if (await storage.readRefreshToken() == null) return 0;     // abgemeldet
    final api = ApiClient(storage);
    final res = await api.dio.get<Map<String, dynamic>>('/api/notifications', queryParameters: {'size': 20});
    final items = (res.data!['items'] as List<dynamic>).cast<Map<String, dynamic>>();
    final lastRaw = await KeyValueStore.instance.read(_lastKey);
    final fresh = selectNew(items, lastRaw == null ? null : int.tryParse(lastRaw));
    if (items.isNotEmpty) {
      final maxId = items.map((n) => (n['id'] as num).toInt()).reduce((a, b) => a > b ? a : b);
      await markShown(maxId);
    }
    if (fresh.isEmpty) return 0;
    await LocalNotifications.init(requestPermission: false);
    for (final n in fresh) {
      await LocalNotifications.show((n['id'] as num).toInt(), n['title'] as String, n['body'] as String);
    }
    return fresh.length;
  }

  /// Welche Meldungen sind neu? Beim allerersten Lauf (lastShown == null) keine, damit nicht
  /// plötzlich alte Meldungen auftauchen. Älteste zuerst.
  @visibleForTesting
  static List<Map<String, dynamic>> selectNew(List<Map<String, dynamic>> items, int? lastShown) {
    if (lastShown == null) return const [];
    final fresh = items
        .where((n) => n['read'] != true && (n['id'] as num).toInt() > lastShown)
        .toList()
      ..sort((a, b) => (a['id'] as num).compareTo(b['id'] as num));
    return fresh;
  }
}

/// Einstieg für die Hintergrundaufgabe (läuft in einer eigenen Dart-Umgebung ohne Oberfläche).
@pragma('vm:entry-point')
void callbackDispatcher() {
  Workmanager().executeTask((task, inputData) async {
    WidgetsFlutterBinding.ensureInitialized();
    try {
      await BackgroundCheck.checkOnce();
    } catch (e) {
      debugPrint('Hintergrundabfrage fehlgeschlagen: $e');
    }
    return true; // auch bei Fehlern: nächster Versuch beim nächsten Intervall
  });
}
