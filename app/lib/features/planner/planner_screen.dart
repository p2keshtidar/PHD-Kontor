import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/api_client.dart';
import '../../core/api_error.dart';
import '../../core/layout.dart';
import '../../core/money.dart';
import '../../l10n/l10n.dart';

int? _n(Object? v) => (v as num?)?.toInt();

/// Zentraler Planer: ein Vorhaben (Reise, Auto, Immobilie) ansparen, finanzieren oder beides – nebeneinander.
class PlannerScreen extends ConsumerStatefulWidget {
  const PlannerScreen({super.key});

  @override
  ConsumerState<PlannerScreen> createState() => _PlannerScreenState();
}

class _PlannerScreenState extends ConsumerState<PlannerScreen> {
  static const _months = [6, 12, 24, 36, 60, 120];

  String _goal = 'REISE';
  int _inMonths = 12;
  final _price = TextEditingController();
  final _equity = TextEditingController();
  Map<String, dynamic>? _result;
  String? _error;
  bool _busy = false;

  @override
  void dispose() {
    _price.dispose();
    _equity.dispose();
    super.dispose();
  }

  Future<void> _compare() async {
    final price = parseEuroToCents(_price.text);
    final equity = _equity.text.trim().isEmpty ? 0 : parseEuroToCents(_equity.text);
    if (price == null || price <= 0 || equity == null) {
      setState(() => _error = tr('Bitte einen gültigen Betrag eingeben.'));
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final res = await ref.read(apiClientProvider).dio.post<Map<String, dynamic>>('/api/planner/compare',
          data: {'goal': _goal, 'priceCents': price, 'equityCents': equity, 'months': _inMonths});
      if (mounted) setState(() => _result = res.data);
    } catch (e) {
      if (mounted) {
        setState(() {
          _result = null;
          _error = apiErrorMessage(e);
        });
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final r = _result;
    return Scaffold(
      appBar: AppBar(title: Text(tr('Vorhaben planen'))),
      body: ListView(padding: safeListPadding(context), children: [
        Text(tr('Ansparen, finanzieren oder beides? Vergleiche die Wege für dein Vorhaben.')),
        const SizedBox(height: 8),
        Wrap(spacing: 6, runSpacing: 6, children: [
          for (final g in [('REISE', tr('Reise'), Icons.flight), ('AUTO', tr('Auto'), Icons.directions_car),
            ('IMMOBILIE', tr('Immobilie'), Icons.home)])
            ChoiceChip(
              key: Key('planner_goal_${g.$1}'),
              avatar: Icon(g.$3, size: 18),
              label: Text(g.$2),
              selected: _goal == g.$1,
              onSelected: (_) => setState(() {
                _goal = g.$1;
                _result = null;
              }),
            ),
        ]),
        TextField(
          key: const Key('planner_price'),
          controller: _price,
          keyboardType: const TextInputType.numberWithOptions(decimal: true),
          decoration: InputDecoration(labelText: tr('Preis in €')),
        ),
        TextField(
          key: const Key('planner_equity'),
          controller: _equity,
          keyboardType: const TextInputType.numberWithOptions(decimal: true),
          decoration: InputDecoration(labelText: tr('Schon vorhanden (Eigenkapital) in €, optional')),
        ),
        DropdownButtonFormField<int>(
          key: const Key('planner_months'),
          value: _inMonths,
          isExpanded: true,
          decoration: InputDecoration(labelText: tr('Wann?')),
          items: [for (final m in _months) DropdownMenuItem(value: m, child: Text(tr('in {0} Monaten', [m])))],
          onChanged: (v) => setState(() => _inMonths = v ?? _inMonths),
        ),
        const SizedBox(height: 12),
        FilledButton(
          key: const Key('planner_compare'),
          onPressed: _busy ? null : _compare,
          child: Text(tr('Vergleichen')),
        ),
        if (_error != null)
          Padding(
            padding: const EdgeInsets.only(top: 8),
            child: Text(_error!, key: const Key('planner_error'), style: TextStyle(color: theme.colorScheme.error)),
          ),
        if (r != null) ...[
          const SizedBox(height: 12),
          Card(
            key: const Key('planner_recommendation'),
            color: theme.colorScheme.secondaryContainer,
            child: Padding(
              padding: const EdgeInsets.all(12),
              child: Text(r['recommendation'] as String, style: TextStyle(color: theme.colorScheme.onSecondaryContainer)),
            ),
          ),
          for (final o in (r['options'] as List<dynamic>).cast<Map<String, dynamic>>()) _OptionCard(o),
          const SizedBox(height: 8),
          Text(r['disclaimer'] as String, style: theme.textTheme.bodySmall),
        ],
        const SizedBox(height: 40),
      ]),
    );
  }
}

class _OptionCard extends StatelessWidget {
  const _OptionCard(this.o);

  final Map<String, dynamic> o;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final kind = o['kind'] as String;
    final possible = o['possible'] as bool;
    final feasibility = o['feasibility'] as String;
    final color = feasibility == 'GREEN'
        ? const Color(0xFF2E7D32)
        : feasibility == 'RED'
            ? theme.colorScheme.error
            : theme.colorScheme.outline;
    final monthly = _n(o['monthlyCents']);
    final saving = _n(o['savingMonthlyCents']);
    final extra = _n(o['extraCostCents']);
    return Card(
      key: Key('planner_option_$kind'),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [
            Icon(possible ? Icons.check_circle : Icons.cancel_outlined, color: color, size: 20),
            const SizedBox(width: 6),
            Expanded(child: Text(o['title'] as String, style: theme.textTheme.titleSmall)),
            Text(possible ? tr('passt') : tr('passt nicht'), style: TextStyle(color: color, fontWeight: FontWeight.w600)),
          ]),
          const SizedBox(height: 4),
          Text(o['text'] as String),
          if (kind == 'MIX' && saving != null) Text(tr('Zuerst {0} pro Monat sparen', [formatCents(saving)])),
          if (monthly != null && kind != 'SAVE') Text(tr('Kreditrate {0} pro Monat', [formatCents(monthly)])),
          if (extra != null) Text(tr('Zusätzliche Kosten (Zinsen): {0}', [formatCents(extra)])),
          Align(
            alignment: Alignment.centerRight,
            child: TextButton(
              key: Key('planner_go_$kind'),
              onPressed: () => context.push(kind == 'SAVE' ? '/plan/neu?type=GOAL' : '/kredit/neu'),
              child: Text(kind == 'SAVE' ? tr('Sparplan anlegen') : tr('Kredit vergleichen')),
            ),
          ),
        ]),
      ),
    );
  }
}
