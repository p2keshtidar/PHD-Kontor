import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'l10n/language_controller.dart';
import 'l10n/l10n.dart';

import 'core/config.dart';
import 'core/messenger.dart';
import 'core/router.dart';
import 'core/theme.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await AppConfig.load();
  await LanguageController.init();
  runApp(const ProviderScope(child: GirokontoApp()));
}

class GirokontoApp extends ConsumerWidget {
  const GirokontoApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    // Bei einem Sprachwechsel wird die ganze Oberfläche neu aufgebaut (ohne Neustart, Seite bleibt offen).
    return ValueListenableBuilder<String>(
      valueListenable: L10n.language,
      builder: (context, lang, _) => MaterialApp.router(
      key: ValueKey('app_$lang'),
      title: tr('Girokonto'),
      locale: Locale(lang),
      supportedLocales: const [Locale('de'), Locale('en')],
      localizationsDelegates: GlobalMaterialLocalizations.delegates,
      debugShowCheckedModeBanner: false,
      scaffoldMessengerKey: rootMessengerKey,
      theme: buildTheme(Brightness.light),
      darkTheme: buildTheme(Brightness.dark),
      themeMode: ThemeMode.system,
      routerConfig: ref.watch(routerProvider),
      ),
    );
  }
}
