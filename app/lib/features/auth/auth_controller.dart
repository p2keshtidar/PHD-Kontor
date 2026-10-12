import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_client.dart';
import 'app_user.dart';
import 'auth_repository.dart';

/// Hält den aktuell angemeldeten Nutzer.
///
/// * AsyncLoading  -> App startet, Sitzung wird geprüft (Splash)
/// * AsyncData(null) -> nicht angemeldet (Login)
/// * AsyncData(user) -> angemeldet
///
/// Fehler beim Login/Registrieren werden an den Bildschirm weitergereicht, der sie anzeigt.
class AuthController extends AsyncNotifier<AppUser?> {
  AuthRepository get _repo => ref.read(authRepositoryProvider);

  @override
  Future<AppUser?> build() async {
    ref.read(apiClientProvider).onSessionExpired = () {
      state = const AsyncData(null);
    };
    return _repo.restoreSession();
  }

  Future<void> login(String email, String password) async {
    final user = await _repo.login(email, password);
    state = AsyncData(user);
  }

  Future<void> register({
    required String firstName,
    required String lastName,
    required String email,
    required String phone,
    required DateTime birthDate,
    required String password,
  }) async {
    final user = await _repo.register(
      firstName: firstName,
      lastName: lastName,
      email: email,
      phone: phone,
      birthDate: birthDate,
      password: password,
    );
    state = AsyncData(user);
  }

  void updateUser(AppUser user) => state = AsyncData(user);

  Future<void> logout() async {
    await _repo.logout();
    state = const AsyncData(null);
  }
}

final authControllerProvider =
    AsyncNotifierProvider<AuthController, AppUser?>(AuthController.new);
