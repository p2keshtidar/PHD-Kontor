import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:girokonto_app/core/api_client.dart';
import 'package:girokonto_app/core/token_storage.dart';
import 'package:girokonto_app/features/auth/app_user.dart';
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
}) async {
  final fake = backend ?? FakeBackend();
  tester.view.devicePixelRatio = 2.0;
  tester.view.physicalSize = size * 2.0;
  tester.platformDispatcher.textScaleFactorTestValue = textScale;
  addTearDown(() {
    tester.view.resetPhysicalSize();
    tester.view.resetDevicePixelRatio();
    tester.platformDispatcher.clearTextScaleFactorTestValue();
  });
  final router = GoRouter(
    routes: [GoRoute(path: '/', builder: (_, __) => screen)],
    errorBuilder: (_, state) => Scaffold(body: Text('ROUTE:${state.uri}')),
  );
  await tester.pumpWidget(ProviderScope(
    overrides: [
      apiClientProvider.overrideWith((ref) {
        final api = ApiClient(ref.watch(tokenStorageProvider));
        api.dio.httpClientAdapter = fake;
        return api;
      }),
      authControllerProvider.overrideWith(() => FakeAuthController(user)),
    ],
    child: MaterialApp.router(routerConfig: router),
  ));
  if (settle) {
    await tester.pumpAndSettle();
  } else {
    await tester.pump(const Duration(milliseconds: 500));
  }
  return fake;
}
