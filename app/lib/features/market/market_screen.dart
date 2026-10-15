import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/api_error.dart';
import '../../core/layout.dart';
import '../../core/money.dart';
import '../../core/text_dialog.dart';
import '../../l10n/l10n.dart';
import 'market_models.dart';
import 'market_repository.dart';

String _pct(double v) => L10n.isEnglish ? v.toStringAsFixed(2) : v.toStringAsFixed(2).replaceAll('.', ',');

/// Finanz-Marktplatz: Angebote fiktiver Anbieter vergleichen, merken, nebeneinanderlegen und (simuliert) beantragen.
class MarketScreen extends ConsumerStatefulWidget {
  const MarketScreen({super.key});

  @override
  ConsumerState<MarketScreen> createState() => _MarketScreenState();
}

class _MarketScreenState extends ConsumerState<MarketScreen> {
  String _category = 'GIRO';
  int _amount = 1000000;
  final Set<String> _compare = {};
  final Set<String> _expanded = {};
  Future<MarketCatalog>? _future;

  @override
  void initState() {
    super.initState();
    _load();
  }

  void _load() {
    setState(() {
      _future = ref.read(marketRepositoryProvider).catalog(_category,
          amountCents: _category == 'CALL' || _category == 'FIXED' ? _amount : null);
    });
  }

  Future<void> _toggleFavorite(MarketProduct p) async {
    try {
      await ref.read(marketRepositoryProvider).favorite(p.id, !p.favorite);
      _load();
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(apiErrorMessage(e))));
    }
  }

  Future<void> _apply(MarketProduct p) async {
    final ok = await showDialog<bool>(context: context, builder: (_) => _ApplyDialog(product: p));
    if (ok != true || !mounted) return;
    try {
      await ref.read(marketRepositoryProvider).apply(p.id);
      ref.invalidate(marketApplicationsProvider);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(tr('Antrag angelegt (Simulation)'))));
        context.push('/antraege');
      }
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(apiErrorMessage(e))));
    }
  }

  Future<void> _changeAmount() async {
    final text = await askText(context,
        title: tr('Beispielbetrag'),
        initial: formatCents(_amount).replaceAll(' €', '').replaceAll('€', ''),
        label: tr('Betrag'),
        maxLength: 12,
        fieldKey: const Key('market_amount_field'),
        saveKey: const Key('market_amount_save'));
    final cents = text == null ? null : parseEuroToCents(text);
    if (cents != null && cents >= 100) {
      _amount = cents;
      _load();
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final categories = {
      'GIRO': tr('Girokonten'),
      'CREDIT_CARD': tr('Kreditkarten'),
      'CALL': tr('Tagesgeld'),
      'FIXED': tr('Festgeld'),
      'DEPOT': tr('Depots'),
    };
    return Scaffold(
      appBar: AppBar(title: Text(tr('Finanz-Marktplatz')), actions: [
        IconButton(
          key: const Key('market_applications'),
          tooltip: tr('Meine Anträge'),
          icon: const Icon(Icons.assignment_outlined),
          onPressed: () => context.push('/antraege'),
        ),
      ]),
      floatingActionButton: _compare.length >= 2
          ? FloatingActionButton.extended(
              key: const Key('market_compare'),
              onPressed: () => Navigator.of(context).push(MaterialPageRoute<void>(
                  builder: (_) => CompareScreen(ids: _compare.toList(),
                      amountCents: _category == 'CALL' || _category == 'FIXED' ? _amount : null))),
              icon: const Icon(Icons.compare),
              label: Text(tr('Vergleichen ({0})', [_compare.length])),
            )
          : null,
      body: ListView(
        padding: safeListPadding(context),
        children: [
          Wrap(spacing: 6, runSpacing: 6, children: [
            for (final e in categories.entries)
              ChoiceChip(
                key: Key('market_cat_${e.key}'),
                label: Text(e.value),
                selected: _category == e.key,
                onSelected: (_) {
                  _category = e.key;
                  _compare.clear();
                  _load();
                },
              ),
            ActionChip(
              key: const Key('market_cat_LOAN'),
              avatar: const Icon(Icons.open_in_new, size: 16),
              label: Text(tr('Kredite')),
              onPressed: () => context.push('/kredit/neu'),
            ),
          ]),
          if (_category == 'CALL' || _category == 'FIXED')
            Align(
              alignment: Alignment.centerLeft,
              child: TextButton.icon(
                key: const Key('market_amount'),
                onPressed: _changeAmount,
                icon: const Icon(Icons.edit, size: 18),
                label: Text(tr('Beispiel: {0} anlegen', [formatCents(_amount)])),
              ),
            ),
          FutureBuilder<MarketCatalog>(
            future: _future,
            builder: (context, snap) {
              if (snap.hasError) {
                return Padding(padding: const EdgeInsets.all(24), child: Text(apiErrorMessage(snap.error!)));
              }
              final c = snap.data;
              if (c == null) {
                return const Padding(padding: EdgeInsets.all(24), child: Center(child: CircularProgressIndicator()));
              }
              return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                Card(
                  key: const Key('market_disclaimer'),
                  color: theme.colorScheme.secondaryContainer,
                  child: Padding(padding: const EdgeInsets.all(12), child: Text(c.disclaimer)),
                ),
                Text(c.sortText, style: theme.textTheme.bodySmall),
                const SizedBox(height: 4),
                for (final p in c.products) _productCard(context, p),
              ]);
            },
          ),
        ],
      ),
    );
  }

  Widget _productCard(BuildContext context, MarketProduct p) {
    final theme = Theme.of(context);
    final open = _expanded.contains(p.id);
    final facts = <String>[
      if (p.interestPercent != null) tr('{0} % Zinsen', [_pct(p.interestPercent!)]),
      if (p.termMonths != null) tr('{0} Monate', [p.termMonths!]),
      if (p.monthlyFeeCents > 0) tr('{0} pro Monat', [formatCents(p.monthlyFeeCents)]),
      if (p.annualFeeCents > 0) tr('{0} pro Jahr', [formatCents(p.annualFeeCents)]),
      if (p.orderFeeCents != null) tr('{0} je Kauf', [formatCents(p.orderFeeCents!)]),
      if (p.minAmountCents != null) tr('ab {0}', [formatCents(p.minAmountCents!)]),
    ];
    return Card(
      key: Key('market_${p.id}'),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(12, 8, 4, 8),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(p.name, style: const TextStyle(fontWeight: FontWeight.bold)),
                Text(p.provider, style: theme.textTheme.bodySmall),
              ]),
            ),
            IconButton(
              key: Key('market_fav_${p.id}'),
              tooltip: p.favorite ? tr('Aus Favoriten entfernen') : tr('Als Favorit merken'),
              icon: Icon(p.favorite ? Icons.star : Icons.star_border, color: p.favorite ? Colors.amber.shade700 : null),
              onPressed: () => _toggleFavorite(p),
            ),
          ]),
          if (facts.isNotEmpty) Text(facts.join(' · ')),
          Text(p.exampleText,
              style: TextStyle(fontWeight: FontWeight.w600, color: p.eligible ? null : theme.colorScheme.error)),
          if (open) ...[
            const SizedBox(height: 6),
            for (final x in p.pros) _line(Icons.add_circle_outline, x, const Color(0xFF2E7D32)),
            for (final x in p.cons) _line(Icons.remove_circle_outline, x, theme.colorScheme.error),
            if (p.requirements.isNotEmpty) _line(Icons.rule, tr('Voraussetzungen: {0}', [p.requirements.join(', ')]), null),
          ],
          Wrap(spacing: 4, crossAxisAlignment: WrapCrossAlignment.center, children: [
            TextButton(
              key: Key('market_more_${p.id}'),
              onPressed: () => setState(() => open ? _expanded.remove(p.id) : _expanded.add(p.id)),
              child: Text(open ? tr('Weniger') : tr('Vor- und Nachteile')),
            ),
            FilterChip(
              key: Key('market_cmp_${p.id}'),
              label: Text(tr('Vergleichen')),
              selected: _compare.contains(p.id),
              onSelected: (v) => setState(() {
                if (!v) {
                  _compare.remove(p.id);
                } else if (_compare.length < 4) {
                  _compare.add(p.id);
                }
              }),
            ),
            TextButton(
              key: Key('market_apply_${p.id}'),
              onPressed: () => _apply(p),
              child: Text(tr('Antrag (Simulation)')),
            ),
          ]),
        ]),
      ),
    );
  }

  Widget _line(IconData icon, String text, Color? color) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 1),
        child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Icon(icon, size: 16, color: color),
          const SizedBox(width: 6),
          Expanded(child: Text(text)),
        ]),
      );
}

class _ApplyDialog extends StatefulWidget {
  const _ApplyDialog({required this.product});

  final MarketProduct product;

  @override
  State<_ApplyDialog> createState() => _ApplyDialogState();
}

class _ApplyDialogState extends State<_ApplyDialog> {
  bool _consent = false;

  @override
  Widget build(BuildContext context) {
    final p = widget.product;
    return AlertDialog(
      title: Text(tr('Antrag (Simulation)')),
      content: SingleChildScrollView(
        child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text('${p.name} · ${p.provider}', style: const TextStyle(fontWeight: FontWeight.bold)),
          const SizedBox(height: 8),
          Text(p.category == 'CREDIT_CARD'
              ? tr('Es wird kein echter Antrag gestellt und keine Karte bestellt oder ausgestellt.')
              : tr('Es wird kein echter Antrag gestellt und kein Konto eröffnet.')),
          if (p.documents.isNotEmpty) ...[
            const SizedBox(height: 8),
            Text(tr('Benötigte Unterlagen: {0}', [p.documents.join(', ')])),
          ],
          CheckboxListTile(
            key: const Key('market_consent'),
            contentPadding: EdgeInsets.zero,
            value: _consent,
            onChanged: (v) => setState(() => _consent = v ?? false),
            title: Text(tr('Mir ist klar, dass das eine Simulation ist.')),
          ),
        ]),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.of(context).pop(false), child: Text(tr('Abbrechen'))),
        FilledButton(
          key: const Key('market_apply_confirm'),
          onPressed: _consent ? () => Navigator.of(context).pop(true) : null,
          child: Text(tr('Antrag anlegen')),
        ),
      ],
    );
  }
}

/// Vergleichsliste: 2 bis 4 Angebote nebeneinander mit denselben Kriterien.
class CompareScreen extends ConsumerWidget {
  const CompareScreen({super.key, required this.ids, this.amountCents});

  final List<String> ids;
  final int? amountCents;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(tr('Vergleich'))),
      body: FutureBuilder<Comparison>(
        future: ref.read(marketRepositoryProvider).compare(ids, amountCents: amountCents),
        builder: (context, snap) {
          if (snap.hasError) return Center(child: Text(apiErrorMessage(snap.error!)));
          final c = snap.data;
          if (c == null) return const Center(child: CircularProgressIndicator());
          return ListView(padding: safeListPadding(context), children: [
            SingleChildScrollView(
              key: const Key('compare_table'),
              scrollDirection: Axis.horizontal,
              child: DataTable(
                columnSpacing: 16,
                columns: [
                  DataColumn(label: Text(tr('Kriterium'))),
                  for (final p in c.products) DataColumn(label: SizedBox(width: 140, child: Text(p.name))),
                ],
                rows: [
                  for (final r in c.rows)
                    DataRow(cells: [
                      DataCell(Text(r.label, style: const TextStyle(fontWeight: FontWeight.w600))),
                      for (final v in r.values) DataCell(SizedBox(width: 140, child: Text(v))),
                    ]),
                ],
              ),
            ),
            const SizedBox(height: 12),
            Text(c.disclaimer, style: theme.textTheme.bodySmall),
          ]);
        },
      ),
    );
  }
}
