import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/api_error.dart';
import '../../core/dates.dart';
import '../../core/money.dart';
import 'booking_filter.dart';
import 'booking_item.dart';
import 'booking_tile.dart';
import 'bookings_repository.dart';

/// Alle Umsätze eines Kontos mit Suche, Filter und Nachladen beim Scrollen (/F120/, /F130/).
class TransactionsScreen extends ConsumerStatefulWidget {
  const TransactionsScreen({super.key, required this.accountId});

  final int accountId;

  @override
  ConsumerState<TransactionsScreen> createState() => _TransactionsScreenState();
}

class _TransactionsScreenState extends ConsumerState<TransactionsScreen> {
  static const _pageSize = 20;

  final _scroll = ScrollController();
  final _search = TextEditingController();
  Timer? _debounce;

  BookingFilter _filter = const BookingFilter();
  final List<BookingItem> _items = [];
  int _page = 0;
  bool _hasMore = true;
  bool _loading = false;
  String? _error;

  /// Zählt Ladevorgänge hoch, damit veraltete Antworten (z. B. nach neuer Suche) ignoriert werden.
  int _generation = 0;

  @override
  void initState() {
    super.initState();
    _scroll.addListener(() {
      if (_scroll.position.pixels > _scroll.position.maxScrollExtent - 300) {
        _loadMore();
      }
    });
    _reload();
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _scroll.dispose();
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
      final page = await ref.read(bookingsRepositoryProvider).fetchPage(
            widget.accountId,
            filter: _filter,
            page: _page,
            size: _pageSize,
          );
      if (!mounted || gen != _generation) return;
      setState(() {
        _items.addAll(page.items);
        _hasMore = page.hasMore;
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

  void _onSearchChanged(String value) {
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 400), () {
      _filter = _filter.withQuery(value);
      _reload();
    });
  }

  Future<void> _openFilter() async {
    final result = await showModalBottomSheet<BookingFilter>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (_) => _FilterSheet(initial: _filter),
    );
    if (result != null) {
      _filter = result;
      _reload();
    }
  }

  @override
  Widget build(BuildContext context) {
    final activeFilters = _filter.activeCount;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Umsätze'),
        actions: [
          IconButton(
            tooltip: 'Filter',
            onPressed: _openFilter,
            icon: Badge(
              isLabelVisible: activeFilters > 0,
              label: Text('$activeFilters'),
              child: const Icon(Icons.filter_list),
            ),
          ),
        ],
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(64),
          child: Padding(
            padding: const EdgeInsets.fromLTRB(12, 0, 12, 10),
            child: TextField(
              controller: _search,
              onChanged: _onSearchChanged,
              decoration: InputDecoration(
                hintText: 'Name oder Verwendungszweck suchen',
                prefixIcon: const Icon(Icons.search),
                isDense: true,
                suffixIcon: _search.text.isEmpty
                    ? null
                    : IconButton(
                        icon: const Icon(Icons.clear),
                        onPressed: () {
                          _search.clear();
                          _onSearchChanged('');
                          setState(() {});
                        },
                      ),
              ),
            ),
          ),
        ),
      ),
      body: RefreshIndicator(onRefresh: _reload, child: _buildList()),
    );
  }

  Widget _buildList() {
    if (_items.isEmpty) {
      Widget content;
      if (_loading) {
        content = const Center(child: CircularProgressIndicator());
      } else if (_error != null) {
        content = Column(children: [
          Text(_error!, textAlign: TextAlign.center),
          const SizedBox(height: 12),
          OutlinedButton(onPressed: _reload, child: const Text('Erneut versuchen')),
        ]);
      } else {
        content = const Text('Keine Umsätze gefunden.', textAlign: TextAlign.center);
      }
      return ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.all(32),
        children: [const SizedBox(height: 60), content],
      );
    }

    final now = DateTime.now();
    return ListView.builder(
      controller: _scroll,
      physics: const AlwaysScrollableScrollPhysics(),
      itemCount: _items.length + 1,
      itemBuilder: (context, i) {
        if (i == _items.length) {
          if (_loading) {
            return const Padding(
              padding: EdgeInsets.all(16),
              child: Center(child: CircularProgressIndicator()),
            );
          }
          if (_error != null) {
            return TextButton(onPressed: _loadMore, child: const Text('Weitere laden'));
          }
          return const SizedBox(height: 24);
        }
        final item = _items[i];
        final header = dayHeader(item.bookedAt, now);
        final showHeader = i == 0 || dayHeader(_items[i - 1].bookedAt, now) != header;
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (showHeader)
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 16, 16, 4),
                child: Text(header, style: Theme.of(context).textTheme.labelLarge),
              ),
            BookingTile(
              item: item,
              onTap: () => context.push('/umsatz', extra: item),
            ),
          ],
        );
      },
    );
  }
}

class _FilterSheet extends StatefulWidget {
  const _FilterSheet({required this.initial});

  final BookingFilter initial;

  @override
  State<_FilterSheet> createState() => _FilterSheetState();
}

class _FilterSheetState extends State<_FilterSheet> {
  late DateTimeRange? _range = widget.initial.from != null && widget.initial.to != null
      ? DateTimeRange(start: widget.initial.from!, end: widget.initial.to!)
      : null;
  late String? _direction = widget.initial.direction;
  late String? _type = widget.initial.type;
  late final _min = TextEditingController(
      text: widget.initial.minCents == null ? '' : formatCents(widget.initial.minCents!).replaceAll(' €', ''));
  late final _max = TextEditingController(
      text: widget.initial.maxCents == null ? '' : formatCents(widget.initial.maxCents!).replaceAll(' €', ''));
  String? _amountError;

  @override
  void dispose() {
    _min.dispose();
    _max.dispose();
    super.dispose();
  }

  Future<void> _pickRange() async {
    final now = DateTime.now();
    final picked = await showDateRangePicker(
      context: context,
      firstDate: DateTime(now.year - 5),
      lastDate: now,
      initialDateRange: _range,
      helpText: 'Zeitraum wählen',
    );
    if (picked != null) setState(() => _range = picked);
  }

  void _apply() {
    final min = _min.text.trim().isEmpty ? null : parseEuroToCents(_min.text);
    final max = _max.text.trim().isEmpty ? null : parseEuroToCents(_max.text);
    if ((_min.text.trim().isNotEmpty && min == null) || (_max.text.trim().isNotEmpty && max == null)) {
      setState(() => _amountError = 'Betrag ungültig, z. B. 25,50');
      return;
    }
    if (min != null && max != null && min > max) {
      setState(() => _amountError = '„Von“ muss kleiner als „Bis“ sein');
      return;
    }
    Navigator.of(context).pop(BookingFilter(
      from: _range?.start,
      to: _range?.end,
      direction: _direction,
      minCents: min,
      maxCents: max,
      type: _type,
      query: widget.initial.query,
    ));
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.fromLTRB(20, 0, 20, MediaQuery.of(context).viewInsets.bottom + 20),
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text('Filter', style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: 16),
            const Text('Zeitraum'),
            const SizedBox(height: 6),
            OutlinedButton.icon(
              icon: const Icon(Icons.date_range),
              label: Text(_range == null
                  ? 'Alle'
                  : '${formatDate(_range!.start)} – ${formatDate(_range!.end)}'),
              onPressed: _pickRange,
            ),
            const SizedBox(height: 16),
            const Text('Richtung'),
            const SizedBox(height: 6),
            SegmentedButton<String>(
              segments: const [
                ButtonSegment(value: 'ALL', label: Text('Alle')),
                ButtonSegment(value: 'IN', label: Text('Eingänge')),
                ButtonSegment(value: 'OUT', label: Text('Ausgänge')),
              ],
              selected: {_direction ?? 'ALL'},
              onSelectionChanged: (s) =>
                  setState(() => _direction = s.first == 'ALL' ? null : s.first),
            ),
            const SizedBox(height: 16),
            const Text('Betrag in €'),
            const SizedBox(height: 6),
            Row(children: [
              Expanded(
                child: TextField(
                  controller: _min,
                  keyboardType: const TextInputType.numberWithOptions(decimal: true),
                  decoration: const InputDecoration(labelText: 'Von', isDense: true),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: TextField(
                  controller: _max,
                  keyboardType: const TextInputType.numberWithOptions(decimal: true),
                  decoration: const InputDecoration(labelText: 'Bis', isDense: true),
                ),
              ),
            ]),
            if (_amountError != null)
              Padding(
                padding: const EdgeInsets.only(top: 6),
                child: Text(_amountError!,
                    style: TextStyle(color: Theme.of(context).colorScheme.error)),
              ),
            const SizedBox(height: 16),
            DropdownButtonFormField<String?>(
              value: _type,
              decoration: const InputDecoration(labelText: 'Art', isDense: true),
              items: [
                const DropdownMenuItem<String?>(value: null, child: Text('Alle')),
                for (final e in bookingTypeLabels.entries)
                  DropdownMenuItem<String?>(value: e.key, child: Text(e.value)),
              ],
              onChanged: (v) => setState(() => _type = v),
            ),
            const SizedBox(height: 24),
            Row(children: [
              Expanded(
                child: OutlinedButton(
                  onPressed: () => Navigator.of(context).pop(widget.initial.withoutFilters()),
                  child: const Text('Zurücksetzen'),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(child: FilledButton(onPressed: _apply, child: const Text('Anwenden'))),
            ]),
          ],
        ),
      ),
    );
  }
}
