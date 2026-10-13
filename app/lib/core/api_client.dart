import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'config.dart';
import 'token_storage.dart';

/// Zentraler HTTP-Client.
///
/// * hängt das Access-Token an jede Anfrage an
/// * bei 401 wird einmal mit dem Refresh-Token ein neues Token-Paar geholt und die Anfrage wiederholt
/// * klappt das nicht, werden die Tokens gelöscht und [onSessionExpired] aufgerufen (-> Login-Seite)
class ApiClient {
  ApiClient(this._storage) {
    final options = BaseOptions(
      baseUrl: AppConfig.apiBaseUrl,
      connectTimeout: const Duration(seconds: 10),
      receiveTimeout: const Duration(seconds: 30), // großzügig für langsame Mobilnetze
      contentType: Headers.jsonContentType,
    );
    dio = Dio(options);
    // Eigener Client ohne Interceptor für Refresh und Wiederholung (verhindert Endlosschleifen).
    _plain = Dio(options);
    dio.interceptors.add(QueuedInterceptorsWrapper(
      onRequest: _onRequest,
      onError: _onError,
    ));
  }

  final TokenStorage _storage;
  late final Dio dio;
  late final Dio _plain;

  /// Wird vom AuthController gesetzt.
  void Function()? onSessionExpired;

  /// Anfragen mit diesem Extra bekommen kein Token (Login, Registrierung).
  static Options noAuth() => Options(extra: {'noAuth': true});

  Future<void> _onRequest(
      RequestOptions options, RequestInterceptorHandler handler) async {
    options.baseUrl = AppConfig.apiBaseUrl;
    if (options.extra['noAuth'] != true) {
      final token = await _storage.readAccessToken();
      if (token != null) {
        options.headers['Authorization'] = 'Bearer $token';
      }
    }
    handler.next(options);
  }

  Future<void> _onError(
      DioException error, ErrorInterceptorHandler handler) async {
    final request = error.requestOptions;
    final isUnauthorized = error.response?.statusCode == 401;
    if (!isUnauthorized || request.extra['noAuth'] == true) {
      handler.next(error);
      return;
    }

    final refreshed = await _refreshTokens();
    if (!refreshed) {
      await _storage.clear();
      onSessionExpired?.call();
      handler.next(error);
      return;
    }

    final newToken = await _storage.readAccessToken();
    request.headers['Authorization'] = 'Bearer $newToken';
    try {
      final response = await _plain.fetch<dynamic>(request);
      handler.resolve(response);
    } on DioException catch (e) {
      handler.next(e);
    }
  }

  Future<bool> _refreshTokens() async {
    final refreshToken = await _storage.readRefreshToken();
    if (refreshToken == null) return false;
    try {
      _plain.options.baseUrl = AppConfig.apiBaseUrl;
      final response = await _plain.post<Map<String, dynamic>>(
        '/api/auth/refresh',
        data: {'refreshToken': refreshToken},
      );
      final data = response.data!;
      await _storage.save(
          data['accessToken'] as String, data['refreshToken'] as String);
      return true;
    } catch (_) {
      return false;
    }
  }
}

final apiClientProvider = Provider<ApiClient>(
  (ref) => ApiClient(ref.watch(tokenStorageProvider)),
);
