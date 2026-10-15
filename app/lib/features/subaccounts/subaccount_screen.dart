import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../l10n/l10n.dart';

import '../../core/api_error.dart';
import '../../core/state_views.dart';
import '../../core/dates.dart';
import '../../core/money.dart';
import '../accounts/account.dart';
import '../accounts/accounts_repository.dart';
import '../accounts/booking_tile.dart';
import '../accounts/bookings_repository.dart';
import '../allocations/allocation_section.dart';
import 'subaccount_card_section.dart';
import 'subaccount_dialogs.dart';
import 'subaccount_icons.dart';
import 'subaccounts_repository.dart';
import '../../core/layout.dart';

/// Detail eines digitalen Kontos: Ziel und Fortschritt, einzahlen, auszahlen, teilen, bearbeiten, auflösen.
class SubaccountScreen extends ConsumerStatefulWidget {
  const SubaccountScreen({super.key, required this.accountId});

  final int accountId;

  @override
  ConsumerState<SubaccountScreen> createState() => _SubaccountScreenState();
}

class _SubaccountScreenState extends ConsumerState<SubaccountScreen> {
  bool _busy = false;

  void _refresh() {
    ref.invalidate(accountsProvider);
    ref.invalidate(recentBookingsProvider);
  }

  Future<void> _run(Future<void> Function() action, String success) async {
    setState(() => _busy = true);
    try {
      await action();
      _refresh();
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(success)));
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(apiErrorMessage(e))));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _deposit(Account main, Account sub) async {
    final cents = await askAmount(context, title: tr('Vom Girokonto einzahlen'), maxCents: main.balanceCents);
    if (cents == null) return;
    await _run(() => ref.read(subaccountsRepositoryProvider).move(main.id, sub.id, cents),
        tr('{0} eingezahlt', [formatCents(cents)]));
  }

  Future<void> _withdraw(Account main, Account sub) async {
    final cents = await askAmount(context, title: tr('Aufs Girokonto auszahlen'), maxCents: sub.balanceCents);
    if (cents == null) return;
    await _run(() => ref.read(subaccountsRepositoryProvider).move(sub.id, main.id, cents),
        tr('{0} ausgezahlt', [formatCents(cents)]));
  }

  /// /K060/: Person per E-Mail, Telefonnummer oder IBAN hinzufügen, mit Rolle
  Future<void> _addMember(Account sub) async {
    final result = await showDialog<(String, String)>(
      context: context,
      builder: (_) => _ShareDialog(name: sub.name),
    );
    if (result == null || result.$1.trim().isEmpty) return;
    await _run(() => ref.read(subaccountsRepositoryProvider).addMember(sub.id, result.$1, role: result.$2),
        tr('Geteilt'));
    ref.invalidate(subaccountMembersProvider(sub.id));
  }

  Future<void> _setRole(Account sub, SubaccountMember m, String role) async {
    await _run(() => ref.read(subaccountsRepositoryProvider).setRole(sub.id, m.userId, role), tr('Rechte geändert'));
    ref.invalidate(subaccountMembersProvider(sub.id));
  }

  Future<void> _removeMember(Account sub, SubaccountMember m, {required bool leave}) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(leave ? tr('Austreten?') : tr('{0} entfernen?', [m.name])),
        content: Text(leave
            ? tr('Du siehst „{0}“ danach nicht mehr. Das Guthaben bleibt im digitalen Konto.', [sub.name])
            : tr('{0} hat danach keinen Zugriff mehr auf „{1}“.', [m.name, sub.name])),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: Text(tr('Abbrechen'))),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: Text(leave ? tr('Austreten') : tr('Entfernen'))),
        ],
      ),
    );
    if (ok != true) return;
    await _run(() => ref.read(subaccountsRepositoryProvider).removeMember(sub.id, m.userId),
        leave ? tr('Ausgetreten') : tr('{0} entfernt', [m.name]));
    ref.invalidate(subaccountMembersProvider(sub.id));
    if (leave && mounted) context.pop();
  }

  Widget _sharing(Account sub, ThemeData theme) {
    final members = ref.watch(subaccountMembersProvider(sub.id));
    return Card(
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 8),
        child: members.when(
          loading: () => const Padding(padding: EdgeInsets.all(16), child: LinearProgressIndicator()),
          error: (e, _) => Padding(padding: const EdgeInsets.all(16), child: Text(apiErrorMessage(e))),
          data: (list) {
            final me = list.where((m) => m.you).toList();
            return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 4, 16, 4),
                child: Row(children: [
                  const Icon(Icons.group, size: 20),
                  const SizedBox(width: 8),
                  Text(list.length > 1 ? tr('Geteilt') : tr('Teilen'), style: theme.textTheme.titleSmall),
                ]),
              ),
              if (list.length == 1)
                Padding(
                  padding: EdgeInsets.symmetric(horizontal: 16, vertical: 4),
                  child: Text(tr('Teile dieses digitale Konto, z. B. als WG-Kasse oder gemeinsames Sparziel.')),
                ),
              if (list.length > 1)
                for (final m in list)
                  ListTile(
                    dense: true,
                    leading: CircleAvatar(radius: 14, child: Text(m.name.isEmpty ? '?' : m.name[0])),
                    title: Text(m.you ? tr('{0} (du)', [m.name]) : m.name),
                    subtitle: Text(digitalRoles[m.role] ?? m.role),
                    trailing: !sub.isForeign && !m.owner
                        ? PopupMenuButton<String>(
                            key: Key('member_menu_${m.userId}'),
                            tooltip: tr('Rechte ändern'),
                            enabled: !_busy,
                            onSelected: (v) =>
                                v == 'REMOVE' ? _removeMember(sub, m, leave: false) : _setRole(sub, m, v),
                            itemBuilder: (_) => [
                              for (final r in ['FULL', 'DEPOSIT', 'VIEW'])
                                CheckedPopupMenuItem(value: r, checked: m.role == r, child: Text(digitalRoles[r]!)),
                              const PopupMenuDivider(),
                              PopupMenuItem(value: 'REMOVE', child: Text(tr('Entfernen'))),
                            ],
                          )
                        : null,
                  ),
              if (!sub.isForeign)
                ListTile(
                  key: const Key('share_add'),
                  leading: const Icon(Icons.person_add_alt_1),
                  title: Text(tr('Person hinzufügen')),
                  onTap: _busy ? null : () => _addMember(sub),
                )
              else if (me.isNotEmpty)
                ListTile(
                  leading: Icon(Icons.logout, color: theme.colorScheme.error),
                  title: Text(tr('Austreten'), style: TextStyle(color: theme.colorScheme.error)),
                  onTap: _busy ? null : () => _removeMember(sub, me.first, leave: true),
                ),
            ]);
          },
        ),
      ),
    );
  }

  Future<void> _edit(Account sub) async {
    final saved = await showDialog<bool>(
      context: context,
      builder: (_) => SubaccountEditDialog(
        id: sub.id,
        initialName: sub.name,
        initialIcon: sub.icon ?? 'savings',
        initialPurpose: sub.digital?.purpose ?? 'SONSTIGES',
        initialTargetCents: sub.digital?.targetCents,
        initialTargetDate: sub.digital?.targetDate,
      ),
    );
    if (saved == true) _refresh();
  }

  Future<void> _close(Account sub) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(tr('„{0}“ auflösen?', [sub.name])),
        content: Text(sub.balanceCents > 0
            ? tr('Das Guthaben von {0} wird aufs Girokonto gebucht.', [formatCents(sub.balanceCents)])
            : tr('Das digitale Konto wird gelöscht.')),
        actions: [
          TextButton(onPressed: () => Navigator.of(ctx).pop(false), child: Text(tr('Abbrechen'))),
          FilledButton(onPressed: () => Navigator.of(ctx).pop(true), child: Text(tr('Auflösen'))),
        ],
      ),
    );
    if (ok != true) return;
    setState(() => _busy = true);
    try {
      await ref.read(subaccountsRepositoryProvider).close(sub.id);
      _refresh();
      if (mounted) context.pop();
    } catch (e) {
      if (mounted) {
        setState(() => _busy = false);
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(apiErrorMessage(e))));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final accounts = ref.watch(accountsProvider);
    return Scaffold(
      appBar: AppBar(title: Text(tr('Digitales Konto'))),
      body: accounts.when(
        loading: () => const LoadingView(),
        error: (e, _) => ErrorView(error: e, onRetry: () => ref.invalidate(accountsProvider)),
        data: (list) {
          final subs = list.where((a) => a.id == widget.accountId);
          final mains = list.where((a) => a.isMain);
          if (subs.isEmpty || mains.isEmpty) {
            return Center(child: Text(tr('Digitales Konto nicht gefunden.')));
          }
          final sub = subs.first;
          final main = mains.first;
          final recent = ref.watch(recentBookingsProvider(sub.id));
          final theme = Theme.of(context);
          return ListView(
            padding: safeListPadding(context),
            children: [
              const SizedBox(height: 8),
              Center(
                child: CircleAvatar(radius: 32, child: Icon(subaccountIcon(sub.icon), size: 32)),
              ),
              const SizedBox(height: 12),
              Center(child: Text(sub.name, style: theme.textTheme.titleLarge)),
              if (sub.isForeign)
                Center(child: Text(tr('Geteilt von {0}', [sub.ownerName]), style: theme.textTheme.bodySmall)),
              const SizedBox(height: 4),
              Center(
                child: Text(formatCents(sub.balanceCents),
                    style: theme.textTheme.headlineMedium?.copyWith(fontWeight: FontWeight.bold)),
              ),
              if (sub.digital != null) ...[
                const SizedBox(height: 12),
                DigitalGoalCard(account: sub),
              ],
              const SizedBox(height: 20),
              Row(children: [
                Expanded(
                  child: FilledButton.icon(
                    key: const Key('digital_deposit'),
                    onPressed: _busy || main.balanceCents <= 0 || !(sub.digital?.canDeposit ?? true)
                        ? null
                        : () => _deposit(main, sub),
                    icon: const Icon(Icons.add),
                    label: Text(tr('Einzahlen')),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: FilledButton.tonalIcon(
                    key: const Key('digital_withdraw'),
                    onPressed: _busy || sub.balanceCents <= 0 || !(sub.digital?.canWithdraw ?? true)
                        ? null
                        : () => _withdraw(main, sub),
                    icon: const Icon(Icons.remove),
                    label: Text(tr('Auszahlen')),
                  ),
                ),
              ]),
              const SizedBox(height: 8),
              Text(tr('Auf dem Girokonto verfügbar: {0}', [formatCents(main.balanceCents)]),
                  textAlign: TextAlign.center, style: theme.textTheme.bodySmall),
              if (sub.isForeign && sub.digital != null)
                Text(tr('Deine Rechte: {0}', [sub.digital!.roleLabel]),
                    textAlign: TextAlign.center, style: theme.textTheme.bodySmall),
              if (sub.digital != null) ...[
                const SizedBox(height: 16),
                AllocationSection(account: sub, onChanged: _refresh),
                const SizedBox(height: 8),
                SubaccountCardSection(accountId: sub.id, onChanged: _refresh),
              ],
              const SizedBox(height: 16),
              _sharing(sub, theme),
              const SizedBox(height: 8),
              Card(
                child: Column(children: [
                  if (!sub.isForeign)
                    ListTile(
                      leading: const Icon(Icons.edit),
                      title: Text(tr('Name, Zweck und Ziel ändern')),
                      onTap: _busy ? null : () => _edit(sub),
                    ),
                  ListTile(
                    leading: const Icon(Icons.receipt_long),
                    title: Text(tr('Alle Umsätze')),
                    onTap: () => context.push('/umsaetze/${sub.id}'),
                  ),
                  if (!sub.isForeign)
                    ListTile(
                      leading: Icon(Icons.delete_outline, color: theme.colorScheme.error),
                      title: Text(tr('Digitales Konto auflösen'), style: TextStyle(color: theme.colorScheme.error)),
                      onTap: _busy ? null : () => _close(sub),
                    ),
                ]),
              ),
              const SizedBox(height: 8),
              Text(tr('Letzte Bewegungen'), style: theme.textTheme.titleMedium),
              recent.when(
                loading: () => const Padding(
                  padding: EdgeInsets.all(16),
                  child: Center(child: CircularProgressIndicator()),
                ),
                error: (e, _) => Text(apiErrorMessage(e)),
                data: (items) => items.isEmpty
                    ? Padding(padding: EdgeInsets.all(16), child: Text(tr('Noch keine Bewegungen.')))
                    : Column(children: [
                        for (final b in items)
                          BookingTile(item: b, onTap: () => context.push('/umsatz', extra: b)),
                      ]),
              ),
            ],
          );
        },
      ),
    );
  }
}

/// Ziel, Fortschritt, Restbetrag und Hinweis, wie das Geld gehalten wird.
class DigitalGoalCard extends StatelessWidget {
  const DigitalGoalCard({super.key, required this.account});

  final Account account;

  @override
  Widget build(BuildContext context) {
    final d = account.digital!;
    final theme = Theme.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [
            Icon(subaccountIcon(digitalPurposes[d.purpose]?.$2), size: 18, color: theme.colorScheme.primary),
            const SizedBox(width: 6),
            Expanded(child: Text(d.purposeLabel, style: theme.textTheme.titleSmall)),
            if (d.goalReached) Icon(Icons.emoji_events, color: theme.colorScheme.tertiary),
          ]),
          if (d.hasTarget) ...[
            const SizedBox(height: 10),
            LinearProgressIndicator(
              key: const Key('digital_progress'),
              value: ((d.progressPercent ?? 0) / 100).clamp(0, 1).toDouble(),
              minHeight: 8,
              borderRadius: BorderRadius.circular(4),
            ),
            const SizedBox(height: 6),
            Text(
              d.goalReached
                  ? tr('Ziel von {0} erreicht', [formatCents(d.targetCents!)])
                  : tr('{0} % von {1} · noch {2}', [
                      (d.progressPercent ?? 0).toStringAsFixed(0),
                      formatCents(d.targetCents!),
                      formatCents(d.remainingCents ?? 0),
                    ]),
              key: const Key('digital_progress_text'),
            ),
            if (d.earmarkedCents > 0)
              Text(
                tr('Guthaben {0} + vorgemerkt {1}', [formatCents(account.balanceCents), formatCents(d.earmarkedCents)]),
                key: const Key('digital_saved'),
                style: theme.textTheme.bodySmall,
              ),
            if (d.targetDate != null)
              Text(
                d.monthlyNeededCents == null
                    ? tr('Zieldatum: {0}', [formatDate(d.targetDate!)])
                    : tr('Zieldatum: {0} · nötig ca. {1} pro Monat',
                        [formatDate(d.targetDate!), formatCents(d.monthlyNeededCents!)]),
                style: theme.textTheme.bodySmall,
              ),
          ] else
            Padding(
              padding: const EdgeInsets.only(top: 6),
              child: Text(tr('Kein Zielbetrag festgelegt.'), style: theme.textTheme.bodySmall),
            ),
          const Divider(height: 20),
          Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Icon(Icons.info_outline, size: 18, color: theme.colorScheme.outline),
            const SizedBox(width: 8),
            Expanded(
              child: Text(
                d.custodyText.isEmpty ? digitalAccountsExplanation : d.custodyText,
                key: const Key('digital_custody'),
                style: theme.textTheme.bodySmall,
              ),
            ),
          ]),
        ]),
      ),
    );
  }
}

/// Erklärung, was ein digitales Konto ist (wenn der Server keinen Text liefert).
String get digitalAccountsExplanation => tr(
    'Digitale Konten sind Töpfe in deinem Projektbank-Konto: Das Geld wird dorthin umgebucht. Sie sind keine eigenen Bankkonten und haben keine eigene Einlagensicherung.');

/// Person zum Teilen eintragen und Rechte wählen.
class _ShareDialog extends StatefulWidget {
  const _ShareDialog({required this.name});

  final String name;

  @override
  State<_ShareDialog> createState() => _ShareDialogState();
}

class _ShareDialogState extends State<_ShareDialog> {
  final _recipient = TextEditingController();
  String _role = 'FULL';

  @override
  void dispose() {
    _recipient.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
        title: Text(tr('Digitales Konto teilen')),
        content: SingleChildScrollView(
          child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
            TextField(
              key: const Key('share_recipient'),
              controller: _recipient,
              autofocus: true,
              decoration: InputDecoration(labelText: tr('E-Mail, Telefonnummer oder IBAN')),
            ),
            const SizedBox(height: 12),
            Text(tr('Was darf die Person in „{0}“?', [widget.name])),
            for (final r in ['FULL', 'DEPOSIT', 'VIEW'])
              RadioListTile<String>(
                key: Key('share_role_$r'),
                contentPadding: EdgeInsets.zero,
                dense: true,
                value: r,
                // ignore: deprecated_member_use
                groupValue: _role,
                // ignore: deprecated_member_use
                onChanged: (v) => setState(() => _role = v ?? 'FULL'),
                title: Text(digitalRoles[r]!),
              ),
          ]),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: Text(tr('Abbrechen'))),
          FilledButton(
            key: const Key('share_confirm'),
            onPressed: () => Navigator.pop(context, (_recipient.text, _role)),
            child: Text(tr('Teilen')),
          ),
        ],
      );
}
