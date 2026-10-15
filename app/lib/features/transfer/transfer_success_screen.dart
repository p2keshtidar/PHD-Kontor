import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../l10n/l10n.dart';

import '../../core/api_error.dart';
import '../../core/dates.dart';
import '../../core/money.dart';
import '../contacts/contacts_repository.dart';
import 'transfer_models.dart';
import '../../core/layout.dart';

/// Bestätigung nach erfolgreicher Überweisung, mit Option "als Kontakt speichern".
class TransferSuccessScreen extends ConsumerStatefulWidget {
  const TransferSuccessScreen({super.key, required this.result});

  final TransferResult? result;

  @override
  ConsumerState<TransferSuccessScreen> createState() => _TransferSuccessScreenState();
}

class _TransferSuccessScreenState extends ConsumerState<TransferSuccessScreen> {
  bool _saving = false;
  bool _saved = false;

  Future<void> _saveContact(TransferResult r) async {
    setState(() => _saving = true);
    try {
      await ref.read(contactsRepositoryProvider).add(r.recipientIban);
      ref.invalidate(contactsProvider);
      if (mounted) setState(() => _saved = true);
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(apiErrorMessage(e))));
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final r = widget.result;
    final theme = Theme.of(context);
    final contacts = ref.watch(contactsProvider).valueOrNull ?? const [];
    final known = r != null && contacts.any((c) => c.userId == r.recipientUserId);

    return Scaffold(
      appBar: AppBar(automaticallyImplyLeading: false, title: Text(tr('Überweisung'))),
      body: r == null
          ? Center(child: Text(tr('Keine Daten.')))
          : ListView(
              padding: safeListPadding(context, 24),
              children: [
                const SizedBox(height: 24),
                Icon(Icons.check_circle, size: 88, color: theme.colorScheme.primary),
                const SizedBox(height: 16),
                Text(tr('Geld gesendet'), textAlign: TextAlign.center, style: theme.textTheme.headlineSmall),
                const SizedBox(height: 8),
                Text(
                  tr('{0} an {1}', [formatCents(r.amountCents), r.recipientName]),
                  textAlign: TextAlign.center,
                  style: theme.textTheme.titleMedium,
                ),
                const SizedBox(height: 24),
                Card(
                  child: Column(children: [
                    ListTile(title: Text(tr('Verwendungszweck')), subtitle: Text(r.description)),
                    ListTile(title: Text(tr('Ausgeführt am')), subtitle: Text(formatDateTime(r.bookedAt))),
                    ListTile(title: Text(tr('Neuer Kontostand')), subtitle: Text(formatCents(r.newBalanceCents))),
                    ListTile(title: Text(tr('Buchungsnummer')), subtitle: Text('${r.bookingId}')),
                  ]),
                ),
                const SizedBox(height: 16),
                if (!known && !_saved)
                  OutlinedButton.icon(
                    onPressed: _saving ? null : () => _saveContact(r),
                    icon: const Icon(Icons.person_add),
                    label: Text(tr('Empfänger im Adressbuch speichern')),
                  ),
                if (_saved) Text(tr('Im Adressbuch gespeichert ✓'), textAlign: TextAlign.center),
                const SizedBox(height: 12),
                FilledButton(
                  onPressed: () => context.go('/konto'),
                  child: Text(tr('Fertig')),
                ),
              ],
            ),
    );
  }
}
