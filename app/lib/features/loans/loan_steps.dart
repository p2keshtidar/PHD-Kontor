import 'package:flutter/material.dart';

import '../../l10n/l10n.dart';

/// Die vier getrennten Schritte eines Kredits: Vergleich → Anfrage → Vertrag → Auszahlung.
/// [current] = 1 bis 4 (der Schritt, der gerade dran ist); alle davor sind erledigt.
class LoanSteps extends StatelessWidget {
  const LoanSteps({super.key, required this.current});

  final int current;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final steps = [tr('Vergleich'), tr('Anfrage'), tr('Vertrag'), tr('Auszahlung')];
    return Wrap(
      key: const Key('loan_steps'),
      spacing: 4,
      runSpacing: 4,
      crossAxisAlignment: WrapCrossAlignment.center,
      children: [
        for (var i = 0; i < steps.length; i++) ...[
          Chip(
            visualDensity: VisualDensity.compact,
            avatar: Icon(
              i + 1 < current ? Icons.check_circle : (i + 1 == current ? Icons.radio_button_checked : Icons.circle_outlined),
              size: 16,
              color: i + 1 <= current ? theme.colorScheme.primary : theme.colorScheme.outline,
            ),
            label: Text('${i + 1}. ${steps[i]}'),
          ),
        ],
      ],
    );
  }
}
