import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_error.dart';
import '../../l10n/l10n.dart';
import 'bank_models.dart';
import 'bank_repository.dart';

/// Meldung anzeigen; eine ältere Meldung wird ersetzt statt hinten angestellt.
void showBankMessage(ScaffoldMessengerState messenger, String text) {
  messenger
    ..removeCurrentSnackBar()
    ..showSnackBar(SnackBar(content: Text(text)));
}

/// Ablauf nach dem Anlegen einer Freigabe: bei der Sandbox ein deutlich gekennzeichneter Test-Dialog,
/// bei echten Banken die Seite der Bank im Browser und danach "Freigabe abschließen".
/// Rückgabe: true, wenn die Bank jetzt verbunden ist.
Future<bool> runConsentFlow(BuildContext context, WidgetRef ref, ConnectStart start) async {
  final repo = ref.read(bankRepositoryProvider);
  final messenger = ScaffoldMessenger.of(context);
  if (start.sandbox) {
    final approve = await showDialog<bool>(
      context: context,
      barrierDismissible: false,
      builder: (_) => SandboxConsentDialog(bankName: start.connection.bankName),
    );
    if (approve == null) return false;
    try {
      final (conn, message) = await repo.complete(start.connection.id, approve: approve);
      refreshBanking(ref);
      showBankMessage(messenger, message);
      return conn.isActive;
    } catch (e) {
      showBankMessage(messenger, apiErrorMessage(e));
      return false;
    }
  }

  final url = start.authorizationUrl;
  if (url != null) {
    var opened = false;
    try {
      opened = await ref.read(linkOpenerProvider)(Uri.parse(url));
    } catch (_) {
      opened = false;
    }
    if (!context.mounted) return false;
    if (!opened) {
      await showDialog<void>(context: context, builder: (_) => _LinkDialog(url: url));
    }
  }
  if (!context.mounted) return false;
  return completeRealConsent(context, ref, start.connection.id, start.nextStepText);
}

/// Echte Bank: nach der Rückkehr aus dem Browser den Status prüfen lassen.
Future<bool> completeRealConsent(BuildContext context, WidgetRef ref, int connectionId, String hint) async {
  final repo = ref.read(bankRepositoryProvider);
  final messenger = ScaffoldMessenger.of(context);
  while (context.mounted) {
    final check = await showDialog<bool>(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => AlertDialog(
        title: Text(tr('Freigabe bei der Bank')),
        content: Text(hint.isEmpty
            ? tr('Melde dich auf der Seite deiner Bank an und bestätige den Lesezugriff. Danach zurück zur App.')
            : hint),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: Text(tr('Später'))),
          FilledButton(
            key: const Key('bank_complete'),
            onPressed: () => Navigator.pop(ctx, true),
            child: Text(tr('Freigabe abschließen')),
          ),
        ],
      ),
    );
    if (check != true) return false;
    try {
      final (conn, message) = await repo.complete(connectionId);
      refreshBanking(ref);
      showBankMessage(messenger, message);
      return conn.isActive;
    } catch (e) {
      showBankMessage(messenger, apiErrorMessage(e));
      if (apiErrorCode(e) != 'CONSENT_PENDING') return false;
    }
  }
  return false;
}

/// Sandbox: simulierte Entscheidung "bei der Bank" – ausdrücklich als Test gekennzeichnet.
class SandboxConsentDialog extends StatelessWidget {
  const SandboxConsentDialog({super.key, required this.bankName});

  final String bankName;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return AlertDialog(
      icon: Icon(Icons.science, color: scheme.tertiary),
      title: Text(tr('Test-Freigabe (Sandbox)')),
      content: SingleChildScrollView(
        child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(tr('Simulation: Bei einer echten Bank würdest du dich jetzt auf deren Seite anmelden. Die Sandbox ist keine echte Bank – es werden nur Testdaten geladen.')),
          const SizedBox(height: 12),
          Text(bankName, style: const TextStyle(fontWeight: FontWeight.bold)),
          const SizedBox(height: 4),
          Text(tr('Zugriff: Kontodetails, Salden und Umsätze lesen. Keine Zahlungen.')),
        ]),
      ),
      actions: [
        TextButton(
          key: const Key('sandbox_reject'),
          onPressed: () => Navigator.pop(context, false),
          child: Text(tr('Ablehnen')),
        ),
        FilledButton(
          key: const Key('sandbox_approve'),
          onPressed: () => Navigator.pop(context, true),
          child: Text(tr('Freigeben (Test)')),
        ),
      ],
    );
  }
}

class _LinkDialog extends StatelessWidget {
  const _LinkDialog({required this.url});

  final String url;

  @override
  Widget build(BuildContext context) => AlertDialog(
        title: Text(tr('Seite der Bank öffnen')),
        content: SingleChildScrollView(
          child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(tr('Der Browser konnte nicht geöffnet werden. Öffne diese Adresse selbst:')),
            const SizedBox(height: 8),
            SelectableText(url),
          ]),
        ),
        actions: [
          TextButton(
            onPressed: () => Clipboard.setData(ClipboardData(text: url)),
            child: Text(tr('Adresse kopieren')),
          ),
          FilledButton(onPressed: () => Navigator.pop(context), child: Text(tr('Weiter'))),
        ],
      );
}

/// Widerrufen mit Auswahl, ob die bisherigen Daten gelöscht werden.
Future<void> revokeConnection(BuildContext context, WidgetRef ref, BankConnection c) async {
  final messenger = ScaffoldMessenger.of(context);
  final deleteData = await showDialog<bool>(
    context: context,
    builder: (_) => _RevokeDialog(connection: c),
  );
  if (deleteData == null) return;
  try {
    final message = await ref.read(bankRepositoryProvider).revoke(c.id, deleteData: deleteData);
    refreshBanking(ref);
    showBankMessage(messenger, message);
  } catch (e) {
    showBankMessage(messenger, apiErrorMessage(e));
  }
}

class _RevokeDialog extends StatefulWidget {
  const _RevokeDialog({required this.connection});

  final BankConnection connection;

  @override
  State<_RevokeDialog> createState() => _RevokeDialogState();
}

class _RevokeDialogState extends State<_RevokeDialog> {
  bool _delete = false;

  @override
  Widget build(BuildContext context) {
    final revoked = widget.connection.status == 'REVOKED';
    return AlertDialog(
      title: Text(revoked ? tr('Daten entfernen?') : tr('Zugriff widerrufen?')),
      content: SingleChildScrollView(
        child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(tr('PHD Kontor ruft danach keine Daten mehr von {0} ab. Die Freigabe wird auch beim Anbieter gelöscht.',
              [widget.connection.bankName])),
          CheckboxListTile(
            key: const Key('revoke_delete_data'),
            contentPadding: EdgeInsets.zero,
            value: _delete || revoked,
            onChanged: revoked ? null : (v) => setState(() => _delete = v ?? false),
            title: Text(tr('Bisherige Umsätze dieser Bank löschen')),
          ),
        ]),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: Text(tr('Abbrechen'))),
        FilledButton(
          key: const Key('revoke_confirm'),
          onPressed: () => Navigator.pop(context, _delete || revoked),
          child: Text(revoked ? tr('Entfernen') : tr('Widerrufen')),
        ),
      ],
    );
  }
}
