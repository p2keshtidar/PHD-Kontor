import 'dart:ui' show PlatformDispatcher;

import '../core/api_client.dart';
import '../core/key_value_store.dart';
import '../features/auth/app_user.dart';
import 'l10n.dart';

/// Wählt, speichert und synchronisiert die Sprache.
///
/// * Erster Start: Gerätesprache, falls Deutsch oder Englisch, sonst Deutsch.
/// * Wechsel: sofort (ohne Neustart), lokal gespeichert und beim Nutzerkonto hinterlegt.
/// * Nach der Anmeldung gilt die beim Konto gespeicherte Sprache (auch auf einem anderen Gerät).
class LanguageController {
  LanguageController._();

  static const _key = 'language';

  static Future<void> init() async {
    final stored = await KeyValueStore.instance.read(_key);
    L10n.language.value =
        stored != null && L10n.supported.contains(stored) ? stored : L10n.fromDevice(PlatformDispatcher.instance.locale.languageCode);
  }

  /// Sprache wechseln; mit [api] wird sie beim angemeldeten Nutzer gespeichert.
  static Future<void> set(String lang, {ApiClient? api}) async {
    if (!L10n.supported.contains(lang)) return;
    L10n.language.value = lang;
    await KeyValueStore.instance.write(_key, lang);
    if (api != null) {
      try {
        await api.dio.put<void>('/api/me/language', data: {'language': lang});
      } catch (_) {
        // offline: lokal bleibt die Wahl erhalten, beim nächsten Wechsel wird erneut gespeichert
      }
    }
  }

  /// Nach Anmeldung: gespeicherte Sprache des Kontos übernehmen bzw. die aktuelle dort speichern.
  static Future<void> applyUser(AppUser user, ApiClient api) async {
    final own = user.language;
    if (own != null && L10n.supported.contains(own)) {
      if (own != L10n.lang) await set(own);
    } else {
      await set(L10n.lang, api: api);
    }
  }
}
