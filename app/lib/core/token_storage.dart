import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'key_value_store.dart';

/// Speichert Access- und Refresh-Token auf dem Gerät (siehe [KeyValueStore]).
class TokenStorage {
  TokenStorage([KeyValueStore? store]) : _store = store ?? KeyValueStore.instance;

  final KeyValueStore _store;

  static const _accessKey = 'access_token';
  static const _refreshKey = 'refresh_token';

  Future<String?> readAccessToken() => _store.read(_accessKey);

  Future<String?> readRefreshToken() => _store.read(_refreshKey);

  Future<void> save(String accessToken, String refreshToken) async {
    await _store.write(_accessKey, accessToken);
    await _store.write(_refreshKey, refreshToken);
  }

  Future<void> clear() async {
    await _store.delete(_accessKey);
    await _store.delete(_refreshKey);
  }
}

final tokenStorageProvider = Provider<TokenStorage>((ref) => TokenStorage());
