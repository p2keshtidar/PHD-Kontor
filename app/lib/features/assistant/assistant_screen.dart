import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_error.dart';
import '../../core/state_views.dart';
import '../../core/layout.dart';
import '../../core/money.dart';
import '../../l10n/l10n.dart';
import '../insights/insights_models.dart';
import 'assistant_repository.dart';

/// Persönlicher Finanzassistent: Fragen zu den eigenen Daten, Monatsbericht, „Was wäre, wenn …“.
/// Regelbasiert im Backend – es werden nur Zahlen aus der App verwendet.
class AssistantScreen extends StatelessWidget {
  const AssistantScreen({super.key});

  @override
  Widget build(BuildContext context) => DefaultTabController(
        length: 3,
        child: Scaffold(
          appBar: AppBar(
            title: Text(tr('Finanzassistent')),
            bottom: TabBar(isScrollable: true, tabs: [
              Tab(key: const Key('assistant_tab_ask'), text: tr('Fragen')),
              Tab(key: const Key('assistant_tab_report'), text: tr('Monatsbericht')),
              Tab(key: const Key('assistant_tab_scenario'), text: tr('Was wäre, wenn …')),
            ]),
          ),
          body: const TabBarView(children: [_AskTab(), _ReportTab(), _ScenarioTab()]),
        ),
      );
}

Widget _small(BuildContext context, String text, {Key? key}) =>
    Text(text, key: key, style: Theme.of(context).textTheme.bodySmall);

// ---------------------------------------------------------------- Fragen

class _AskTab extends ConsumerStatefulWidget {
  const _AskTab();

  @override
  ConsumerState<_AskTab> createState() => _AskTabState();
}

class _AskTabState extends ConsumerState<_AskTab> {
  final _controller = TextEditingController();
  final List<AssistantAnswer> _answers = [];
  bool _busy = false;
  String? _error;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _ask(String q) async {
    final question = q.trim();
    if (question.isEmpty || _busy) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final a = await ref.read(assistantRepositoryProvider).ask(question);
      if (!mounted) return;
      setState(() {
        _answers.insert(0, a);
        _controller.clear();
      });
    } catch (e) {
      if (mounted) setState(() => _error = apiErrorMessage(e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final questions = ref.watch(assistantQuestionsProvider);
    final theme = Theme.of(context);
    return ListView(padding: safeListPadding(context), children: [
      Text(tr('Frag nach Ausgaben, Sparpotenzialen, dem Monatsende oder Budgets – ich antworte nur mit Zahlen aus der App.')),
      const SizedBox(height: 8),
      questions.maybeWhen(
        data: (list) => Wrap(spacing: 6, runSpacing: 6, children: [
          for (var i = 0; i < list.length; i++)
            OutlinedButton(
              key: Key('assistant_suggestion_$i'),
              onPressed: _busy ? null : () => _ask(list[i]),
              child: Text(list[i]),
            ),
        ]),
        orElse: () => const SizedBox.shrink(),
      ),
      const SizedBox(height: 12),
      Row(children: [
        Expanded(
          child: TextField(
            key: const Key('assistant_input'),
            controller: _controller,
            maxLength: 300,
            textInputAction: TextInputAction.send,
            onSubmitted: _ask,
            decoration: InputDecoration(labelText: tr('Deine Frage'), counterText: ''),
          ),
        ),
        IconButton(
          key: const Key('assistant_send'),
          tooltip: tr('Fragen'),
          icon: _busy
              ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2))
              : const Icon(Icons.send),
          onPressed: _busy ? null : () => _ask(_controller.text),
        ),
      ]),
      if (_error != null) Text(_error!, style: TextStyle(color: theme.colorScheme.error)),
      const SizedBox(height: 8),
      for (var i = 0; i < _answers.length; i++) _AnswerCard(_answers[i], key: Key('assistant_answer_$i')),
    ]);
  }
}

class _AnswerCard extends StatelessWidget {
  const _AnswerCard(this.answer, {super.key});

  final AssistantAnswer answer;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(answer.question, style: theme.textTheme.labelLarge),
          const SizedBox(height: 6),
          Text(answer.text),
          for (final f in answer.facts)
            Padding(
              padding: const EdgeInsets.only(top: 4),
              child: Row(children: [
                Expanded(child: Text(f.label)),
                Text(f.amountCents != null ? formatCents(f.amountCents!) : (f.value ?? ''),
                    style: const TextStyle(fontWeight: FontWeight.w600)),
              ]),
            ),
          for (final n in answer.notes) Padding(padding: const EdgeInsets.only(top: 4), child: Text('• $n')),
          const SizedBox(height: 6),
          _small(context, '${answer.note} ${answer.disclaimer}'),
        ]),
      ),
    );
  }
}

// ---------------------------------------------------------------- Monatsbericht

class _ReportTab extends ConsumerStatefulWidget {
  const _ReportTab();

  @override
  ConsumerState<_ReportTab> createState() => _ReportTabState();
}

class _ReportTabState extends ConsumerState<_ReportTab> {
  String? _month; // null = letzter voller Monat (vom Server)
  String? _base;

  static String _shift(String month, int by) {
    final total = int.parse(month.substring(0, 4)) * 12 + int.parse(month.substring(5, 7)) - 1 + by;
    return '${total ~/ 12}-${(total % 12 + 1).toString().padLeft(2, '0')}';
  }

  @override
  Widget build(BuildContext context) {
    final data = ref.watch(monthReportProvider(_month));
    final theme = Theme.of(context);
    return data.when(
      loading: () => const LoadingView(),
      error: (e, _) => ErrorView(error: e, onRetry: () => ref.invalidate(monthReportProvider(_month))),
      data: (r) {
        _base ??= r.month;
        final canNext = r.month.compareTo(_shift(_base!, 1)) < 0;
        final canPrev = r.month.compareTo(_shift(_base!, -11)) > 0;
        return ListView(padding: safeListPadding(context), children: [
          Row(children: [
            IconButton(
              key: const Key('report_prev'),
              tooltip: tr('Vorheriger Monat'),
              icon: const Icon(Icons.chevron_left),
              onPressed: canPrev ? () => setState(() => _month = _shift(r.month, -1)) : null,
            ),
            Expanded(
              child: Text(r.complete ? r.monthLabel : tr('{0} (bisher)', [r.monthLabel]),
                  key: const Key('report_month'), textAlign: TextAlign.center, style: theme.textTheme.titleMedium),
            ),
            IconButton(
              key: const Key('report_next'),
              tooltip: tr('Nächster Monat'),
              icon: const Icon(Icons.chevron_right),
              onPressed: canNext ? () => setState(() => _month = _shift(r.month, 1)) : null,
            ),
          ]),
          if (!r.hasData)
            Padding(
              padding: const EdgeInsets.all(24),
              child: Text(tr('Für diesen Monat liegen keine Umsätze vor.'),
                  key: const Key('report_empty'), textAlign: TextAlign.center),
            )
          else ...[
            Card(
              child: Padding(
                padding: const EdgeInsets.all(12),
                child: Column(children: [
                  _row(tr('Einnahmen'), formatCents(r.incomeCents)),
                  _row(tr('Ausgaben'), formatCents(r.expenseCents)),
                  _row(tr('Übrig'), formatCents(r.freeCents), bold: true),
                  if (r.savingsRatePercent != null) _row(tr('Sparquote'), '${r.savingsRatePercent} %'),
                ]),
              ),
            ),
            const SizedBox(height: 8),
            for (final n in r.notes) Padding(padding: const EdgeInsets.only(bottom: 4), child: Text('• $n')),
            const SizedBox(height: 8),
            Text(tr('Ausgaben nach Kategorie'), style: theme.textTheme.titleSmall),
            for (final c in r.categories)
              ListTile(
                dense: true,
                contentPadding: EdgeInsets.zero,
                title: Text(c.label),
                subtitle: c.changePercent == null
                    ? null
                    : Text(tr('Vormonat {0} ({1} %)',
                        [formatCents(c.previousCents), '${c.changePercent! > 0 ? '+' : ''}${c.changePercent}'])),
                trailing: Text(formatCents(c.cents)),
              ),
          ],
          const SizedBox(height: 8),
          _small(context, r.disclaimer),
        ]);
      },
    );
  }

  Widget _row(String label, String value, {bool bold = false}) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 2),
        child: Row(children: [
          Expanded(child: Text(label)),
          Text(value, style: TextStyle(fontWeight: bold ? FontWeight.bold : FontWeight.normal)),
        ]),
      );
}

// ---------------------------------------------------------------- Szenarien

class _ScenarioTab extends ConsumerStatefulWidget {
  const _ScenarioTab();

  @override
  ConsumerState<_ScenarioTab> createState() => _ScenarioTabState();
}

class _ScenarioTabState extends ConsumerState<_ScenarioTab> {
  static const _variable = ['LEBENSMITTEL', 'FREIZEIT', 'SHOPPING', 'MOBILITAET', 'VERTRAEGE', 'SONSTIGES', 'WOHNEN'];

  String _type = 'CUT_CATEGORY';
  String _category = 'FREIZEIT';
  final _amount = TextEditingController();
  final _percent = TextEditingController();
  final _target = TextEditingController();
  ScenarioResult? _result;
  String? _error;
  bool _busy = false;

  @override
  void dispose() {
    _amount.dispose();
    _percent.dispose();
    _target.dispose();
    super.dispose();
  }

  Future<void> _calculate() async {
    Map<String, Object?> body;
    if (_type == 'INCOME_CHANGE') {
      final p = int.tryParse(_percent.text.trim().replaceAll('+', '').replaceAll('%', '').trim());
      if (p == null) {
        setState(() => _error = tr('Bitte eine Prozentzahl eingeben, z. B. -10.'));
        return;
      }
      body = {'type': _type, 'percent': p};
    } else {
      final cents = parseEuroToCents(_amount.text);
      if (cents == null || cents <= 0) {
        setState(() => _error = tr('Bitte einen gültigen Betrag eingeben.'));
        return;
      }
      body = {'type': _type, 'amountCents': cents};
      if (_type == 'CUT_CATEGORY') body['category'] = _category;
      if (_type == 'EXTRA_SAVING') {
        final target = parseEuroToCents(_target.text);
        if (target == null || target <= 0) {
          setState(() => _error = tr('Bitte ein Sparziel eingeben.'));
          return;
        }
        body['targetCents'] = target;
      }
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final r = await ref.read(assistantRepositoryProvider).scenario(body);
      if (mounted) setState(() => _result = r);
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
    final labels = selectableCategories;
    final r = _result;
    return ListView(padding: safeListPadding(context), children: [
      Text(tr('Rechne aus, was eine Änderung für deinen freien Betrag pro Monat bedeutet.')),
      const SizedBox(height: 8),
      Wrap(spacing: 6, runSpacing: 6, children: [
        for (final t in [
          ('CUT_CATEGORY', tr('Weniger ausgeben')),
          ('INCOME_CHANGE', tr('Einnahmen ändern')),
          ('EXTRA_SAVING', tr('Für ein Ziel sparen')),
        ])
          ChoiceChip(
            key: Key('scenario_type_${t.$1}'),
            label: Text(t.$2),
            selected: _type == t.$1,
            onSelected: (_) => setState(() {
              _type = t.$1;
              _result = null;
              _error = null;
            }),
          ),
      ]),
      const SizedBox(height: 8),
      if (_type == 'CUT_CATEGORY')
        DropdownButtonFormField<String>(
          key: const Key('scenario_category'),
          value: _category,
          isExpanded: true,
          decoration: InputDecoration(labelText: tr('Kategorie')),
          items: [for (final c in _variable) DropdownMenuItem(value: c, child: Text(labels[c] ?? c))],
          onChanged: (v) => setState(() => _category = v ?? _category),
        ),
      if (_type == 'INCOME_CHANGE')
        TextField(
          key: const Key('scenario_percent'),
          controller: _percent,
          keyboardType: const TextInputType.numberWithOptions(signed: true),
          decoration: InputDecoration(labelText: tr('Änderung in % (z. B. -10 oder 5)')),
        )
      else
        TextField(
          key: const Key('scenario_amount'),
          controller: _amount,
          keyboardType: const TextInputType.numberWithOptions(decimal: true),
          decoration: InputDecoration(labelText: tr('Betrag pro Monat in €')),
        ),
      if (_type == 'EXTRA_SAVING')
        TextField(
          key: const Key('scenario_target'),
          controller: _target,
          keyboardType: const TextInputType.numberWithOptions(decimal: true),
          decoration: InputDecoration(labelText: tr('Sparziel in €')),
        ),
      const SizedBox(height: 12),
      FilledButton(
        key: const Key('scenario_calculate'),
        onPressed: _busy ? null : _calculate,
        child: Text(tr('Berechnen')),
      ),
      if (_error != null)
        Padding(
          padding: const EdgeInsets.only(top: 8),
          child: Text(_error!, key: const Key('scenario_error'), style: TextStyle(color: theme.colorScheme.error)),
        ),
      if (r != null)
        Card(
          key: const Key('scenario_result'),
          margin: const EdgeInsets.only(top: 12),
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(r.text, style: theme.textTheme.titleSmall),
              const SizedBox(height: 6),
              Text(tr('Frei pro Monat: {0} → {1}', [formatCents(r.currentFreeCents), formatCents(r.newFreeCents)])),
              if (r.currentRate != null && r.newRate != null)
                Text(tr('Sparquote: {0} % → {1} %', ['${r.currentRate}', '${r.newRate}'])),
              for (final w in r.warnings)
                Text('⚠ $w', style: TextStyle(color: theme.colorScheme.error)),
              for (final a in r.assumptions) _small(context, a),
              const SizedBox(height: 4),
              _small(context, r.disclaimer),
            ]),
          ),
        ),
    ]);
  }
}
