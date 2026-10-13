import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/api_error.dart';
import '../../core/ids.dart';
import '../../core/money.dart';
import '../accounts/accounts_repository.dart';
import '../accounts/bookings_repository.dart';
import '../contacts/contact.dart';
import '../contacts/contacts_repository.dart';
import 'transfer_models.dart';
import 'transfer_repository.dart';

/// Überweisung in drei Schritten (/NF050/): Eingeben -> Zusammenfassung prüfen -> Senden.
class TransferScreen extends ConsumerStatefulWidget {
  const TransferScreen({super.key});

  @override
  ConsumerState<TransferScreen> createState() => _TransferScreenState();
}

class _TransferScreenState extends ConsumerState<TransferScreen> {
  final _formKey = GlobalKey<FormState>();
  final _recipient = TextEditingController();
  final _amount = TextEditingController();
  final _description = TextEditingController();
  Contact? _selected;
  bool _checking = false;
  String? _error;

  @override
  void dispose() {
    _recipient.dispose();
    _amount.dispose();
    _description.dispose();
    super.dispose();
  }

  void _select(Contact c) {
    setState(() {
      _selected = c;
      _recipient.text = c.iban;
    });
  }

  Future<void> _openAddressBook() async {
    final c = await context.push<Contact>('/adressbuch?select=1');
    if (c != null) _select(c);
  }

  Future<void> _next() async {
    FocusScope.of(context).unfocus();
    if (!_formKey.currentState!.validate()) return;
    final cents = parseEuroToCents(_amount.text)!;
    setState(() {
      _checking = true;
      _error = null;
    });
    try {
      final check = await ref
          .read(transferRepositoryProvider)
          .check(_recipient.text, cents, _description.text);
      if (!mounted) return;
      setState(() => _checking = false);
      final result = await showModalBottomSheet<TransferResult>(
        context: context,
        isScrollControlled: true,
        showDragHandle: true,
        isDismissible: false,
        builder: (_) => _SummarySheet(
          check: check,
          recipientInput: _recipient.text,
        ),
      );
      if (result != null && mounted) {
        ref.invalidate(accountsProvider);
        ref.invalidate(recentBookingsProvider);
        _formKey.currentState!.reset();
        _recipient.clear();
        _amount.clear();
        _description.clear();
        setState(() => _selected = null);
        context.push('/ueberweisung-erfolg', extra: result);
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _checking = false;
          _error = apiErrorMessage(e);
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final contacts = ref.watch(contactsProvider);
    final theme = Theme.of(context);
    return GestureDetector(
      onTap: () => FocusScope.of(context).unfocus(),
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          // Schnellauswahl aus dem Adressbuch
          SizedBox(
            height: 96,
            child: contacts.maybeWhen(
              data: (list) => ListView(
                scrollDirection: Axis.horizontal,
                children: [
                  _QuickContact(
                    label: 'Adressbuch',
                    icon: Icons.contacts,
                    onTap: _openAddressBook,
                  ),
                  _QuickContact(
                    label: 'Daueraufträge',
                    icon: Icons.event_repeat,
                    onTap: () => context.push('/dauerauftraege'),
                  ),
                  for (final c in list)
                    _QuickContact(
                      label: c.displayName,
                      initial: c.displayName.isEmpty ? '?' : c.displayName[0].toUpperCase(),
                      selected: _selected?.id == c.id,
                      onTap: () => _select(c),
                    ),
                ],
              ),
              orElse: () => Row(children: [
                _QuickContact(label: 'Adressbuch', icon: Icons.contacts, onTap: _openAddressBook),
                _QuickContact(
                  label: 'Daueraufträge',
                  icon: Icons.event_repeat,
                  onTap: () => context.push('/dauerauftraege'),
                ),
              ]),
            ),
          ),
          const SizedBox(height: 8),
          Form(
            key: _formKey,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                TextFormField(
                  key: const Key('transfer_recipient'),
                  controller: _recipient,
                  onChanged: (_) {
                    if (_selected != null) setState(() => _selected = null);
                  },
                  decoration: InputDecoration(
                    labelText: 'Empfänger',
                    hintText: 'IBAN, E-Mail oder Telefonnummer',
                    prefixIcon: const Icon(Icons.person),
                    helperText: _selected == null ? null : 'Ausgewählt: ${_selected!.fullName}',
                  ),
                  validator: (v) => (v == null || v.trim().isEmpty) ? 'Bitte Empfänger eingeben' : null,
                ),
                const SizedBox(height: 16),
                TextFormField(
                  key: const Key('transfer_amount'),
                  controller: _amount,
                  keyboardType: const TextInputType.numberWithOptions(decimal: true),
                  decoration: const InputDecoration(
                    labelText: 'Betrag',
                    hintText: 'z. B. 25,50',
                    suffixText: '€',
                    prefixIcon: Icon(Icons.euro),
                  ),
                  validator: (v) {
                    final cents = parseEuroToCents(v ?? '');
                    if (cents == null) return 'Bitte gültigen Betrag eingeben (max. 2 Nachkommastellen)';
                    if (cents <= 0) return 'Der Betrag muss größer als 0 sein';
                    return null;
                  },
                ),
                const SizedBox(height: 16),
                TextFormField(
                  controller: _description,
                  maxLength: 140,
                  decoration: const InputDecoration(
                    labelText: 'Verwendungszweck (optional)',
                    prefixIcon: Icon(Icons.notes),
                  ),
                ),
                if (_error != null) ...[
                  const SizedBox(height: 8),
                  Text(_error!, style: TextStyle(color: theme.colorScheme.error)),
                ],
                const SizedBox(height: 16),
                FilledButton.icon(
                  key: const Key('transfer_next'),
                  onPressed: _checking ? null : _next,
                  icon: _checking
                      ? const SizedBox(
                          width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
                      : const Icon(Icons.arrow_forward),
                  label: const Text('Weiter'),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _QuickContact extends StatelessWidget {
  const _QuickContact({
    required this.label,
    required this.onTap,
    this.icon,
    this.initial,
    this.selected = false,
  });

  final String label;
  final VoidCallback onTap;
  final IconData? icon;
  final String? initial;
  final bool selected;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12),
      child: SizedBox(
        width: 76,
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            CircleAvatar(
              radius: 26,
              backgroundColor: selected ? scheme.primary : scheme.secondaryContainer,
              foregroundColor: selected ? scheme.onPrimary : scheme.onSecondaryContainer,
              child: icon != null ? Icon(icon) : Text(initial ?? ''),
            ),
            const SizedBox(height: 6),
            Text(label, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 12)),
          ],
        ),
      ),
    );
  }
}

/// Zusammenfassung mit Name des Empfängers (/F240/) und Senden (/F250/).
class _SummarySheet extends ConsumerStatefulWidget {
  const _SummarySheet({required this.check, required this.recipientInput});

  final TransferCheck check;
  final String recipientInput;

  @override
  ConsumerState<_SummarySheet> createState() => _SummarySheetState();
}

class _SummarySheetState extends ConsumerState<_SummarySheet> {
  /// Einmal pro Zusammenfassung erzeugt: wiederholtes Tippen bucht nur einmal.
  final String _key = newIdempotencyKey();
  bool _sending = false;
  String? _error;

  Future<void> _send() async {
    setState(() {
      _sending = true;
      _error = null;
    });
    try {
      final c = widget.check;
      final result = await ref
          .read(transferRepositoryProvider)
          .execute(widget.recipientInput, c.amountCents, c.description, _key);
      if (mounted) Navigator.of(context).pop(result);
    } catch (e) {
      if (mounted) {
        setState(() {
          _sending = false;
          _error = apiErrorMessage(e);
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = widget.check;
    final theme = Theme.of(context);
    // Scrollbar, damit "Jetzt senden" auch auf sehr kleinen Displays erreichbar bleibt
    return SingleChildScrollView(
      padding: const EdgeInsets.fromLTRB(20, 0, 20, 24),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text('Überweisung prüfen', style: theme.textTheme.titleLarge),
          const SizedBox(height: 16),
          Center(
            child: Text(formatCents(c.amountCents),
                style: theme.textTheme.displaySmall?.copyWith(fontWeight: FontWeight.bold)),
          ),
          const SizedBox(height: 16),
          ListTile(
            contentPadding: EdgeInsets.zero,
            leading: const Icon(Icons.person),
            title: Text(c.recipientName, style: const TextStyle(fontWeight: FontWeight.bold)),
            subtitle: Text(formatIban(c.recipientIban)),
          ),
          ListTile(
            contentPadding: EdgeInsets.zero,
            leading: const Icon(Icons.notes),
            title: const Text('Verwendungszweck'),
            subtitle: Text(c.description),
          ),
          ListTile(
            contentPadding: EdgeInsets.zero,
            leading: const Icon(Icons.account_balance_wallet),
            title: const Text('Kontostand danach'),
            subtitle: Text(formatCents(c.availableCents - c.amountCents)),
          ),
          if (_error != null)
            Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: Text(_error!, style: TextStyle(color: theme.colorScheme.error)),
            ),
          const SizedBox(height: 8),
          FilledButton.icon(
            onPressed: _sending ? null : _send,
            icon: _sending
                ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
                : const Icon(Icons.send),
            label: const Text('Jetzt senden'),
          ),
          const SizedBox(height: 8),
          TextButton(
            onPressed: _sending ? null : () => Navigator.of(context).pop(),
            child: const Text('Abbrechen'),
          ),
        ],
      ),
    );
  }
}
