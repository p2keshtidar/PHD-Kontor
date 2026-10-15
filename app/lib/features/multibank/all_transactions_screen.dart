import 'dart:async';
import 'dart:convert';

import 'package:cross_file/cross_file.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/api_error.dart';
import '../../core/charts.dart';
import '../../core/dates.dart';
import '../../core/money.dart';
import '../../l10n/l10n.dart';
import 'duplicates_screen.dart';
import 'multibank_models.dart';
import 'multibank_repository.dart';

/// Alle Umsätze an einem Ort: Konten der Projektbank (Simulation) und verbundene Banken (nur lesend).
class AllTransactionsScreen extends ConsumerStatefulWidget {
  const AllTransactionsScreen({super.key});

  @override
  ConsumerState<AllTransactionsScreen> createState() => _AllTransactionsScreenState();
}

class _AllTransactionsScreenState extends ConsumerState<AllTransactionsScreen> {
  static const _pageSize = 30;

  final _search = TextEditingController();
  Timer? _debounce;
  String _source = 'ALL';
  bool _internal = true;
  final List<TxItem> _items = [];
  int _page = 0;
  bool _hasMore = true;
  bool _loading = false;
  String? _error;
  int _income = 0;
  int _expense = 0;
  List<String> _notes = const [];
  int _generation = 0;

  @override
  void initState() {
    super.initState();
    _reload();
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _search.dispose();
    super.dispose();
  }

  Future<void> _reload() async {
    _generation++;
    setState(() {
      _items.clear();
      _page = 0;
      _hasMore = true;
      _error = null;
      _loading = false;
    });
    await _loadMore();
  }

  Future<void> _loadMore() async {
    if (_loading || !_hasMore) return;
    final gen = _generation;
    setState(() => _loading = true);
    try {
      final page = await ref.read(multiBankRepositoryProvider).transactions(
            source: _source,
            query: _search.text,
            internal: _internal,
            page: _page,
            size: _pageSize,
          );
      if (!mounted || gen != _generation) return;
      setState(() {
        _items.addAll(page.items);
        _hasMore = page.hasMore;
        _income = page.incomeCents;
        _expense = page.expenseCents;
        _notes = page.notes;
        _page++;
        _loading = false;
      });
    } catch (e) {
      if (!mounted || gen != _generation) return;
      setState(() {
        _error = apiErrorMessage(e);
        _loading = false;
      });
    }
  }

  /// CSV-Export der aktuellen Auswahl: im Browser als Datei, sonst in die Zwischenablage (für Tabellenprogramme).
  Future<void> _export() async {
    final messenger = ScaffoldMessenger.of(context);
    try {
      final csv = await ref.read(multiBankRepositoryProvider)
          .exportCsv(source: _source, query: _search.text, internal: _internal);
      final rows = csv.split('\r\n').where((l) => l.trim().isNotEmpty).length - 1;
      if (kIsWeb) {
        final name = 'umsaetze-${DateTime.now().toIso8601String().substring(0, 10)}.csv';
        await XFile.fromData(utf8.encode(csv), mimeType: 'text/csv', name: name).saveTo(name);
        messenger.showSnackBar(SnackBar(content: Text(tr('CSV-Datei gespeichert ({0} Umsätze)', [rows]))));
      } else {
        await Clipboard.setData(ClipboardData(text: csv.replaceFirst('\uFEFF', '')));
        messenger.showSnackBar(SnackBar(
            content: Text(tr('CSV in die Zwischenablage kopiert ({0} Umsätze) – zum Einfügen in eine Tabelle.', [rows]))));
      }
    } catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(apiErrorMessage(e))));
    }
  }

  Future<void> _openDuplicates() async {
    await Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => const DuplicatesScreen()));
    ref.invalidate(duplicatesProvider);
    _reload();
  }

  void _onSearch(String _) {
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 400), _reload);
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final sources = {
      'ALL': tr('Alle'),
      'PROJEKTBANK': tr('Projektbank'),
      'EXTERNAL': tr('Andere Banken'),
      'IMPORT': tr('CSV-Import'),
    };
    final duplicates = ref.watch(duplicatesProvider).valueOrNull?.pairs.length ?? 0;
    final children = <Widget>[
      if (duplicates > 0)
        Card(
          key: const Key('tx_duplicates_hint'),
          color: theme.colorScheme.tertiaryContainer,
          child: ListTile(
            leading: const Icon(Icons.compare_arrows),
            title: Text(duplicates == 1
                ? tr('1 möglicher doppelter Umsatz')
                : tr('{0} mögliche doppelte Umsätze', [duplicates])),
            subtitle: Text(tr('Prüfen, ob etwas doppelt erfasst oder abgebucht wurde')),
            trailing: const Icon(Icons.chevron_right),
            onTap: _openDuplicates,
          ),
        ),
      TextField(
        key: const Key('tx_search'),
        controller: _search,
        onChanged: _onSearch,
        decoration: InputDecoration(
          prefixIcon: const Icon(Icons.search),
          hintText: tr('Suchen (Name, Verwendungszweck, Konto)'),
          isDense: true,
          border: const OutlineInputBorder(),
        ),
      ),
      const SizedBox(height: 8),
      Wrap(spacing: 6, runSpacing: 6, children: [
        for (final e in sources.entries)
          ChoiceChip(
            key: Key('tx_source_${e.key}'),
            label: Text(e.value),
            selected: _source == e.key,
            onSelected: (_) {
              setState(() => _source = e.key);
              _reload();
            },
          ),
        FilterChip(
          key: const Key('tx_internal'),
          label: Text(tr('Umbuchungen zeigen')),
          selected: _internal,
          onSelected: (v) {
            setState(() => _internal = v);
            _reload();
          },
        ),
      ]),
      const SizedBox(height: 8),
      Card(
        key: const Key('tx_sums'),
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Row(children: [
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(tr('Einnahmen'), style: theme.textTheme.bodySmall),
                Text(formatCents(_income),
                    style: const TextStyle(fontWeight: FontWeight.bold, color: IncomeExpenseChart.incomeColor)),
              ]),
            ),
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(tr('Ausgaben'), style: theme.textTheme.bodySmall),
                Text(formatCents(-_expense),
                    style: TextStyle(fontWeight: FontWeight.bold, color: theme.colorScheme.error)),
              ]),
            ),
          ]),
        ),
      ),
    ];

    DateTime? lastDay;
    for (final t in _items) {
      final day = DateTime(t.date.year, t.date.month, t.date.day);
      if (lastDay == null || day != lastDay) {
        children.add(Padding(
          padding: const EdgeInsets.only(top: 12, bottom: 4),
          child: Text(dayHeader(day, DateTime.now()), style: theme.textTheme.labelLarge),
        ));
        lastDay = day;
      }
      children.add(_TxTile(item: t));
    }
    if (_error != null) {
      children.add(Padding(
        padding: const EdgeInsets.all(16),
        child: Column(children: [
          Text(_error!, textAlign: TextAlign.center),
          TextButton(onPressed: _reload, child: Text(tr('Erneut versuchen'))),
        ]),
      ));
    } else if (_loading) {
      children.add(const Padding(padding: EdgeInsets.all(24), child: Center(child: CircularProgressIndicator())));
    } else if (_items.isEmpty) {
      children.add(Padding(
        padding: const EdgeInsets.all(24),
        child: Text(tr('Keine Umsätze gefunden.'), key: const Key('tx_empty'), textAlign: TextAlign.center),
      ));
    } else if (_hasMore) {
      children.add(Center(
        child: TextButton(key: const Key('tx_more'), onPressed: _loadMore, child: Text(tr('Weitere laden'))),
      ));
    }
    if (_notes.isNotEmpty) {
      children.add(Padding(
        padding: const EdgeInsets.only(top: 12),
        child: Text(_notes.join(' '), key: const Key('tx_notes'), style: theme.textTheme.bodySmall),
      ));
    }

    return Scaffold(
      appBar: AppBar(title: Text(tr('Alle Umsätze')), actions: [
        IconButton(
          key: const Key('tx_duplicates'),
          tooltip: tr('Mögliche Duplikate'),
          icon: const Icon(Icons.compare_arrows),
          onPressed: _openDuplicates,
        ),
        IconButton(
          key: const Key('tx_export'),
          tooltip: kIsWeb ? tr('Als CSV-Datei speichern') : tr('Als CSV kopieren'),
          icon: const Icon(Icons.download),
          onPressed: _export,
        ),
      ]),
      body: RefreshIndicator(
        onRefresh: _reload,
        child: ListView(
          padding: EdgeInsets.fromLTRB(16, 12, 16, 16 + MediaQuery.paddingOf(context).bottom),
          children: children,
        ),
      ),
    );
  }
}

class _TxTile extends StatelessWidget {
  const _TxTile({required this.item});

  final TxItem item;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final positive = item.amountCents > 0;
    final amount = item.currency == 'EUR'
        ? formatCents(item.amountCents)
        : '${(item.amountCents / 100).toStringAsFixed(2)} ${item.currency}';
    return ListTile(
      key: Key('tx_${item.source}_${item.id}_${item.accountId}'),
      contentPadding: EdgeInsets.zero,
      leading: Icon(item.internal
          ? Icons.swap_horiz
          : item.isImport
              ? Icons.upload_file
              : item.isExternal
                  ? Icons.account_balance_outlined
                  : Icons.account_balance),
      title: Text(item.title, maxLines: 1, overflow: TextOverflow.ellipsis),
      subtitle: Wrap(spacing: 6, runSpacing: 2, crossAxisAlignment: WrapCrossAlignment.center, children: [
        Text([item.accountName, if (item.institution != null) item.institution!].join(' · '),
            maxLines: 2, overflow: TextOverflow.ellipsis),
        if (item.isPending) _Chip(tr('vorgemerkt')),
        if (item.internal) _Chip(tr('Umbuchung')),
        if (!item.internal && item.categoryLabel.isNotEmpty) _Chip(item.categoryLabel),
        if (item.note != null)
          Row(mainAxisSize: MainAxisSize.min, children: [
            const Icon(Icons.sticky_note_2_outlined, size: 14),
            const SizedBox(width: 2),
            Flexible(child: Text(item.note!, maxLines: 1, overflow: TextOverflow.ellipsis)),
          ]),
      ]),
      trailing: Text(amount,
          style: TextStyle(
              fontWeight: FontWeight.bold,
              color: item.internal ? null : (positive ? IncomeExpenseChart.incomeColor : theme.colorScheme.error))),
      onTap: item.isImport
          ? null
          : () => context.push(item.isExternal ? '/banken/umsatz/${item.id}' : '/umsaetze/${item.accountId}'),
    );
  }
}

class _Chip extends StatelessWidget {
  const _Chip(this.text);

  final String text;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
      decoration: BoxDecoration(color: scheme.surfaceContainerHighest, borderRadius: BorderRadius.circular(8)),
      child: Text(text, style: const TextStyle(fontSize: 11)),
    );
  }
}
