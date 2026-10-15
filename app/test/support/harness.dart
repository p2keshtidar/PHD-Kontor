import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:girokonto_app/core/api_client.dart';
import 'package:girokonto_app/core/key_value_store.dart';
import 'package:girokonto_app/core/token_storage.dart';
import 'package:girokonto_app/features/auth/app_user.dart';
import 'package:girokonto_app/l10n/l10n.dart';
import 'package:girokonto_app/features/auth/auth_controller.dart';
import 'package:go_router/go_router.dart';

import 'fake_backend.dart';

/// Angemeldeter Testnutzer ohne Token-Speicher.
class FakeAuthController extends AuthController {
  FakeAuthController([this.user]);
  final AppUser? user;

  @override
  Future<AppUser?> build() async => user ?? AppUser.fromJson(FakeBackend.user());
}

/// Token-Speicher nur im Arbeitsspeicher, schon mit Token (angemeldet).
class MemoryTokenStorage extends TokenStorage {
  String? access = 'a.b.c';
  String? refresh = 'r';
  @override
  Future<String?> readAccessToken() async => access;
  @override
  Future<String?> readRefreshToken() async => refresh;
  @override
  Future<void> save(String accessToken, String refreshToken) async {
    access = accessToken;
    refresh = refreshToken;
  }

  @override
  Future<void> clear() async => access = refresh = null;
}

/// Pumpt einen Bildschirm mit simuliertem Server, optional in anderer Größe/Schriftgröße.
/// Navigation zu anderen Seiten landet auf einer Platzhalterseite "ROUTE:/pfad".
Future<FakeBackend> pumpScreen(
  WidgetTester tester,
  Widget screen, {
  FakeBackend? backend,
  Size size = const Size(400, 800),
  double textScale = 1.0,
  AppUser? user,
  bool settle = true,
  List<Override> overrides = const [],
}) async {
  final fake = backend ?? FakeBackend();
  // Plattform-Kanäle (sicherer Speicher) antworten im Widget-Test nie -> alles im Arbeitsspeicher.
  KeyValueStore.memoryOnly = true;
  tester.view.devicePixelRatio = 2.0;
  tester.view.physicalSize = size * 2.0;
  tester.platformDispatcher.textScaleFactorTestValue = textScale;
  addTearDown(() {
    L10n.language.value = 'de';
    tester.view.resetPhysicalSize();
    tester.view.resetDevicePixelRatio();
    tester.platformDispatcher.clearTextScaleFactorTestValue();
  });
  final router = GoRouter(
    routes: [GoRoute(path: '/', builder: (_, __) => Scaffold(body: screen))],
    errorBuilder: (_, state) => Scaffold(body: Text('ROUTE:${state.uri}')),
  );
  await tester.pumpWidget(ProviderScope(
    overrides: [
      tokenStorageProvider.overrideWithValue(MemoryTokenStorage()),
      apiClientProvider.overrideWith((ref) {
        final api = ApiClient(ref.watch(tokenStorageProvider));
        api.dio.httpClientAdapter = fake;
        return api;
      }),
      authControllerProvider.overrideWith(() => FakeAuthController(user)),
      ...overrides,
    ],
    // wie in main.dart: bei Sprachwechsel neu aufbauen
    child: ValueListenableBuilder<String>(
      valueListenable: L10n.language,
      builder: (_, lang, __) => MaterialApp.router(key: ValueKey(lang), routerConfig: router),
    ),
  ));
  if (settle) {
    await tester.pumpAndSettle();
  } else {
    await tester.pump(const Duration(milliseconds: 500));
  }
  return fake;
}
