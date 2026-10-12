import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/api_error.dart';
import '../../core/validators.dart';
import '../auth/app_user.dart';
import '../auth/auth_controller.dart';
import '../auth/auth_repository.dart';
import '../accounts/accounts_repository.dart';
import '../accounts/bookings_repository.dart';
import '../card/card_repository.dart';
import '../insights/demo_dialog.dart';
import '../insights/insights_repository.dart';
import '../loans/loans_repository.dart';
import '../notifications/notifications_repository.dart';
import '../savings/plans_repository.dart';

/// Profil (/F030/): Daten ansehen und ändern, Passwort ändern, abmelden.
class ProfileScreen extends ConsumerWidget {
  const ProfileScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final user = ref.watch(authControllerProvider).valueOrNull;
    if (user == null) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    return Scaffold(
      appBar: AppBar(title: const Text('Profil')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Center(
            child: CircleAvatar(
              radius: 36,
              child: Text(
                '${user.firstName.isNotEmpty ? user.firstName[0] : ''}${user.lastName.isNotEmpty ? user.lastName[0] : ''}',
                style: const TextStyle(fontSize: 24),
              ),
            ),
          ),
          const SizedBox(height: 12),
          Center(
              child: Text(user.fullName,
                  style: Theme.of(context).textTheme.titleLarge)),
          const SizedBox(height: 24),
          Card(
            child: Column(children: [
              ListTile(
                  leading: const Icon(Icons.mail),
                  title: const Text('E-Mail'),
                  subtitle: Text(user.email)),
              ListTile(
                  leading: const Icon(Icons.phone),
                  title: const Text('Telefon'),
                  subtitle: Text(user.phone)),
              ListTile(
                  leading: const Icon(Icons.cake),
                  title: const Text('Geburtsdatum'),
                  subtitle: Text(toGermanDate(user.birthDate))),
            ]),
          ),
          const SizedBox(height: 12),
          Card(
            child: Column(children: [
              ListTile(
                leading: const Icon(Icons.contacts),
                title: const Text('Adressbuch'),
                onTap: () => context.push('/adressbuch'),
              ),
              ListTile(
                leading: const Icon(Icons.edit),
                title: const Text('Daten ändern'),
                onTap: () => showDialog<void>(
                    context: context,
                    builder: (_) => _EditProfileDialog(user: user)),
              ),
              ListTile(
                leading: const Icon(Icons.password),
                title: const Text('Passwort ändern'),
                onTap: () => showDialog<void>(
                    context: context,
                    builder: (_) => const _ChangePasswordDialog()),
              ),
            ]),
          ),
          const SizedBox(height: 12),
          _DemoSection(isAdmin: user.isAdmin),
          const SizedBox(height: 24),
          OutlinedButton.icon(
            icon: const Icon(Icons.logout),
            label: const Text('Abmelden'),
            // Nach dem Abmelden leitet der Router automatisch zur Login-Seite um.
            onPressed: () => ref.read(authControllerProvider.notifier).logout(),
          ),
        ],
      ),
    );
  }
}

class _EditProfileDialog extends ConsumerStatefulWidget {
  const _EditProfileDialog({required this.user});

  final AppUser user;

  @override
  ConsumerState<_EditProfileDialog> createState() => _EditProfileDialogState();
}

class _EditProfileDialogState extends ConsumerState<_EditProfileDialog> {
  final _formKey = GlobalKey<FormState>();
  late final _firstName = TextEditingController(text: widget.user.firstName);
  late final _lastName = TextEditingController(text: widget.user.lastName);
  late final _phone = TextEditingController(text: widget.user.phone);
  bool _saving = false;
  String? _error;

  @override
  void dispose() {
    _firstName.dispose();
    _lastName.dispose();
    _phone.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      final updated = await ref.read(authRepositoryProvider).updateProfile(
            firstName: _firstName.text,
            lastName: _lastName.text,
            phone: _phone.text,
          );
      ref.read(authControllerProvider.notifier).updateUser(updated);
      if (mounted) Navigator.of(context).pop();
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
      title: const Text('Daten ändern'),
      content: Form(
        key: _formKey,
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          TextFormField(
              controller: _firstName,
              decoration: const InputDecoration(labelText: 'Vorname'),
              validator: (v) => validateRequired(v, 'Vorname')),
          TextFormField(
              controller: _lastName,
              decoration: const InputDecoration(labelText: 'Nachname'),
              validator: (v) => validateRequired(v, 'Nachname')),
          TextFormField(
              controller: _phone,
              keyboardType: TextInputType.phone,
              decoration: const InputDecoration(labelText: 'Telefon'),
              validator: validatePhone),
          if (_error != null)
            Padding(
              padding: const EdgeInsets.only(top: 12),
              child: Text(_error!,
                  style: TextStyle(color: Theme.of(context).colorScheme.error)),
            ),
        ]),
      ),
      actions: [
        TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Abbrechen')),
        FilledButton(
            onPressed: _saving ? null : _save, child: const Text('Speichern')),
      ],
    );
  }
}

class _ChangePasswordDialog extends ConsumerStatefulWidget {
  const _ChangePasswordDialog();

  @override
  ConsumerState<_ChangePasswordDialog> createState() =>
      _ChangePasswordDialogState();
}

class _ChangePasswordDialogState extends ConsumerState<_ChangePasswordDialog> {
  final _formKey = GlobalKey<FormState>();
  final _old = TextEditingController();
  final _new = TextEditingController();
  final _new2 = TextEditingController();
  bool _saving = false;
  String? _error;

  @override
  void dispose() {
    _old.dispose();
    _new.dispose();
    _new2.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      await ref.read(authRepositoryProvider).changePassword(_old.text, _new.text);
      if (!mounted) return;
      final messenger = ScaffoldMessenger.of(context);
      Navigator.of(context).pop();
      messenger.showSnackBar(const SnackBar(content: Text('Passwort geändert')));
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
      title: const Text('Passwort ändern'),
      content: Form(
        key: _formKey,
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          TextFormField(
              controller: _old,
              obscureText: true,
              decoration: const InputDecoration(labelText: 'Altes Passwort'),
              validator: (v) => validateRequired(v, 'Altes Passwort')),
          TextFormField(
              controller: _new,
              obscureText: true,
              decoration: const InputDecoration(labelText: 'Neues Passwort'),
              validator: validateNewPassword),
          TextFormField(
              controller: _new2,
              obscureText: true,
              decoration:
                  const InputDecoration(labelText: 'Neues Passwort wiederholen'),
              validator: (v) =>
                  v != _new.text ? 'Passwörter stimmen nicht überein' : null),
          if (_error != null)
            Padding(
              padding: const EdgeInsets.only(top: 12),
              child: Text(_error!,
                  style: TextStyle(color: Theme.of(context).colorScheme.error)),
            ),
        ]),
      ),
      actions: [
        TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Abbrechen')),
        FilledButton(
            onPressed: _saving ? null : _save, child: const Text('Speichern')),
      ],
    );
  }
}

/// Demo-Modus (/F590/, /F950/): Simulationsdatum, Beispielmonate, für Admin: Zeit vorspulen.
class _DemoSection extends ConsumerStatefulWidget {
  const _DemoSection({required this.isAdmin});

  final bool isAdmin;

  @override
  ConsumerState<_DemoSection> createState() => _DemoSectionState();
}

class _DemoSectionState extends ConsumerState<_DemoSection> {
  bool _busy = false;

  void _refreshEverything() {
    for (final p in <ProviderOrFamily>[
      clockProvider,
      accountsProvider,
      netWorthProvider,
      overviewProvider,
      monthsProvider,
      plansProvider,
      loansProvider,
      cardProvider,
      unreadCountProvider,
      notificationsProvider,
    ]) {
      ref.invalidate(p);
    }
    ref.invalidate(recentBookingsProvider);
  }

  Future<void> _advance({int? days, int? months}) async {
    setState(() => _busy = true);
    try {
      final date = await ref.read(insightsRepositoryProvider).advance(days: days, months: months);
      _refreshEverything();
      if (mounted) {
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text('Neues Simulationsdatum: ${toGermanDate(date)}')));
      }
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(apiErrorMessage(e))));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final clock = ref.watch(clockProvider);
    final overview = ref.watch(overviewProvider).valueOrNull;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(8),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          ListTile(
            leading: const Icon(Icons.science),
            title: const Text('Demo-Modus'),
            subtitle: Text(clock.maybeWhen(
              data: (d) => 'Simulationsdatum: ${toGermanDate(d)}',
              orElse: () => 'Simulationsdatum wird geladen …',
            )),
          ),
          ListTile(
            leading: const Icon(Icons.auto_awesome),
            title: const Text('Beispielmonate erzeugen'),
            subtitle: Text(overview?.demoProfile != null
                ? 'Bereits erzeugt (${overview!.demoProfile})'
                : '6 Monate Einnahmen und Ausgaben zum Ausprobieren'),
            enabled: overview?.demoProfile == null && !_busy,
            onTap: () async {
              if (await showDemoDialog(context)) _refreshEverything();
            },
          ),
          if (widget.isAdmin) ...[
            const Divider(),
            const Padding(
              padding: EdgeInsets.fromLTRB(16, 4, 16, 8),
              child: Text('Zeit vorspulen (nur Administrator). Dabei laufen für jeden Tag Gehälter, '
                  'Sparraten und Kreditraten wie in echt.'),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Wrap(spacing: 8, runSpacing: 8, children: [
                OutlinedButton(onPressed: _busy ? null : () => _advance(days: 1), child: const Text('+1 Tag')),
                OutlinedButton(onPressed: _busy ? null : () => _advance(months: 1), child: const Text('+1 Monat')),
                OutlinedButton(onPressed: _busy ? null : () => _advance(months: 3), child: const Text('+3 Monate')),
                OutlinedButton(onPressed: _busy ? null : () => _advance(months: 12), child: const Text('+12 Monate')),
              ]),
            ),
            if (_busy)
              const Padding(padding: EdgeInsets.all(12), child: LinearProgressIndicator()),
            const SizedBox(height: 8),
          ],
        ]),
      ),
    );
  }
}
