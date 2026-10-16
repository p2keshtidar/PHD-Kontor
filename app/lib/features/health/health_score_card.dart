import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_client.dart';
import '../../core/api_error.dart';
import '../../l10n/l10n.dart';
import '../../core/theme.dart';

final healthScoreProvider = FutureProvider.autoDispose<Map<String, dynamic>>((ref) async {
  final res = await ref.watch(apiClientProvider).dio.get<Map<String, dynamic>>('/api/health-score');
  return res.data!;
});

/// Interner Finanz-Check (0–100 Punkte) mit Bestandteilen und Tipps – keine SCHUFA-Auskunft.
class HealthScoreCard extends ConsumerWidget {
  const HealthScoreCard({super.key});

  Future<void> _consent(BuildContext context, WidgetRef ref, bool on) async {
    final dio = ref.read(apiClientProvider).dio;
    try {
      on ? await dio.post<void>('/api/health-score/consent') : await dio.delete<void>('/api/health-score/consent');
      ref.invalidate(healthScoreProvider);
    } catch (e) {
      if (context.mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(apiErrorMessage(e))));
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final data = ref.watch(healthScoreProvider);
    final theme = Theme.of(context);
    return Card(
      key: const Key('health_score'),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: data.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (e, _) => Text(apiErrorMessage(e)),
          data: (s) {
            if (s['consented'] == false) {
              return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(tr('Finanz-Check'), style: theme.textTheme.titleMedium),
                const SizedBox(height: 4),
                Text(s['consentText'] as String? ?? '', key: const Key('health_consent_text')),
                const SizedBox(height: 4),
                Text(s['disclaimer'] as String, style: theme.textTheme.bodySmall),
                Align(
                  alignment: Alignment.centerRight,
                  child: FilledButton.tonal(
                    key: const Key('health_consent'),
                    onPressed: () => _consent(context, ref, true),
                    child: Text(tr('Finanz-Check aktivieren')),
                  ),
                ),
              ]);
            }
            final score = (s['score'] as num).toInt();
            final level = s['level'] as String;
            final color = switch (level) {
              'GUT' => positiveColor(context),
              'MITTEL' => warningColor(context),
              'KRITISCH' => theme.colorScheme.error,
              _ => theme.colorScheme.outline,
            };
            return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Row(children: [
                Semantics(
                  container: true,
                  label: tr('Finanz-Check: {0} von 100 Punkten', [score]),
                  child: ExcludeSemantics(
                    child: SizedBox(
                      width: 56,
                      height: 56,
                      child: Stack(alignment: Alignment.center, children: [
                        CircularProgressIndicator(value: score / 100, strokeWidth: 6, color: color,
                            backgroundColor: theme.colorScheme.surfaceContainerHighest),
                        Text('$score', key: const Key('health_score_value'),
                            style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 18)),
                      ]),
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text(tr('Finanz-Check'), style: theme.textTheme.titleMedium),
                    Text(s['levelLabel'] as String, style: TextStyle(color: color, fontWeight: FontWeight.w600)),
                  ]),
                ),
              ]),
              const SizedBox(height: 8),
              for (final c in (s['components'] as List<dynamic>).cast<Map<String, dynamic>>())
                Padding(
                  key: Key('health_${c['key']}'),
                  padding: const EdgeInsets.symmetric(vertical: 3),
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Row(children: [
                      Expanded(child: Text('${c['label']} · ${c['value']}')),
                      Text('${c['points']}/${c['max']}', style: const TextStyle(fontWeight: FontWeight.w600)),
                    ]),
                    Text(c['text'] as String, style: theme.textTheme.bodySmall),
                  ]),
                ),
              for (final t in (s['tips'] as List<dynamic>).cast<String>())
                Padding(
                  padding: const EdgeInsets.only(top: 2),
                  child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    const Icon(Icons.lightbulb_outline, size: 16),
                    const SizedBox(width: 4),
                    Expanded(child: Text(t)),
                  ]),
                ),
              const SizedBox(height: 6),
              Text(s['disclaimer'] as String, key: const Key('health_disclaimer'), style: theme.textTheme.bodySmall),
              Align(
                alignment: Alignment.centerRight,
                child: TextButton(
                  key: const Key('health_revoke'),
                  onPressed: () => _consent(context, ref, false),
                  child: Text(tr('Finanz-Check ausschalten')),
                ),
              ),
            ]);
          },
        ),
      ),
    );
  }
}
