import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_error.dart';
import '../../core/dates.dart';
import '../../core/money.dart';
import '../../l10n/l10n.dart';
import '../accounts/account.dart';
import 'allocation_dialogs.dart';
import 'allocation_models.dart';
import 'allocations_repository.dart';

/// "Automatisch sparen" im digitalen Konto: Sparregeln, anstehende Termine, Vorgänge mit Status, Vormerkungen
/// aus verbundenen Bankkonten und eine Prognose für das Ziel.
class AllocationSection extends ConsumerStatefulWidget {
  const AllocationSection({super.key, required this.account, required this.onChanged});

  final Account account;

  /// Kontostände neu laden (nach Umbuchungen)
  final VoidCallback onChanged;

  @override
  ConsumerState<AllocationSection> createState() => _AllocationSectionState();
}

class _AllocationSectionState extends ConsumerState<AllocationSection> {
  bool _busy = false;

  int get _id => widget.account.id;

  void _reload() {
    ref.invalidate(accountAllocationsProvider(_id));
    ref.invalidate(allocationSourcesProvider);
    widget.onChanged();
  }

  Future<void> _run(Future<void> Function() action, String success) async {
    setState(() => _busy = true);
    try {
      await action();
      _reload();
      if (mounted) {
        ScaffoldMessenger.of(context)
          ..removeCurrentSnackBar()
          ..showSnackBar(SnackBar(content: Text(success)));
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context)
          ..removeCurrentSnackBar()
          ..showSnackBar(SnackBar(content: Text(apiErrorMessage(e))));
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  /// Vorgang jetzt erneut versuchen; das Ergebnis (ausgeführt oder Grund) wird angezeigt.
  Future<void> _retry(AllocationRun r) async {
    setState(() => _busy = true);
    String message;
    try {
      final result = await ref.read(allocationsRepositoryProvider).retry(r.id);
      message = result.completed
          ? tr('Ausgeführt: {0}', [formatCents(result.amountCents)])
          : tr('Nicht ausgeführt: {0}', [result.failureReason ?? result.statusLabel]);
      _reload();
    } catch (e) {
      message = apiErrorMessage(e);
    }
    if (!mounted) return;
    setState(() => _busy = false);
    ScaffoldMessenger.of(context)
      ..removeCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  Future<void> _newRule() async {
    final saved = await showDialog<bool>(
      context: context,
      builder: (_) => AllocationRuleDialog(accountId: _id, accountName: widget.account.name),
    );
    if (saved == true) {
      _reload();
      if (mounted) {
        ScaffoldMessenger.of(context)
          ..removeCurrentSnackBar()
          ..showSnackBar(SnackBar(content: Text(tr('Sparregel eingerichtet'))));
      }
    }
  }

  Future<void> _earmark() async {
    final saved = await showDialog<bool>(
      context: context,
      builder: (_) => EarmarkDialog(accountId: _id, accountName: widget.account.name),
    );
    if (saved == true) {
      _reload();
      if (mounted) {
        ScaffoldMessenger.of(context)
          ..removeCurrentSnackBar()
          ..showSnackBar(SnackBar(content: Text(tr('Vorgemerkt – das Geld bleibt bei deiner Bank'))));
      }
    }
  }

  Future<void> _ruleAction(AllocationRule r, String action) async {
    final repo = ref.read(allocationsRepositoryProvider);
    switch (action) {
      case 'PAUSE':
        await _run(() => repo.pause(r.id), tr('Sparregel pausiert'));
      case 'RESUME':
        await _run(() => repo.resume(r.id), tr('Sparregel fortgesetzt'));
      case 'AMOUNT':
        final cents = await askRuleAmount(context, r.amountCents);
        if (cents != null) await _run(() => repo.updateRule(r.id, amountCents: cents), tr('Betrag geändert'));
      case 'END':
        final ok = await showDialog<bool>(
          context: context,
          builder: (ctx) => AlertDialog(
            title: Text(tr('Sparregel beenden?')),
            content: Text(tr('Es wird nichts mehr automatisch zugewiesen. Bisherige Umbuchungen und Vormerkungen bleiben.')),
            actions: [
              TextButton(onPressed: () => Navigator.pop(ctx, false), child: Text(tr('Abbrechen'))),
              FilledButton(
                  key: const Key('alloc_end_confirm'),
                  onPressed: () => Navigator.pop(ctx, true),
                  child: Text(tr('Beenden'))),
            ],
          ),
        );
        if (ok == true) await _run(() => repo.end(r.id), tr('Sparregel beendet'));
      default:
        final interval = action.substring('INTERVAL_'.length);
        await _run(() => repo.updateRule(r.id, interval: interval), tr('Rhythmus geändert'));
    }
  }

  @override
  Widget build(BuildContext context) {
    final data = ref.watch(accountAllocationsProvider(_id));
    final theme = Theme.of(context);
    return Card(
      key: const Key('alloc_section'),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [
            Icon(Icons.autorenew, size: 20, color: theme.colorScheme.primary),
            const SizedBox(width: 8),
            Expanded(child: Text(tr('Automatisch sparen'), style: theme.textTheme.titleSmall)),
          ]),
          const SizedBox(height: 4),
          data.when(
            loading: () => const Padding(padding: EdgeInsets.all(12), child: LinearProgressIndicator()),
            error: (e, _) => Text(apiErrorMessage(e)),
            data: (a) => _content(a, theme),
          ),
        ]),
      ),
    );
  }

  Widget _content(AccountAllocations a, ThemeData theme) {
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      if (a.rules.isEmpty)
        Padding(
          padding: const EdgeInsets.symmetric(vertical: 4),
          child: Text(tr('Lege eine Sparregel an: Ein fester Betrag wird regelmäßig zugewiesen – wöchentlich, alle 2 Wochen, monatlich oder vierteljährlich.'),
              style: theme.textTheme.bodySmall),
        ),
      for (final r in a.rules) _ruleTile(r, theme),
      if (a.otherRules > 0)
        Padding(
          padding: const EdgeInsets.only(top: 4),
          child: Text(tr('Weitere Sparregeln anderer Mitglieder: {0}', [a.otherRules]), style: theme.textTheme.bodySmall),
        ),
      if (a.projectionText != null && a.projectionText!.isNotEmpty)
        Padding(
          padding: const EdgeInsets.only(top: 8),
          child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Icon(Icons.timeline, size: 18, color: theme.colorScheme.outline),
            const SizedBox(width: 8),
            Expanded(child: Text(a.projectionText!, key: const Key('alloc_projection'), style: theme.textTheme.bodySmall)),
          ]),
        ),
      if (a.earmarkedCents > 0 || a.earmarks.isNotEmpty) ..._earmarks(a, theme),
      if (a.upcoming.isNotEmpty) ...[
        const Divider(height: 20),
        Text(tr('Ausstehend'), style: theme.textTheme.labelLarge),
        for (final u in a.upcoming.take(3))
          ListTile(
            dense: true,
            contentPadding: EdgeInsets.zero,
            leading: Icon(u.movesMoney ? Icons.schedule : Icons.bookmark_border, size: 20),
            title: Text(tr('{0} am {1}', [formatCents(u.amountCents), formatDate(u.date)])),
            subtitle: Text(u.movesMoney ? tr('Umbuchung vom Girokonto') : tr('Vormerkung bei {0}', [u.sourceName ?? ''])),
          ),
      ],
      if (a.runs.isNotEmpty) ...[
        const Divider(height: 20),
        Text(tr('Vorgänge'), style: theme.textTheme.labelLarge),
        for (final r in a.runs.take(5)) _runTile(r, theme),
      ],
      if (a.canAllocate) ...[
        const SizedBox(height: 8),
        Wrap(spacing: 8, runSpacing: 8, children: [
          OutlinedButton.icon(
            key: const Key('alloc_new'),
            onPressed: _busy ? null : _newRule,
            icon: const Icon(Icons.add),
            label: Text(tr('Sparregel anlegen')),
          ),
          OutlinedButton.icon(
            key: const Key('alloc_earmark'),
            onPressed: _busy ? null : _earmark,
            icon: const Icon(Icons.bookmark_add_outlined),
            label: Text(tr('Vormerken')),
          ),
        ]),
      ],
    ]);
  }

  List<Widget> _earmarks(AccountAllocations a, ThemeData theme) => [
        const Divider(height: 20),
        Text(tr('Vorgemerkt: {0}', [formatCents(a.earmarkedCents)]),
            key: const Key('alloc_earmarked'), style: theme.textTheme.labelLarge),
        for (final e in a.earmarks)
          ListTile(
            dense: true,
            contentPadding: EdgeInsets.zero,
            leading: Icon(e.active ? Icons.bookmark : Icons.bookmark_remove_outlined, size: 20),
            title: Text(e.bankName == null ? e.sourceName : '${e.sourceName} · ${e.bankName}'),
            subtitle: Text(e.active
                ? tr('{0} – das Geld bleibt dort', [formatCents(e.amountCents)])
                : tr('{0} – Verbindung nicht aktiv, Deckung ungeprüft', [formatCents(e.amountCents)])),
            trailing: e.own && e.externalAccountId != null
                ? TextButton(
                    key: Key('alloc_release_${e.externalAccountId}'),
                    onPressed: _busy
                        ? null
                        : () => _run(() => ref.read(allocationsRepositoryProvider).release(_id, e.externalAccountId!),
                            tr('Vormerkung aufgehoben')),
                    child: Text(tr('Aufheben')),
                  )
                : null,
          ),
        if (a.earmarkExplanation.isNotEmpty) Text(a.earmarkExplanation, style: theme.textTheme.bodySmall),
      ];

  Widget _ruleTile(AllocationRule r, ThemeData theme) {
    final lines = <String>[
      r.sourceLabel,
      if (r.active && r.nextDate != null) tr('Nächste Ausführung: {0}', [formatDate(r.nextDate!)]),
      if (!r.active) r.statusReason == null ? r.statusLabel : '${r.statusLabel}: ${r.statusReason}',
      if (!r.movesMoney) tr('Nur Vormerkung – das Geld bleibt bei der Bank'),
    ];
    return ListTile(
      key: Key('alloc_rule_${r.id}'),
      contentPadding: EdgeInsets.zero,
      leading: Icon(r.paused ? Icons.pause_circle_outline : (r.movesMoney ? Icons.sync_alt : Icons.bookmark_border)),
      title: Text('${formatCents(r.amountCents)} · ${r.rhythm}'),
      subtitle: Text(lines.join('\n')),
      isThreeLine: lines.length > 1,
      trailing: PopupMenuButton<String>(
        key: Key('alloc_menu_${r.id}'),
        tooltip: tr('Sparregel ändern'),
        enabled: !_busy,
        onSelected: (v) => _ruleAction(r, v),
        itemBuilder: (_) => [
          if (r.active) PopupMenuItem(value: 'PAUSE', child: Text(tr('Pausieren'))),
          if (r.paused) PopupMenuItem(value: 'RESUME', child: Text(tr('Fortsetzen'))),
          PopupMenuItem(value: 'AMOUNT', child: Text(tr('Betrag ändern'))),
          for (final e in allocationIntervals.entries)
            if (e.key != r.interval) PopupMenuItem(value: 'INTERVAL_${e.key}', child: Text(tr('Rhythmus: {0}', [e.value]))),
          const PopupMenuDivider(),
          PopupMenuItem(value: 'END', child: Text(tr('Beenden'))),
        ],
      ),
    );
  }

  Widget _runTile(AllocationRun r, ThemeData theme) {
    final (IconData icon, Color color) = switch (r.status) {
      'COMPLETED' => (Icons.check_circle, Colors.green.shade700),
      'FAILED' => (Icons.error, theme.colorScheme.error),
      'CANCELLED' => (Icons.block, theme.colorScheme.outline),
      _ => (Icons.schedule, Colors.orange.shade800),
    };
    return ListTile(
      key: Key('alloc_run_${r.id}'),
      dense: true,
      contentPadding: EdgeInsets.zero,
      leading: Icon(icon, color: color, size: 20),
      title: Text('${formatCents(r.amountCents)} · ${r.kindLabel}'),
      subtitle: Text([
        '${formatDate(r.dueDate)} · ${r.statusLabel}',
        if (r.failureReason != null && !r.completed) r.failureReason!,
      ].join('\n')),
      trailing: r.canRetry
          ? IconButton(
              key: Key('alloc_retry_${r.id}'),
              tooltip: tr('Erneut versuchen'),
              onPressed: _busy ? null : () => _retry(r),
              icon: const Icon(Icons.replay),
            )
          : null,
    );
  }
}
