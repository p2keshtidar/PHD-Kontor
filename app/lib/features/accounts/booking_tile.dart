import 'package:flutter/material.dart';

import '../../core/dates.dart';
import '../../core/money.dart';
import 'booking_item.dart';

/// Eine Zeile in der Umsatzliste: Eingang grün, Ausgang rot (/F120/).
class BookingTile extends StatelessWidget {
  const BookingTile({super.key, required this.item, this.onTap});

  final BookingItem item;
  final VoidCallback? onTap;

  static const incomingColor = Color(0xFF2E7D32);

  IconData get _icon => switch (item.type) {
        'TRANSFER' => item.isIncoming ? Icons.call_received : Icons.call_made,
        'CARD' => Icons.credit_card,
        'INITIAL' => Icons.card_giftcard,
        'INTERNAL' => Icons.swap_vert,
        'SAVING_RATE' => Icons.savings,
        'DEPOT' => Icons.show_chart,
        'INCOME' => Icons.work,
        'DIRECT_DEBIT' => Icons.receipt,
        'LOAN_PAYOUT' => Icons.request_quote,
        'LOAN_RATE' => Icons.request_quote,
        'LOAN_EXTRA' => Icons.bolt,
        _ => Icons.receipt_long,
      };

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final title = item.counterpartyName.isNotEmpty
        ? item.counterpartyName
        : bookingTypeLabel(item.type);
    return ListTile(
      onTap: onTap,
      leading: CircleAvatar(
        backgroundColor: scheme.secondaryContainer,
        child: Icon(_icon, color: scheme.onSecondaryContainer, size: 20),
      ),
      title: Text(title, maxLines: 1, overflow: TextOverflow.ellipsis),
      subtitle: Text(
        '${item.categoryLabel ?? item.description} · ${formatDateTime(item.bookedAt)}',
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      ),
      trailing: Text(
        '${item.isIncoming ? '+' : ''}${formatCents(item.amountCents)}',
        style: TextStyle(
          fontWeight: FontWeight.bold,
          color: item.isIncoming ? incomingColor : scheme.error,
        ),
      ),
    );
  }
}
