import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_client.dart';
import '../../core/token_storage.dart';
import '../../core/validators.dart';
import 'app_user.dart';

/// Alle Aufrufe rund um Anmeldung und Profil (/F010/ bis /F030/).
class AuthRepository {
  AuthRepository(this._api, this._storage);

  final ApiClient _api;
  final TokenStorage _storage;

  Future<AppUser> login(String email, String password) async {
    final res = await _api.dio.post<Map<String, dynamic>>(
      '/api/auth/login',
      data: {'email': email.trim(), 'password': password},
      options: ApiClient.noAuth(),
    );
    return _storeTokens(res.data!);
  }

  Future<AppUser> register({
    required String firstName,
    required String lastName,
    required String email,
    required String phone,
    required DateTime birthDate,
    required String password,
  }) async {
    final res = await _api.dio.post<Map<String, dynamic>>(
      '/api/auth/register',
      data: {
        'firstName': firstName.trim(),
        'lastName': lastName.trim(),
        'email': email.trim(),
        'phone': phone.trim(),
        'birthDate': toIsoDate(birthDate),
        'password': password,
      },
      options: ApiClient.noAuth(),
    );
    return _storeTokens(res.data!);
  }

  /// Beim App-Start: gibt es noch eine gültige Sitzung?
  Future<AppUser?> restoreSession() async {
    if (await _storage.readRefreshToken() == null) return null;
    try {
      return await fetchMe();
    } on DioException catch (e) {
      if (e.response?.statusCode == 401) await _storage.clear();
      return null;
    }
  }

  Future<AppUser> fetchMe() async {
    final res = await _api.dio.get<Map<String, dynamic>>('/api/me');
    return AppUser.fromJson(res.data!);
  }

  Future<AppUser> updateProfile({
    required String firstName,
    required String lastName,
    required String phone,
  }) async {
    final res = await _api.dio.put<Map<String, dynamic>>('/api/me', data: {
      'firstName': firstName.trim(),
      'lastName': lastName.trim(),
      'phone': phone.trim(),
    });
    return AppUser.fromJson(res.data!);
  }

  Future<void> changePassword(String oldPassword, String newPassword) async {
    await _api.dio.put<void>('/api/me/password', data: {
      'oldPassword': oldPassword,
      'newPassword': newPassword,
    });
  }

  Future<void> logout() async {
    try {
      await _api.dio.post<void>('/api/auth/logout');
    } catch (_) {
      // Auch ohne Server lokal abmelden.
    } finally {
      await _storage.clear();
    }
  }

  Future<AppUser> _storeTokens(Map<String, dynamic> data) async {
    await _storage.save(
        data['accessToken'] as String, data['refreshToken'] as String);
    return AppUser.fromJson(data['user'] as Map<String, dynamic>);
  }
}

final authRepositoryProvider = Provider<AuthRepository>((ref) => AuthRepository(
      ref.watch(apiClientProvider),
      ref.watch(tokenStorageProvider),
    ));
