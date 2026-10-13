import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

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
      appBar: AppBar(automaticallyImplyLeading: false, title: const Text('Überweisung')),
      body: r == null
          ? const Center(child: Text('Keine Daten.'))
          : ListView(
              padding: safeListPadding(context, 24),
              children: [
                const SizedBox(height: 24),
                Icon(Icons.check_circle, size: 88, color: theme.colorScheme.primary),
                const SizedBox(height: 16),
                Text('Geld gesendet', textAlign: TextAlign.center, style: theme.textTheme.headlineSmall),
                const SizedBox(height: 8),
                Text(
                  '${formatCents(r.amountCents)} an ${r.recipientName}',
                  textAlign: TextAlign.center,
                  style: theme.textTheme.titleMedium,
                ),
                const SizedBox(height: 24),
                Card(
                  child: Column(children: [
                    ListTile(title: const Text('Verwendungszweck'), subtitle: Text(r.description)),
                    ListTile(title: const Text('Ausgeführt am'), subtitle: Text(formatDateTime(r.bookedAt))),
                    ListTile(title: const Text('Neuer Kontostand'), subtitle: Text(formatCents(r.newBalanceCents))),
                    ListTile(title: const Text('Buchungsnummer'), subtitle: Text('${r.bookingId}')),
                  ]),
                ),
                const SizedBox(height: 16),
                if (!known && !_saved)
                  OutlinedButton.icon(
                    onPressed: _saving ? null : () => _saveContact(r),
                    icon: const Icon(Icons.person_add),
                    label: const Text('Empfänger im Adressbuch speichern'),
                  ),
                if (_saved) const Text('Im Adressbuch gespeichert ✓', textAlign: TextAlign.center),
                const SizedBox(height: 12),
                FilledButton(
                  onPressed: () => context.go('/konto'),
                  child: const Text('Fertig'),
                ),
              ],
            ),
    );
  }
}
