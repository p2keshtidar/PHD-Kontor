import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/api_client.dart';
import '../features/auth/auth_controller.dart';
import 'l10n.dart';
import 'language_controller.dart';

/// Sprachumschalter (Deutsch ↔ Englisch), z. B. oben rechts in der App-Leiste.
class LanguageButton extends ConsumerWidget {
  const LanguageButton({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final next = L10n.isEnglish ? 'de' : 'en';
    return TextButton.icon(
      key: const Key('language_switch'),
      onPressed: () {
        final loggedIn = ref.read(authControllerProvider).valueOrNull != null;
        LanguageController.set(next, api: loggedIn ? ref.read(apiClientProvider) : null);
      },
      icon: const Icon(Icons.translate, size: 18),
      label: Text(L10n.lang.toUpperCase()),
      style: TextButton.styleFrom(visualDensity: VisualDensity.compact),
    ).withTooltip(tr('Sprache: {0}', [L10n.names[next]]));
  }
}

extension on Widget {
  Widget withTooltip(String text) => Tooltip(message: text, child: this);
}
