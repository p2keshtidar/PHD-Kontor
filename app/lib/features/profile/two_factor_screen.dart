import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:qr_flutter/qr_flutter.dart';
import '../../l10n/l10n.dart';

import '../../core/api_error.dart';
import '../auth/auth_controller.dart';
import '../auth/auth_repository.dart';
import '../../core/layout.dart';

/// /K050/ Zwei-Faktor-Anmeldung: einrichten (QR-Code scannen, Code bestätigen) oder ausschalten.
class TwoFactorScreen extends ConsumerStatefulWidget {
  const TwoFactorScreen({super.key});

  @override
  ConsumerState<TwoFactorScreen> createState() => _TwoFactorScreenState();
}

class _TwoFactorScreenState extends ConsumerState<TwoFactorScreen> {
  final _code = TextEditingController();
  final _password = TextEditingController();
  String? _secret;
  String? _uri;
  String? _error;
  bool _busy = false;

  @override
  void dispose() {
    _code.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _run(Future<void> Function() action) async {
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await action();
    } catch (e) {
      if (mounted) setState(() => _error = apiErrorMessage(e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _start() => _run(() async {
        final (secret, uri) = await ref.read(twoFactorRepositoryProvider).setup();
        setState(() {
          _secret = secret;
          _uri = uri;
        });
      });

  Future<void> _confirm() => _run(() async {
        await ref.read(twoFactorRepositoryProvider).enable(_code.text);
        await ref.read(authControllerProvider.notifier).reload();
        if (!mounted) return;
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(tr('Zwei-Faktor-Anmeldung ist jetzt aktiv'))));
        Navigator.of(context).pop();
      });

  Future<void> _disable() => _run(() async {
        await ref.read(twoFactorRepositoryProvider).disable(_password.text, _code.text);
        await ref.read(authControllerProvider.notifier).reload();
        if (!mounted) return;
        ScaffoldMessenger.of(context)
            .showSnackBar(SnackBar(content: Text(tr('Zwei-Faktor-Anmeldung ausgeschaltet'))));
        Navigator.of(context).pop();
      });

  @override
  Widget build(BuildContext context) {
    final enabled = ref.watch(authControllerProvider).valueOrNull?.twoFactorEnabled ?? false;
    final theme = Theme.of(context);
    final codeField = TextField(
      key: const Key('totp_code'),
      controller: _code,
      keyboardType: TextInputType.number,
      // Authenticator-Apps zeigen "123 456": Leerzeichen beim Einfügen ignorieren, dann auf 6 Ziffern kürzen
      inputFormatters: [FilteringTextInputFormatter.digitsOnly, LengthLimitingTextInputFormatter(6)],
      decoration: InputDecoration(
        labelText: tr('6-stelliger Code aus der App'),
        prefixIcon: Icon(Icons.pin),
        counterText: '',
      ),
    );
    final error = _error == null
        ? const SizedBox.shrink()
        : Padding(
            padding: const EdgeInsets.only(top: 8),
            child: Text(_error!, style: TextStyle(color: theme.colorScheme.error)),
          );

    return Scaffold(
      appBar: AppBar(title: Text(tr('Zwei-Faktor-Anmeldung'))),
      body: ListView(padding: safeListPadding(context), children: [
        if (enabled) ...[
          ListTile(
            contentPadding: EdgeInsets.zero,
            leading: Icon(Icons.verified_user, color: Colors.green, size: 36),
            title: Text(tr('Aktiv')),
            subtitle: Text(tr('Bei jeder Anmeldung brauchst du zusätzlich den Code aus deiner Authenticator-App.')),
          ),
          const SizedBox(height: 16),
          Text(tr('Ausschalten'), style: theme.textTheme.titleMedium),
          const SizedBox(height: 8),
          TextField(
            controller: _password,
            obscureText: true,
            decoration: InputDecoration(labelText: tr('Passwort'), prefixIcon: Icon(Icons.lock)),
          ),
          const SizedBox(height: 8),
          codeField,
          error,
          const SizedBox(height: 16),
          OutlinedButton.icon(
            onPressed: _busy ? null : _disable,
            icon: const Icon(Icons.no_encryption_outlined),
            label: Text(tr('Zwei-Faktor-Anmeldung ausschalten')),
          ),
        ] else if (_secret == null) ...[
          const Icon(Icons.security, size: 64),
          const SizedBox(height: 12),
          Text(tr('Mehr Sicherheit für dein Konto'), style: theme.textTheme.titleLarge, textAlign: TextAlign.center),
          const SizedBox(height: 8),
          Text(
            tr('Zusätzlich zum Passwort fragt die App bei der Anmeldung nach einem Code, den eine Authenticator-App alle 30 Sekunden neu erzeugt (z. B. Google Authenticator, Microsoft Authenticator, Authy oder 2FAS). Wer nur dein Passwort kennt, kommt so nicht in dein Konto.'),
            textAlign: TextAlign.center,
          ),
          error,
          const SizedBox(height: 24),
          FilledButton.icon(
            key: const Key('totp_start'),
            onPressed: _busy ? null : _start,
            icon: const Icon(Icons.qr_code),
            label: Text(tr('Einrichten')),
          ),
        ] else ...[
          Text(tr('1. QR-Code mit der Authenticator-App scannen'), style: theme.textTheme.titleMedium),
          const SizedBox(height: 12),
          Center(
            child: Container(
              padding: const EdgeInsets.all(12),
              color: Colors.white,
              child: QrImageView(data: _uri!, size: 200, backgroundColor: Colors.white),
            ),
          ),
          const SizedBox(height: 12),
          Text(tr('Oder den Schlüssel von Hand eingeben (Typ: zeitbasiert):')),
          const SizedBox(height: 4),
          Row(children: [
            Expanded(
              child: SelectableText(groupSecret(_secret!),
                  key: const Key('totp_secret'),
                  style: const TextStyle(fontFamily: 'monospace', fontSize: 16, fontWeight: FontWeight.bold)),
            ),
            IconButton(
              tooltip: tr('Kopieren'),
              icon: const Icon(Icons.copy),
              onPressed: () async {
                await Clipboard.setData(ClipboardData(text: _secret!));
                if (context.mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(tr('Schlüssel kopiert'))));
                }
              },
            ),
          ]),
          const SizedBox(height: 20),
          Text(tr('2. Angezeigten Code eingeben'), style: theme.textTheme.titleMedium),
          const SizedBox(height: 8),
          codeField,
          error,
          const SizedBox(height: 16),
          FilledButton.icon(
            key: const Key('totp_confirm'),
            onPressed: _busy ? null : _confirm,
            icon: const Icon(Icons.check),
            label: Text(tr('Bestätigen und einschalten')),
          ),
        ],
      ]),
    );
  }
}


/// "ABCD EFGH ..." – leichter abzutippen
String groupSecret(String s) =>
    [for (var i = 0; i < s.length; i += 4) s.substring(i, i + 4 > s.length ? s.length : i + 4)].join(' ');
