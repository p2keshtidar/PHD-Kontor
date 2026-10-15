import 'package:flutter/material.dart';

import '../l10n/l10n.dart';
import 'api_error.dart';

/// Einheitlicher Fehlerzustand: Symbol, verständliche Meldung (für Bildschirmleser sofort vorgelesen) und
/// „Erneut versuchen“.
class ErrorView extends StatelessWidget {
  const ErrorView({super.key, required this.error, this.onRetry});

  final Object error;
  final VoidCallback? onRetry;

  @override
  Widget build(BuildContext context) => Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            const ExcludeSemantics(child: Icon(Icons.cloud_off, size: 48)),
            const SizedBox(height: 12),
            Semantics(
              container: true,
              liveRegion: true,
              child: Text(apiErrorMessage(error), key: const Key('error_message'), textAlign: TextAlign.center),
            ),
            if (onRetry != null) ...[
              const SizedBox(height: 12),
              OutlinedButton(
                key: const Key('error_retry'),
                onPressed: onRetry,
                child: Text(tr('Erneut versuchen')),
              ),
            ],
          ]),
        ),
      );
}

/// Einheitlicher Ladezustand mit Text für Bildschirmleser.
class LoadingView extends StatelessWidget {
  const LoadingView({super.key});

  @override
  Widget build(BuildContext context) => Center(
        child: Semantics(label: tr('Wird geladen …'), child: const CircularProgressIndicator()),
      );
}

/// Einheitlicher Leerzustand: Symbol und Erklärung, was hier erscheinen wird und wie man startet.
class EmptyView extends StatelessWidget {
  const EmptyView({super.key, required this.text, this.icon = Icons.inbox_outlined});

  final String text;
  final IconData icon;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.all(24),
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          ExcludeSemantics(child: Icon(icon, size: 40, color: Theme.of(context).colorScheme.outline)),
          const SizedBox(height: 8),
          Text(text, textAlign: TextAlign.center),
        ]),
      );
}
