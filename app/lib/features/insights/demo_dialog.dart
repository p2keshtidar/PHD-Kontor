import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_error.dart';
import 'insights_models.dart';
import 'insights_repository.dart';

/// Beispielmonate erzeugen (/F590/). Gibt true zurück, wenn erzeugt.
Future<bool> showDemoDialog(BuildContext context) async {
  final ok = await showDialog<bool>(context: context, builder: (_) => const _DemoDialog());
  return ok == true;
}

class _DemoDialog extends ConsumerStatefulWidget {
  const _DemoDialog();

  @override
  ConsumerState<_DemoDialog> createState() => _DemoDialogState();
}

class _DemoDialogState extends ConsumerState<_DemoDialog> {
  String _profile = 'EINSTEIGER';
  bool _busy = false;
  String? _error;

  Future<void> _create() async {
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await ref.read(insightsRepositoryProvider).createSampleMonths(_profile);
      if (mounted) Navigator.of(context).pop(true);
    } catch (e) {
      if (mounted) {
        setState(() {
          _busy = false;
          _error = apiErrorMessage(e);
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Beispielmonate erzeugen'),
      content: SingleChildScrollView(child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
        const Text('Die App bucht 6 Monate mit typischen Einnahmen und Ausgaben, damit der Finanzüberblick '
            'und die Pläne etwas zum Rechnen haben. Das geht nur einmal pro Konto.'),
        const SizedBox(height: 12),
        for (final e in demoProfiles.entries)
          RadioListTile<String>(
            contentPadding: EdgeInsets.zero,
            value: e.key,
            groupValue: _profile,
            title: Text(e.value),
            onChanged: _busy ? null : (v) => setState(() => _profile = v!),
          ),
        if (_error != null) Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
      ])),
      actions: [
        TextButton(onPressed: _busy ? null : () => Navigator.of(context).pop(false), child: const Text('Abbrechen')),
        FilledButton(
          onPressed: _busy ? null : _create,
          child: _busy
              ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
              : const Text('Erzeugen'),
        ),
      ],
    );
  }
}
