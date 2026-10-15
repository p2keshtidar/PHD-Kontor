import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/api_error.dart';
import '../../core/dates.dart';
import '../../core/ids.dart';
import '../../core/layout.dart';
import '../../core/money.dart';
import '../../core/text_dialog.dart';
import '../../l10n/l10n.dart';
import 'groups_repository.dart';
import 'groups_screen.dart';

/// Eine Gruppe: Salden, Vorschläge zum Ausgleichen, Ausgaben, erledigte Ausgleiche.
class GroupScreen extends ConsumerWidget {
  const GroupScreen({super.key, required this.groupId});

  final int groupId;

  Future<bool> _run(BuildContext context, WidgetRef ref, Future<void> Function() action, [String? done]) async {
    try {
      await action();
      ref.invalidate(groupDetailProvider(groupId));
      ref.invalidate(groupsProvider);
      if (done != null && context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(done)));
      }
      return true;
    } catch (e) {
      if (context.mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(apiErrorMessage(e))));
      return false;
    }
  }

  Future<bool> _confirm(BuildContext context, String title, String text, String action, Key key) async =>
      await showDialog<bool>(
        context: context,
        builder: (c) => AlertDialog(
          title: Text(title),
          content: Text(text),
          actions: [
            TextButton(onPressed: () => Navigator.pop(c, false), child: Text(tr('Abbrechen'))),
            FilledButton(key: key, onPressed: () => Navigator.pop(c, true), child: Text(action)),
          ],
        ),
      ) ??
      false;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final data = ref.watch(groupDetailProvider(groupId));
    final repo = ref.read(groupsRepositoryProvider);
    final theme = Theme.of(context);
    return data.when(
      loading: () => Scaffold(appBar: AppBar(), body: const Center(child: CircularProgressIndicator())),
      error: (e, _) => Scaffold(appBar: AppBar(), body: Center(child: Text(apiErrorMessage(e)))),
      data: (g) => Scaffold(
        appBar: AppBar(
          title: Text(g.name),
          actions: [
            IconButton(
              key: const Key('group_add_member'),
              tooltip: tr('Person hinzufügen'),
              icon: const Icon(Icons.person_add_alt_1),
              onPressed: () async {
                final r = await askText(context,
                    title: tr('Person hinzufügen'),
                    label: tr('E-Mail, Telefon oder IBAN'),
                    maxLength: 100,
                    fieldKey: const Key('group_member_field'),
                    saveKey: const Key('group_member_save'));
                if (r != null && r.trim().isNotEmpty && context.mounted) {
                  await _run(context, ref, () => repo.addMember(g.id, r.trim()), tr('Hinzugefügt'));
                }
              },
            ),
            PopupMenuButton<String>(
              key: const Key('group_menu'),
              onSelected: (v) async {
                if (v == 'rename') {
                  final n = await askText(context, title: tr('Umbenennen'), initial: g.name, maxLength: 60);
                  if (n != null && n.trim().isNotEmpty && context.mounted) {
                    await _run(context, ref, () => repo.rename(g.id, n.trim()));
                  }
                } else if (v == 'delete' || v == 'leave') {
                  final ok = await _confirm(
                      context,
                      v == 'delete' ? tr('Gruppe löschen?') : tr('Gruppe verlassen?'),
                      tr('Das geht nur, wenn alles ausgeglichen ist.'),
                      v == 'delete' ? tr('Löschen') : tr('Verlassen'),
                      const Key('group_confirm'));
                  if (!ok || !context.mounted) return;
                  final done = await _run(context, ref,
                      () => v == 'delete' ? repo.delete(g.id) : repo.removeMember(g.id, g.me.userId));
                  if (done && context.mounted) context.pop();
                }
              },
              itemBuilder: (_) => [
                if (g.owner) PopupMenuItem(value: 'rename', child: Text(tr('Umbenennen'))),
                if (g.owner)
                  PopupMenuItem(key: const Key('group_delete'), value: 'delete', child: Text(tr('Gruppe löschen')))
                else
                  PopupMenuItem(key: const Key('group_leave'), value: 'leave', child: Text(tr('Gruppe verlassen'))),
              ],
            ),
          ],
        ),
        floatingActionButton: FloatingActionButton.extended(
          key: const Key('group_add_expense'),
          onPressed: () async {
            final input = await showDialog<_ExpenseInput>(
                context: context, builder: (_) => _ExpenseDialog(members: g.members));
            if (input != null && context.mounted) {
              await _run(
                  context,
                  ref,
                  () => repo.addExpense(g.id,
                      description: input.description,
                      amountCents: input.amountCents,
                      payerId: input.payerId,
                      participants: input.participants),
                  tr('Ausgabe eingetragen'));
            }
          },
          icon: const Icon(Icons.add),
          label: Text(tr('Ausgabe')),
        ),
        body: RefreshIndicator(
          onRefresh: () async => ref.invalidate(groupDetailProvider(groupId)),
          child: ListView(padding: safeListPadding(context), children: [
            Semantics(
              container: true,
              label: balanceText(g.myBalanceCents),
              child: Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text(balanceText(g.myBalanceCents),
                        key: const Key('group_my_balance'),
                        style: theme.textTheme.titleLarge?.copyWith(color: balanceColor(g.myBalanceCents))),
                    Text(tr('Ausgaben insgesamt: {0}', [formatCents(g.totalSpentCents)]),
                        style: theme.textTheme.bodySmall),
                  ]),
                ),
              ),
            ),
            const SizedBox(height: 12),
            Text(tr('Salden'), style: theme.textTheme.titleMedium),
            for (final m in g.members)
              ListTile(
                key: Key('group_member_${m.userId}'),
                dense: true,
                contentPadding: EdgeInsets.zero,
                leading: Icon(m.owner ? Icons.star_outline : Icons.person_outline),
                title: Text(m.me ? tr('{0} (du)', [m.name]) : m.name),
                trailing: Text(formatCents(m.balanceCents),
                    style: TextStyle(color: balanceColor(m.balanceCents), fontWeight: FontWeight.w600)),
              ),
            const SizedBox(height: 12),
            Text(tr('So wird ausgeglichen'), style: theme.textTheme.titleMedium),
            if (g.suggestions.isEmpty)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 8),
                child: Text(tr('Niemand schuldet etwas.'), key: const Key('group_settled')),
              ),
            for (final s in g.suggestions)
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(12),
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text(tr('{0} → {1}: {2}', [s.fromName, s.toName, formatCents(s.amountCents)]),
                        key: Key('group_suggestion_${s.fromUserId}_${s.toUserId}')),
                    if (s.fromUserId == g.me.userId)
                      Align(
                        alignment: Alignment.centerRight,
                        child: FilledButton.tonal(
                          key: Key('group_settle_${s.toUserId}'),
                          onPressed: () async {
                            final key = newIdempotencyKey();
                            final ok = await _confirm(
                                context,
                                tr('Ausgleichen'),
                                tr('{0} an {1} überweisen? Das ist eine Überweisung der Projektbank (Spielgeld).',
                                    [formatCents(s.amountCents), s.toName]),
                                tr('Überweisen'),
                                const Key('group_settle_confirm'));
                            if (ok && context.mounted) {
                              await _run(context, ref, () => repo.settle(g.id, s.toUserId, s.amountCents, key),
                                  tr('Ausgeglichen'));
                            }
                          },
                          child: Text(tr('Überweisen')),
                        ),
                      ),
                    if (s.toUserId == g.me.userId)
                      Align(
                        alignment: Alignment.centerRight,
                        child: TextButton(
                          key: Key('group_cash_${s.fromUserId}'),
                          onPressed: () async {
                            final ok = await _confirm(
                                context,
                                tr('Barzahlung bestätigen'),
                                tr('Hast du {0} von {1} bar erhalten?', [formatCents(s.amountCents), s.fromName]),
                                tr('Erhalten'),
                                const Key('group_cash_confirm'));
                            if (ok && context.mounted) {
                              await _run(context, ref, () => repo.confirmCash(g.id, s.fromUserId, s.amountCents),
                                  tr('Ausgeglichen'));
                            }
                          },
                          child: Text(tr('Bar erhalten')),
                        ),
                      ),
                  ]),
                ),
              ),
            const SizedBox(height: 12),
            Text(tr('Ausgaben'), style: theme.textTheme.titleMedium),
            if (g.expenses.isEmpty)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 8),
                child: Text(tr('Noch keine Ausgaben.'), key: const Key('group_no_expenses')),
              ),
            for (final e in g.expenses)
              ListTile(
                key: Key('group_expense_${e.id}'),
                contentPadding: EdgeInsets.zero,
                title: Text(e.description),
                subtitle: Text(tr('{0} hat bezahlt · {1} · dein Anteil {2}',
                    [e.payerName, formatDate(e.date), formatCents(e.myShareCents)])),
                trailing: Row(mainAxisSize: MainAxisSize.min, children: [
                  Text(formatCents(e.amountCents)),
                  if (e.deletable)
                    IconButton(
                      key: Key('group_expense_delete_${e.id}'),
                      tooltip: tr('Löschen'),
                      icon: const Icon(Icons.delete_outline),
                      onPressed: () => _run(context, ref, () => repo.deleteExpense(g.id, e.id)),
                    ),
                ]),
              ),
            if (g.settlements.isNotEmpty) ...[
              const SizedBox(height: 12),
              Text(tr('Ausgleiche'), style: theme.textTheme.titleMedium),
              for (final s in g.settlements)
                ListTile(
                  dense: true,
                  contentPadding: EdgeInsets.zero,
                  leading: const Icon(Icons.check_circle_outline),
                  title: Text(tr('{0} → {1}: {2}', [s.fromName, s.toName, formatCents(s.amountCents)])),
                  subtitle: Text('${s.methodLabel} · ${formatDate(s.createdAt)}'),
                ),
            ],
            const SizedBox(height: 12),
            Text(g.note, style: theme.textTheme.bodySmall),
            const SizedBox(height: 80),
          ]),
        ),
      ),
    );
  }
}

class _ExpenseInput {
  const _ExpenseInput(this.description, this.amountCents, this.payerId, this.participants);

  final String description;
  final int amountCents;
  final int payerId;
  final List<int> participants;
}

/// Neue Ausgabe: Beschreibung, Betrag, wer bezahlt hat, wer beteiligt ist (gleichmäßig aufgeteilt).
class _ExpenseDialog extends StatefulWidget {
  const _ExpenseDialog({required this.members});

  final List<GroupMember> members;

  @override
  State<_ExpenseDialog> createState() => _ExpenseDialogState();
}

class _ExpenseDialogState extends State<_ExpenseDialog> {
  final _description = TextEditingController();
  final _amount = TextEditingController();
  late int _payer = widget.members.firstWhere((m) => m.me).userId;
  late final Set<int> _participants = {for (final m in widget.members) m.userId};
  String? _error;

  @override
  void dispose() {
    _description.dispose();
    _amount.dispose();
    super.dispose();
  }

  void _save() {
    final cents = parseEuroToCents(_amount.text);
    if (_description.text.trim().isEmpty) {
      setState(() => _error = tr('Bitte eine Beschreibung angeben.'));
    } else if (cents == null || cents <= 0) {
      setState(() => _error = tr('Bitte einen gültigen Betrag eingeben.'));
    } else if (_participants.isEmpty) {
      setState(() => _error = tr('Mindestens eine Person muss beteiligt sein.'));
    } else {
      Navigator.pop(context, _ExpenseInput(_description.text.trim(), cents, _payer, _participants.toList()..sort()));
    }
  }

  @override
  Widget build(BuildContext context) {
    final cents = parseEuroToCents(_amount.text) ?? 0;
    final each = _participants.isEmpty ? 0 : cents ~/ _participants.length;
    return AlertDialog(
      title: Text(tr('Neue Ausgabe')),
      content: SingleChildScrollView(
        child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
          TextField(
            key: const Key('expense_description'),
            controller: _description,
            maxLength: 100,
            decoration: InputDecoration(labelText: tr('Wofür?')),
          ),
          TextField(
            key: const Key('expense_amount'),
            controller: _amount,
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
            decoration: InputDecoration(labelText: tr('Betrag in €')),
            onChanged: (_) => setState(() {}),
          ),
          const SizedBox(height: 12),
          DropdownButtonFormField<int>(
            key: const Key('expense_payer'),
            value: _payer,
            isExpanded: true,
            decoration: InputDecoration(labelText: tr('Bezahlt von')),
            items: [
              for (final m in widget.members)
                DropdownMenuItem(value: m.userId, child: Text(m.me ? tr('{0} (du)', [m.name]) : m.name)),
            ],
            onChanged: (v) => setState(() => _payer = v ?? _payer),
          ),
          const SizedBox(height: 12),
          Text(tr('Aufteilen auf')),
          for (final m in widget.members)
            CheckboxListTile(
              key: Key('expense_participant_${m.userId}'),
              dense: true,
              contentPadding: EdgeInsets.zero,
              value: _participants.contains(m.userId),
              title: Text(m.me ? tr('{0} (du)', [m.name]) : m.name),
              onChanged: (v) => setState(() => v == true ? _participants.add(m.userId) : _participants.remove(m.userId)),
            ),
          if (cents > 0 && _participants.isNotEmpty)
            Text(tr('Je Person etwa {0}', [formatCents(each)]), key: const Key('expense_each')),
          if (_error != null)
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
            ),
        ]),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: Text(tr('Abbrechen'))),
        FilledButton(key: const Key('expense_save'), onPressed: _save, child: Text(tr('Eintragen'))),
      ],
    );
  }
}
