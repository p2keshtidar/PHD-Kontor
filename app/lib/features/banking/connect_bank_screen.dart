import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_error.dart';
import '../../core/layout.dart';
import '../../l10n/l10n.dart';
import 'bank_accounts_screen.dart';
import 'bank_consent.dart';
import 'bank_models.dart';
import 'bank_repository.dart';

/// Bank auswählen und Lesezugriff freigeben.
class ConnectBankScreen extends ConsumerStatefulWidget {
  const ConnectBankScreen({super.key});

  @override
  ConsumerState<ConnectBankScreen> createState() => _ConnectBankScreenState();
}

class _ConnectBankScreenState extends ConsumerState<ConnectBankScreen> {
  String? _provider;
  String _search = '';
  String? _busyId;

  Future<void> _connect(BankProvider provider, Institution inst) async {
    setState(() => _busyId = inst.id);
    ConnectStart start;
    try {
      start = await ref.read(bankRepositoryProvider).connect(provider.id, inst.id);
    } catch (e) {
      if (mounted) {
        setState(() => _busyId = null);
        showBankMessage(ScaffoldMessenger.of(context), apiErrorMessage(e));
      }
      return;
    }
    if (!mounted) return;
    // Ladeanzeige nur während der Anfrage – nicht, solange der Nutzer bei der Bank bzw. im Dialog ist
    setState(() => _busyId = null);
    final ok = await runConsentFlow(context, ref, start);
    refreshBanking(ref);
    if (ok && mounted) Navigator.of(context).pop(true);
  }

  @override
  Widget build(BuildContext context) {
    final providers = ref.watch(bankProvidersProvider);
    return Scaffold(
      appBar: AppBar(title: Text(tr('Bank verbinden'))),
      body: providers.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(child: Text(apiErrorMessage(e))),
        data: (list) {
          if (list.isEmpty) {
            return Padding(
              padding: const EdgeInsets.all(24),
              child: Text(tr('Zurzeit ist kein Anbieter für Bankverbindungen eingerichtet.')),
            );
          }
          final provider = list.firstWhere((p) => p.id == _provider, orElse: () => list.first);
          final institutions = ref.watch(institutionsProvider(provider.id));
          return ListView(padding: safeListPadding(context), children: [
            const BankInfoCard(),
            if (list.length > 1) ...[
              const SizedBox(height: 8),
              Wrap(spacing: 8, children: [
                for (final p in list)
                  ChoiceChip(
                    label: Text(p.name),
                    selected: p.id == provider.id,
                    onSelected: (_) => setState(() => _provider = p.id),
                  ),
              ]),
            ],
            const SizedBox(height: 8),
            Text(provider.description, style: Theme.of(context).textTheme.bodySmall),
            if (provider.sandbox) ...[const SizedBox(height: 8), const SandboxNotice()],
            const SizedBox(height: 12),
            TextField(
              key: const Key('bank_search'),
              decoration: InputDecoration(
                labelText: tr('Bank suchen'),
                prefixIcon: const Icon(Icons.search),
              ),
              onChanged: (v) => setState(() => _search = v.trim().toLowerCase()),
            ),
            const SizedBox(height: 8),
            institutions.when(
              loading: () => const Padding(
                padding: EdgeInsets.all(24),
                child: Center(child: CircularProgressIndicator()),
              ),
              error: (e, _) => Text(apiErrorMessage(e)),
              data: (banks) {
                final shown = banks.where((b) => _search.isEmpty || b.name.toLowerCase().contains(_search)).toList();
                if (shown.isEmpty) return Text(tr('Keine passende Bank gefunden.'));
                return Card(
                  child: Column(children: [
                    for (final b in shown)
                      ListTile(
                        key: Key('institution_${b.id}'),
                        leading: Icon(b.sandbox ? Icons.science : Icons.account_balance),
                        title: Text(b.name),
                        subtitle: Text(tr('Umsätze bis {0} Tage zurück, Zugriff {1} Tage',
                            [b.historyDays > 90 ? 90 : b.historyDays, b.accessDays])),
                        trailing: _busyId == b.id
                            ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2))
                            : const Icon(Icons.chevron_right),
                        onTap: _busyId == null ? () => _connect(provider, b) : null,
                      ),
                  ]),
                );
              },
            ),
            const SizedBox(height: 12),
            Text(
              tr('Du kannst den Zugriff jederzeit widerrufen. Dabei entscheidest du, ob die bisher abgerufenen Umsätze gelöscht werden.'),
              style: Theme.of(context).textTheme.bodySmall,
            ),
          ]);
        },
      ),
    );
  }
}
