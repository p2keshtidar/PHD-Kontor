import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_client.dart';
import '../notifications/background_check.dart';
import 'app_user.dart';
import 'auth_repository.dart';
import '../../l10n/language_controller.dart';

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
    final user = await _repo.restoreSession();
    if (user != null) _applyLanguage(user);
    return user;
  }

  /// Sprache des Kontos übernehmen (oder die aktuelle beim Konto speichern).
  void _applyLanguage(AppUser user) {
    LanguageController.applyUser(user, ref.read(apiClientProvider));
  }

  Future<void> login(String email, String password, {String? code}) async {
    final user = await _repo.login(email, password, code: code);
    _applyLanguage(user);
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
    _applyLanguage(user);
    state = AsyncData(user);
  }

  void updateUser(AppUser user) => state = AsyncData(user);

  /// Nutzerdaten neu laden (z. B. nach dem Einschalten der Zwei-Faktor-Anmeldung).
  Future<void> reload() async {
    state = AsyncData(await _repo.fetchMe());
  }

  Future<void> logout() async {
    await BackgroundCheck.stop(); // /K070/ keine Hintergrundabfragen mehr
    await _repo.logout();
    state = const AsyncData(null);
  }
}

final authControllerProvider =
    AsyncNotifierProvider<AuthController, AppUser?>(AuthController.new);
