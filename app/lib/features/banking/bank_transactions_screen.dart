import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/api_error.dart';
import '../../core/dates.dart';
import '../../l10n/l10n.dart';
import '../accounts/booking_tile.dart';
import '../insights/insights_models.dart';
import 'bank_models.dart';
import 'bank_repository.dart';
import '../../core/theme.dart';

/// Umsätze verbundener Konten: Suche, Filter nach Status und Kategorie, mehrere Banken gemeinsam.
class BankTransactionsScreen extends ConsumerStatefulWidget {
  const BankTransactionsScreen({super.key, this.accountId, this.accountName});

  final int? accountId;
  final String? accountName;

  @override
  ConsumerState<BankTransactionsScreen> createState() => _BankTransactionsScreenState();
}

class _BankTransactionsScreenState extends ConsumerState<BankTransactionsScreen> {
  late BankTxFilter _filter = BankTxFilter(accountId: widget.accountId);
  final List<BankTransaction> _items = [];
  int _page = 0;
  bool _hasMore = true;
  bool _loading = false;
  Object? _error;
  Timer? _debounce;
  int _generation = 0;

  @override
  void initState() {
    super.initState();
    _reload();
  }

  @override
  void dispose() {
    _debounce?.cancel();
    super.dispose();
  }

  Future<void> _reload() async {
    _generation++;
    setState(() {
      _items.clear();
      _page = 0;
      _hasMore = true;
      _error = null;
    });
    await _loadMore();
  }

  Future<void> _loadMore() async {
    if (_loading || !_hasMore) return;
    final gen = _generation;
    setState(() => _loading = true);
    try {
      final page = await ref.read(bankRepositoryProvider).transactions(_filter, _page);
      if (!mounted || gen != _generation) return;
      setState(() {
        _items.addAll(page.items);
        _hasMore = page.hasMore;
        _page++;
      });
    } catch (e) {
      if (mounted && gen == _generation) setState(() => _error = e);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  void _setFilter(BankTxFilter f) {
    if (f == _filter) return;
    _filter = f;
    _loading = false;
    _reload();
  }

  @override
  Widget build(BuildContext context) {
    final categories = selectableCategories;
    return Scaffold(
      appBar: AppBar(title: Text(widget.accountName ?? tr('Umsätze verbundener Konten'))),
      body: RefreshIndicator(
        onRefresh: _reload,
        child: NotificationListener<ScrollNotification>(
          onNotification: (n) {
            if (n.metrics.pixels > n.metrics.maxScrollExtent - 300) _loadMore();
            return false;
          },
          child: ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: EdgeInsets.fromLTRB(16, 12, 16, 16 + MediaQuery.paddingOf(context).bottom),
            children: [
              TextField(
                key: const Key('bank_tx_search'),
                decoration: InputDecoration(
                  labelText: tr('Suchen (Empfänger, Verwendungszweck, Notiz)'),
                  prefixIcon: const Icon(Icons.search),
                ),
                onChanged: (v) {
                  _debounce?.cancel();
                  _debounce = Timer(const Duration(milliseconds: 400), () => _setFilter(_filter.copyWith(query: v)));
                },
              ),
              const SizedBox(height: 8),
              SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                child: Row(children: [
                  ChoiceChip(
                    key: const Key('bank_status_all'),
                    label: Text(tr('Alle')),
                    selected: _filter.status == null,
                    onSelected: (_) => _setFilter(_filter.copyWith(status: null)),
                  ),
                  for (final e in bankStatusFilters.entries)
                    Padding(
                      padding: const EdgeInsets.only(left: 6),
                      child: ChoiceChip(
                        key: Key('bank_status_${e.key}'),
                        label: Text(e.value),
                        selected: _filter.status == e.key,
                        onSelected: (_) => _setFilter(_filter.copyWith(status: e.key)),
                      ),
                    ),
                ]),
              ),
              const SizedBox(height: 4),
              DropdownButtonFormField<String?>(
                key: const Key('bank_category_filter'),
                // ignore: deprecated_member_use
                value: _filter.category,
                isExpanded: true,
                decoration: InputDecoration(labelText: tr('Kategorie'), prefixIcon: const Icon(Icons.category)),
                items: [
                  DropdownMenuItem<String?>(value: null, child: Text(tr('Alle Kategorien'))),
                  for (final e in {...categories, 'UMBUCHUNG': tr('Umbuchung')}.entries)
                    DropdownMenuItem<String?>(value: e.key, child: Text(e.value)),
                ],
                onChanged: (v) => _setFilter(_filter.copyWith(category: v)),
              ),
              const SizedBox(height: 8),
              if (_error != null)
                Padding(
                  padding: const EdgeInsets.all(16),
                  child: Text(apiErrorMessage(_error!), textAlign: TextAlign.center),
                )
              else if (_items.isEmpty && !_loading)
                Padding(
                  padding: const EdgeInsets.all(24),
                  child: Text(tr('Keine Umsätze gefunden.'), textAlign: TextAlign.center),
                ),
              if (_items.isNotEmpty)
                Card(
                  child: Column(children: [
                    for (final t in _items)
                      BankTransactionTile(
                        item: t,
                        showBank: widget.accountId == null,
                        onTap: () async {
                          final changed = await context.push<bool>('/banken/umsatz/${t.id}');
                          if (changed == true) _reload();
                        },
                      ),
                  ]),
                ),
              if (_loading)
                const Padding(
                  padding: EdgeInsets.all(16),
                  child: Center(child: CircularProgressIndicator()),
                ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Eine Zeile: Empfänger, Kategorie, Datum, Status (vorgemerkt/storniert/zurückgebucht) und Betrag.
class BankTransactionTile extends StatelessWidget {
  const BankTransactionTile({super.key, required this.item, this.onTap, this.showBank = true});

  final BankTransaction item;
  final VoidCallback? onTap;
  final bool showBank;

  IconData get _icon => switch (item.status) {
        'PENDING' => Icons.schedule,
        'CANCELLED' => Icons.block,
        'REVERSED' => Icons.undo,
        'SUPERSEDED' => Icons.done_all,
        _ => item.reversal ? Icons.undo : (item.isIncoming ? Icons.south_west : Icons.north_east),
      };

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final parts = [
      item.categoryLabel,
      formatDate(item.bookingDate),
      if (showBank) item.bankName,
      if (item.status != 'BOOKED') item.statusLabel,
      if (item.excluded) tr('nicht im Überblick'),
    ];
    final amount = '${item.isIncoming ? '+' : ''}${formatBankAmount(item.amountCents, item.currency)}';
    return ListTile(
      key: Key('bank_tx_${item.id}'),
      onTap: onTap,
      leading: CircleAvatar(
        backgroundColor: item.isPending ? scheme.tertiaryContainer : scheme.secondaryContainer,
        child: Icon(_icon, size: 20, color: item.isPending ? scheme.onTertiaryContainer : scheme.onSecondaryContainer),
      ),
      title: Text(item.title, maxLines: 1, overflow: TextOverflow.ellipsis),
      subtitle: Text(parts.join(' · '), maxLines: 2, overflow: TextOverflow.ellipsis),
      trailing: Text(
        amount,
        style: TextStyle(
          fontWeight: FontWeight.bold,
          decoration: item.isInactive ? TextDecoration.lineThrough : null,
          color: item.isInactive
              ? scheme.outline
              : (item.isIncoming ? positiveColor(context) : scheme.error),
        ),
      ),
    );
  }
}
