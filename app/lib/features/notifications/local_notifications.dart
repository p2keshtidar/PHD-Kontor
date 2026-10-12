import 'package:flutter/foundation.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';

/// Systembenachrichtigungen auf iOS und Android (/F510/). Im Browser nicht verfügbar.
class LocalNotifications {
  static final _plugin = FlutterLocalNotificationsPlugin();
  static bool _ready = false;

  static bool get isSupported =>
      !kIsWeb &&
      (defaultTargetPlatform == TargetPlatform.android ||
          defaultTargetPlatform == TargetPlatform.iOS);

  /// [requestPermission]: im Hintergrund (ohne sichtbare App) nicht nachfragen.
  static Future<void> init({bool requestPermission = true}) async {
    if (!isSupported || _ready) return;
    try {
      const settings = InitializationSettings(
        android: AndroidInitializationSettings('@mipmap/ic_launcher'),
        iOS: DarwinInitializationSettings(),
      );
      await _plugin.initialize(settings);
      // Android 13+: Erlaubnis für Benachrichtigungen erfragen
      if (requestPermission) {
        await _plugin
          .resolvePlatformSpecificImplementation<AndroidFlutterLocalNotificationsPlugin>()
            ?.requestNotificationsPermission();
      }
      _ready = true;
    } catch (e) {
      debugPrint('Lokale Benachrichtigungen nicht verfügbar: $e');
    }
  }

  static Future<void> show(int id, String title, String body) async {
    if (!_ready) return;
    const details = NotificationDetails(
      android: AndroidNotificationDetails(
        'buchungen',
        'Buchungen',
        channelDescription: 'Meldungen zu Ein- und Ausgängen',
        importance: Importance.high,
        priority: Priority.high,
      ),
      iOS: DarwinNotificationDetails(),
    );
    try {
      await _plugin.show(id, title, body, details);
    } catch (e) {
      debugPrint('Benachrichtigung fehlgeschlagen: $e');
    }
  }
}
