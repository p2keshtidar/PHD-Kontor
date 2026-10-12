import 'dart:async';
import 'dart:convert';

import 'package:stomp_dart_client/stomp_dart_client.dart';

import '../features/notifications/app_notification.dart';
import 'api_client.dart';
import 'config.dart';
import 'token_storage.dart';

/// Hält die WebSocket-Verbindung (STOMP) zum Backend und meldet neue Benachrichtigungen (/F510/).
///
/// Bei Verbindungsabbruch wird nach 5 Sekunden mit einem frischen Token neu verbunden.
class RealtimeService {
  RealtimeService({
    required ApiClient api,
    required TokenStorage storage,
    required this.onNotification,
  })  : _api = api,
        _storage = storage;

  final ApiClient _api;
  final TokenStorage _storage;
  final void Function(AppNotification notification) onNotification;

  StompClient? _client;
  Timer? _retry;
  bool _active = false;

  static String get websocketUrl =>
      '${AppConfig.apiBaseUrl.replaceFirst(RegExp(r'^http'), 'ws')}/ws';

  void start() {
    if (_active) return;
    _active = true;
    _connect();
  }

  void stop() {
    _active = false;
    _retry?.cancel();
    final c = _client;
    _client = null;
    c?.deactivate();
  }

  Future<void> _connect() async {
    if (!_active) return;
    try {
      // Ein einfacher Aufruf sorgt dafür, dass das Access-Token gültig ist (der ApiClient erneuert es bei Bedarf).
      await _api.dio.get<dynamic>('/api/notifications/unread-count');
    } catch (_) {
      _scheduleRetry();
      return;
    }
    final token = await _storage.readAccessToken();
    if (token == null || !_active) return;

    final client = StompClient(
      config: StompConfig(
        url: websocketUrl,
        stompConnectHeaders: {'Authorization': 'Bearer $token'},
        reconnectDelay: Duration.zero, // eigenes Wiederverbinden mit frischem Token
        onConnect: _onConnect,
        onStompError: (_) => _scheduleRetry(),
        onWebSocketError: (dynamic _) => _scheduleRetry(),
        onWebSocketDone: _scheduleRetry,
      ),
    );
    _client = client;
    client.activate();
  }

  void _onConnect(StompFrame frame) {
    _client?.subscribe(
      destination: '/user/queue/notifications',
      callback: (StompFrame f) {
        final body = f.body;
        if (body == null) return;
        try {
          onNotification(AppNotification.fromJson(jsonDecode(body) as Map<String, dynamic>));
        } catch (_) {
          // unbekanntes Format ignorieren
        }
      },
    );
  }

  void _scheduleRetry() {
    if (!_active) return;
    _retry?.cancel();
    final c = _client;
    _client = null;
    c?.deactivate();
    _retry = Timer(const Duration(seconds: 5), _connect);
  }
}
