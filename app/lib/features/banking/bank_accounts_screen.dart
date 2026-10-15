import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/api_error.dart';
import '../../core/dates.dart';
import '../../core/layout.dart';
import '../../core/money.dart';
import '../../l10n/l10n.dart';
import 'bank_consent.dart';
import 'bank_models.dart';
import 'bank_repository.dart';

/// Echte Bankkonten: alle verbundenen Banken und Konten, Abruf, Erneuern, Widerrufen.
class BankAccountsScreen extends ConsumerWidget {
  const BankAccountsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final connections = ref.watch(bankConnectionsProvider);
    final overview = ref.watch(bankOverviewProvider).valueOrNull;
    return Scaffold(
      appBar: AppBar(title: Text(tr('Echte Bankkonten'))),
      floatingActionButton: FloatingActionButton.extended(
        key: const Key('bank_connect'),
        onPressed: () => context.push('/banken/verbinden'),
        icon: const Icon(Icons.add_link),
        label: Text(tr('Bank verbinden')),
      ),
      body: RefreshIndicator(
        onRefresh: () async {
          refreshBanking(ref);
          await ref.read(bankConnectionsProvider.future);
        },
        child: connections.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (e, _) => ListView(padding: const EdgeInsets.all(24), children: [
            Text(apiErrorMessage(e), textAlign: TextAlign.center),
            const SizedBox(height: 12),
            Center(
              child: OutlinedButton(
                onPressed: () => ref.invalidate(bankConnectionsProvider),
                child: Text(tr('Erneut versuchen')),
              ),
            ),
          ]),
          data: (list) {
            final visible = list.where((c) => c.status != 'REJECTED' || c.accounts.isNotEmpty).toList();
            return ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: safeListPadding(context).copyWith(bottom: safeListPadding(context).bottom + 72),
              children: [
                const BankInfoCard(),
                if (overview != null && overview.accounts.isNotEmpty) ...[
                  const SizedBox(height: 8),
                  _SummaryCard(overview: overview),
                ],
                if (list.any((c) => c.sandbox)) ...[
                  const SizedBox(height: 8),
                  const SandboxNotice(),
                ],
                const SizedBox(height: 8),
                if (visible.isEmpty)
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: 24),
                    child: Text(tr('Noch keine Bank verbunden.'), textAlign: TextAlign.center),
                  ),
                for (final c in visible) _ConnectionCard(connection: c),
                if (visible.any((c) => c.accounts.isNotEmpty))
                  Align(
                    alignment: Alignment.centerLeft,
                    child: TextButton.icon(
                      key: const Key('bank_all_transactions'),
                      onPressed: () => context.push('/banken/umsaetze'),
                      icon: const Icon(Icons.list_alt),
                      label: Text(tr('Alle Umsätze verbundener Konten')),
                    ),
                  ),
              ],
            );
          },
        ),
      ),
    );
  }
}

/// Erklärt in zwei Sätzen, was "verbinden" bedeutet (nur Lesezugriff, kein Bank-Passwort).
class BankInfoCard extends StatelessWidget {
  const BankInfoCard({super.key});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Icon(Icons.verified_user, color: theme.colorScheme.primary),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              tr('Nur Lesezugriff über Open Banking (PSD2): Girokonto sieht Kontostand und Umsätze, kann aber kein Geld bewegen. Du meldest dich bei deiner Bank an – dein Bank-Passwort bekommt Girokonto nie.'),
              style: theme.textTheme.bodySmall,
            ),
          ),
        ]),
      ),
    );
  }
}

/// Hinweis: Sandbox-Banken sind simuliert.
class SandboxNotice extends StatelessWidget {
  const SandboxNotice({super.key});

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Card(
      color: scheme.tertiaryContainer,
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Row(children: [
          Icon(Icons.science, color: scheme.onTertiaryContainer),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              tr('Sandbox: simulierte Banken mit Testdaten – keine echte Bank, kein echtes Geld.'),
              style: TextStyle(color: scheme.onTertiaryContainer),
            ),
          ),
        ]),
      ),
    );
  }
}

/// Kleines Kennzeichen "Testdaten" bzw. Status.
class BankChip extends StatelessWidget {
  const BankChip(this.text, {super.key, this.warning = false});

  final String text;
  final bool warning;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
      decoration: BoxDecoration(
        color: warning ? scheme.errorContainer : scheme.secondaryContainer,
        borderRadius: BorderRadius.circular(6),
      ),
      child: Text(text,
          style: TextStyle(fontSize: 11, color: warning ? scheme.onErrorContainer : scheme.onSecondaryContainer)),
    );
  }
}

class _SummaryCard extends StatelessWidget {
  const _SummaryCard({required this.overview});

  final BankOverview overview;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      color: theme.colorScheme.primaryContainer,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(tr('Summe aller verbundenen Konten'), style: TextStyle(color: theme.colorScheme.onPrimaryContainer)),
          const SizedBox(height: 4),
          Text(formatCents(overview.totalBalanceCents),
              key: const Key('bank_total'),
              style: theme.textTheme.headlineSmall
                  ?.copyWith(color: theme.colorScheme.onPrimaryContainer, fontWeight: FontWeight.bold)),
          const SizedBox(height: 4),
          Text(
            tr('Diesen Monat: +{0} Einnahmen, -{1} Ausgaben',
                [formatCents(overview.monthIncomeCents), formatCents(overview.monthExpenseCents)]),
            style: TextStyle(color: theme.colorScheme.onPrimaryContainer),
          ),
        ]),
      ),
    );
  }
}

class _ConnectionCard extends ConsumerStatefulWidget {
  const _ConnectionCard({required this.connection});

  final BankConnection connection;

  @override
  ConsumerState<_ConnectionCard> createState() => _ConnectionCardState();
}

class _ConnectionCardState extends ConsumerState<_ConnectionCard> {
  bool _busy = false;

  Future<void> _sync() async {
    setState(() => _busy = true);
    final messenger = ScaffoldMessenger.of(context);
    try {
      final r = await ref.read(bankRepositoryProvider).sync(widget.connection.id);
      refreshBanking(ref);
      showBankMessage(messenger, r.message);
    } catch (e) {
      showBankMessage(messenger, apiErrorMessage(e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _renew() async {
    setState(() => _busy = true);
    try {
      final start = await ref.read(bankRepositoryProvider).renew(widget.connection.id);
      if (mounted) await runConsentFlow(context, ref, start);
    } catch (e) {
      if (mounted) showBankMessage(ScaffoldMessenger.of(context), apiErrorMessage(e));
    } finally {
      refreshBanking(ref);
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = widget.connection;
    final theme = Theme.of(context);
    final failed = c.lastSyncStatus != null && c.lastSyncStatus != 'OK';
    return Card(
      key: Key('bank_connection_${c.id}'),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 12, 8, 8),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [
            Icon(c.sandbox ? Icons.science : Icons.account_balance, color: theme.colorScheme.primary),
            const SizedBox(width: 12),
            Expanded(
              child: Text(c.bankName, style: theme.textTheme.titleMedium, maxLines: 2, overflow: TextOverflow.ellipsis),
            ),
            PopupMenuButton<String>(
              key: Key('bank_menu_${c.id}'),
              onSelected: (v) {
                if (v == 'revoke') revokeConnection(context, ref, c);
              },
              itemBuilder: (_) => [
                PopupMenuItem(
                  value: 'revoke',
                  child: Text(c.status == 'REVOKED' ? tr('Daten entfernen') : tr('Zugriff widerrufen')),
                ),
              ],
            ),
          ]),
          const SizedBox(height: 4),
          Wrap(spacing: 6, runSpacing: 4, children: [
            BankChip(c.statusLabel, warning: !c.isActive),
            if (c.sandbox) BankChip(tr('Testdaten')),
            if (failed && c.isActive) BankChip(tr('Abruf gestört'), warning: true),
          ]),
          const SizedBox(height: 8),
          Text(
            c.lastSyncAt == null
                ? tr('Noch nicht abgerufen')
                : tr('Zuletzt abgerufen: {0}', [formatDateTime(c.lastSyncAt!)]),
            style: theme.textTheme.bodySmall,
          ),
          if (c.isActive && c.consentExpiresAt != null)
            Text(
              tr('Freigabe gültig bis {0} ({1})', [
                formatDate(c.consentExpiresAt!),
                c.daysLeft == 1 ? tr('noch 1 Tag') : tr('noch {0} Tage', [c.daysLeft ?? 0]),
              ]),
              style: theme.textTheme.bodySmall?.copyWith(
                  color: (c.daysLeft ?? 99) <= 7 ? theme.colorScheme.error : null),
            ),
          if (c.permissions.isNotEmpty)
            Text(tr('Berechtigungen: {0}', [c.permissions.join(', ')]), style: theme.textTheme.bodySmall),
          if (failed && c.lastErrorMessage != null)
            Padding(
              padding: const EdgeInsets.only(top: 4),
              child: Text(c.lastErrorMessage!, style: TextStyle(color: theme.colorScheme.error)),
            ),
          for (final a in c.accounts)
            ListTile(
              key: Key('bank_account_${a.id}'),
              contentPadding: EdgeInsets.zero,
              dense: true,
              title: Text(a.name),
              subtitle: a.ibanMasked == null ? null : Text(a.ibanMasked!),
              trailing: Text(
                a.balanceCents == null ? '–' : formatCents(a.balanceCents!),
                style: const TextStyle(fontWeight: FontWeight.bold),
              ),
              onTap: () => context.push('/banken/umsaetze?konto=${a.id}&name=${Uri.encodeComponent(a.name)}'),
            ),
          Wrap(spacing: 8, children: [
            if (c.isActive)
              TextButton.icon(
                key: Key('bank_sync_${c.id}'),
                onPressed: _busy ? null : _sync,
                icon: _busy
                    ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2))
                    : const Icon(Icons.sync),
                label: Text(tr('Jetzt abrufen')),
              ),
            if (c.isPending)
              TextButton.icon(
                key: Key('bank_finish_${c.id}'),
                onPressed: _busy
                    ? null
                    : () async {
                        setState(() => _busy = true);
                        if (c.sandbox) {
                          final approve = await showDialog<bool>(
                              context: context, builder: (_) => SandboxConsentDialog(bankName: c.bankName));
                          if (approve != null) {
                            try {
                              final (_, msg) =
                                  await ref.read(bankRepositoryProvider).complete(c.id, approve: approve);
                              if (context.mounted) {
                                showBankMessage(ScaffoldMessenger.of(context), msg);
                              }
                            } catch (e) {
                              if (context.mounted) {
                                showBankMessage(ScaffoldMessenger.of(context), apiErrorMessage(e));
                              }
                            }
                          }
                        } else {
                          await completeRealConsent(context, ref, c.id, '');
                        }
                        refreshBanking(ref);
                        if (mounted) setState(() => _busy = false);
                      },
                icon: const Icon(Icons.check),
                label: Text(tr('Freigabe abschließen')),
              ),
            if (c.canRenew)
              TextButton.icon(
                key: Key('bank_renew_${c.id}'),
                onPressed: _busy ? null : _renew,
                icon: const Icon(Icons.autorenew),
                label: Text(tr('Freigabe erneuern')),
              ),
          ]),
        ]),
      ),
    );
  }
}
