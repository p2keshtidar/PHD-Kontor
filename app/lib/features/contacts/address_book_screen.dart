import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/api_error.dart';
import '../../core/money.dart';
import 'contact.dart';
import 'contacts_repository.dart';
import 'device_contacts.dart';

/// Adressbuch (/F220/). Mit [selectMode] gibt ein Tippen den Kontakt an die Überweisung zurück.
class AddressBookScreen extends ConsumerStatefulWidget {
  const AddressBookScreen({super.key, this.selectMode = false});

  final bool selectMode;

  @override
  ConsumerState<AddressBookScreen> createState() => _AddressBookScreenState();
}

class _AddressBookScreenState extends ConsumerState<AddressBookScreen> {
  List<ContactMatch>? _matches;
  bool _searchingDevice = false;

  /// Weggewischte Kontakte sofort ausblenden (Dismissible darf nicht im Baum bleiben).
  final Set<int> _removed = {};

  void _snack(String text) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));
  }

  Future<void> _addManually() async {
    final added = await showDialog<bool>(context: context, builder: (_) => const _AddContactDialog());
    if (added == true) ref.invalidate(contactsProvider);
  }

  Future<void> _searchDevice() async {
    setState(() => _searchingDevice = true);
    try {
      final phones = await DeviceContacts.loadPhoneNumbers();
      if (phones == null) {
        _snack('Zugriff auf Kontakte wurde nicht erlaubt.');
        return;
      }
      final matches = await ref.read(contactsRepositoryProvider).match(phones);
      if (mounted) setState(() => _matches = matches);
      if (matches.isEmpty) _snack('Keine deiner Kontakte nutzen diese App.');
    } catch (e) {
      if (mounted) _snack(apiErrorMessage(e));
    } finally {
      if (mounted) setState(() => _searchingDevice = false);
    }
  }

  Future<void> _addMatch(ContactMatch m) async {
    try {
      await ref.read(contactsRepositoryProvider).add(m.iban);
      ref.invalidate(contactsProvider);
      setState(() => _matches = _matches?.where((x) => x.userId != m.userId).toList());
      _snack('${m.fullName} gespeichert');
    } catch (e) {
      _snack(apiErrorMessage(e));
    }
  }

  Future<void> _delete(Contact c) async {
    setState(() => _removed.add(c.id));
    try {
      await ref.read(contactsRepositoryProvider).delete(c.id);
      ref.invalidate(contactsProvider);
    } catch (e) {
      _snack(apiErrorMessage(e));
      if (mounted) setState(() => _removed.remove(c.id));
      ref.invalidate(contactsProvider);
    }
  }

  @override
  Widget build(BuildContext context) {
    final contacts = ref.watch(contactsProvider);
    return Scaffold(
      appBar: AppBar(title: Text(widget.selectMode ? 'Empfänger wählen' : 'Adressbuch')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _addManually,
        icon: const Icon(Icons.person_add),
        label: const Text('Hinzufügen'),
      ),
      body: RefreshIndicator(
        onRefresh: () => ref.refresh(contactsProvider.future),
        child: ListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.only(bottom: 96),
          children: [
            if (DeviceContacts.isSupported)
              Padding(
                padding: const EdgeInsets.all(16),
                child: OutlinedButton.icon(
                  onPressed: _searchingDevice ? null : _searchDevice,
                  icon: _searchingDevice
                      ? const SizedBox(
                          width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
                      : const Icon(Icons.contacts),
                  label: const Text('Kontakte vom Handy durchsuchen'),
                ),
              ),
            if (_matches != null && _matches!.isNotEmpty) ...[
              const _Header('Gefunden in deinen Kontakten'),
              for (final m in _matches!)
                ListTile(
                  leading: const CircleAvatar(child: Icon(Icons.person_search)),
                  title: Text(m.fullName),
                  subtitle: Text(formatIban(m.iban)),
                  trailing: m.alreadyContact
                      ? const Icon(Icons.check)
                      : IconButton(
                          tooltip: 'Speichern',
                          icon: const Icon(Icons.add_circle_outline),
                          onPressed: () => _addMatch(m),
                        ),
                ),
              const Divider(),
            ],
            const _Header('Gespeicherte Empfänger'),
            contacts.when(
              loading: () => const Padding(
                padding: EdgeInsets.all(24),
                child: Center(child: CircularProgressIndicator()),
              ),
              error: (e, _) => Padding(padding: const EdgeInsets.all(16), child: Text(apiErrorMessage(e))),
              data: (all) {
                final list = all.where((c) => !_removed.contains(c.id)).toList();
                return list.isEmpty
                  ? const Padding(
                      padding: EdgeInsets.all(16),
                      child: Text('Noch keine Empfänger gespeichert.'),
                    )
                  : Column(children: [
                      for (final c in list)
                        Dismissible(
                          key: ValueKey(c.id),
                          direction: DismissDirection.endToStart,
                          background: Container(
                            color: Theme.of(context).colorScheme.errorContainer,
                            alignment: Alignment.centerRight,
                            padding: const EdgeInsets.only(right: 24),
                            child: const Icon(Icons.delete),
                          ),
                          onDismissed: (_) => _delete(c),
                          child: ListTile(
                            leading: CircleAvatar(
                                child: Text(c.displayName.isEmpty ? '?' : c.displayName[0].toUpperCase())),
                            title: Text(c.displayName),
                            subtitle: Text(c.displayName == c.fullName
                                ? formatIban(c.iban)
                                : '${c.fullName} · ${formatIban(c.iban)}'),
                            trailing: widget.selectMode ? const Icon(Icons.chevron_right) : null,
                            onTap: widget.selectMode ? () => context.pop(c) : null,
                          ),
                        ),
                    ]);
              },
            ),
            const Padding(
              padding: EdgeInsets.all(16),
              child: Text('Zum Löschen nach links wischen.', style: TextStyle(fontSize: 12)),
            ),
          ],
        ),
      ),
    );
  }
}

class _Header extends StatelessWidget {
  const _Header(this.text);

  final String text;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
        child: Text(text, style: Theme.of(context).textTheme.labelLarge),
      );
}

class _AddContactDialog extends ConsumerStatefulWidget {
  const _AddContactDialog();

  @override
  ConsumerState<_AddContactDialog> createState() => _AddContactDialogState();
}

class _AddContactDialogState extends ConsumerState<_AddContactDialog> {
  final _recipient = TextEditingController();
  final _nickname = TextEditingController();
  bool _saving = false;
  String? _error;

  @override
  void dispose() {
    _recipient.dispose();
    _nickname.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (_recipient.text.trim().isEmpty) {
      setState(() => _error = 'Bitte IBAN, E-Mail oder Telefonnummer eingeben');
      return;
    }
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      await ref.read(contactsRepositoryProvider).add(_recipient.text, nickname: _nickname.text);
      if (mounted) Navigator.of(context).pop(true);
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = apiErrorMessage(e);
          _saving = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Empfänger hinzufügen'),
      content: SingleChildScrollView(child: Column(mainAxisSize: MainAxisSize.min, children: [
        TextField(
          controller: _recipient,
          decoration: const InputDecoration(labelText: 'IBAN, E-Mail oder Telefonnummer'),
        ),
        const SizedBox(height: 12),
        TextField(
          controller: _nickname,
          decoration: const InputDecoration(labelText: 'Spitzname (optional)'),
        ),
        if (_error != null)
          Padding(
            padding: const EdgeInsets.only(top: 12),
            child: Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
          ),
      ])),
      actions: [
        TextButton(onPressed: () => Navigator.of(context).pop(false), child: const Text('Abbrechen')),
        FilledButton(onPressed: _saving ? null : _save, child: const Text('Speichern')),
      ],
    );
  }
}
