import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../l10n/l10n.dart';

import '../../core/api_error.dart';
import '../../core/dates.dart';
import '../../core/money.dart';
import '../insights/insights_models.dart';
import '../insights/insights_repository.dart';
import 'booking_item.dart';
import 'booking_tile.dart';
import 'bookings_repository.dart';
import '../../core/layout.dart';

/// Detailansicht eines Umsatzes mit Buchungsnummer (/F140/) und Kategorie (/F560/).
class BookingDetailScreen extends ConsumerStatefulWidget {
  const BookingDetailScreen({super.key, required this.item});

  final BookingItem? item;

  @override
  ConsumerState<BookingDetailScreen> createState() => _BookingDetailScreenState();
}

class _BookingDetailScreenState extends ConsumerState<BookingDetailScreen> {
  String? _category;
  String? _categoryLabel;

  /// Diese Kategorien sind fest (Umbuchung, Sparen, Startguthaben, Kreditauszahlung).
  bool get _editable => _category != null && selectableCategories.containsKey(_category);

  @override
  void initState() {
    super.initState();
    _category = widget.item?.category;
    _categoryLabel = widget.item?.categoryLabel;
  }

  Future<void> _changeCategory(BookingItem b) async {
    final picked = await showDialog<String>(
      context: context,
      builder: (ctx) => SimpleDialog(
        title: Text(tr('Kategorie wählen')),
        children: [
          for (final e in selectableCategories.entries)
            SimpleDialogOption(
              onPressed: () => Navigator.of(ctx).pop(e.key),
              child: Row(children: [
                Expanded(child: Text(e.value)),
                if (e.key == _category) const Icon(Icons.check, size: 18),
              ]),
            ),
        ],
      ),
    );
    if (picked == null || picked == _category || b.accountId == null) return;
    try {
      await ref.read(insightsRepositoryProvider).changeCategory(b.accountId!, b.bookingId, picked);
      setState(() {
        _category = picked;
        _categoryLabel = selectableCategories[picked];
      });
      ref.invalidate(monthsProvider);
      ref.invalidate(overviewProvider);
      ref.invalidate(recentBookingsProvider);
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(apiErrorMessage(e))));
    }
  }

  @override
  Widget build(BuildContext context) {
    final b = widget.item;
    if (b == null) {
      return Scaffold(
        appBar: AppBar(title: Text(tr('Umsatz'))),
        body: Center(child: Text(tr('Umsatz nicht verfügbar. Bitte aus der Liste öffnen.'))),
      );
    }
    final theme = Theme.of(context);
    final color = b.isIncoming ? BookingTile.incomingColor : theme.colorScheme.error;
    return Scaffold(
      appBar: AppBar(title: Text(tr('Umsatz'))),
      body: ListView(
        padding: safeListPadding(context),
        children: [
          const SizedBox(height: 12),
          Center(
            child: Text(
              '${b.isIncoming ? '+' : ''}${formatCents(b.amountCents)}',
              style: theme.textTheme.displaySmall?.copyWith(color: color, fontWeight: FontWeight.bold),
            ),
          ),
          const SizedBox(height: 6),
          Center(child: Text(b.isIncoming ? tr('Eingang') : tr('Ausgang'))),
          const SizedBox(height: 24),
          Card(
            child: Column(children: [
              _row(Icons.person, b.isIncoming ? tr('Von') : tr('An'), b.counterpartyName.isEmpty ? '–' : b.counterpartyName),
              if (b.counterpartyIban != null)
                _row(Icons.account_balance, 'IBAN', formatIban(b.counterpartyIban!), copyValue: b.counterpartyIban),
              _row(Icons.notes, tr('Verwendungszweck'), b.description),
              _row(Icons.category, tr('Art'), bookingTypeLabel(b.type)),
              ListTile(
                leading: const Icon(Icons.label),
                title: Text(tr('Kategorie')),
                subtitle: Text(_categoryLabel ?? '–'),
                trailing: _editable && b.accountId != null
                    ? TextButton(onPressed: () => _changeCategory(b), child: Text(tr('Ändern')))
                    : null,
              ),
              _row(Icons.schedule, tr('Datum'), formatDateTime(b.bookedAt)),
              _row(Icons.tag, tr('Buchungsnummer'), '${b.bookingId}'),
            ]),
          ),
        ],
      ),
    );
  }

  Widget _row(IconData icon, String label, String value, {String? copyValue}) {
    return ListTile(
      leading: Icon(icon),
      title: Text(label),
      subtitle: SelectableText(value),
      trailing: copyValue == null
          ? null
          : IconButton(
              icon: const Icon(Icons.copy),
              tooltip: tr('Kopieren'),
              onPressed: () {
                Clipboard.setData(ClipboardData(text: copyValue));
                ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(tr('Kopiert'))));
              },
            ),
    );
  }
}
