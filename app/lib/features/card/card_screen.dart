import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_error.dart';
import '../../core/money.dart';
import '../accounts/accounts_repository.dart';
import '../accounts/bookings_repository.dart';
import 'card_model.dart';
import 'card_repository.dart';

/// Bereich "Karte": anzeigen, Details mit Passwort, sperren, Limits, Demo-Zahlung (/F310/ bis /F340/).
class CardScreen extends ConsumerStatefulWidget {
  const CardScreen({super.key});

  @override
  ConsumerState<CardScreen> createState() => _CardScreenState();
}

class _CardScreenState extends ConsumerState<CardScreen> {
  CardDetails? _details;
  Timer? _hideTimer;
  bool _busy = false;

  /// Lokaler Wert des Schiebereglers, bis "Speichern" gedrückt wird.
  double? _limitEuro;
  bool? _online;

  @override
  void dispose() {
    _hideTimer?.cancel();
    super.dispose();
  }

  void _snack(String text) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));
  }

  Future<void> _run(Future<void> Function() action) async {
    setState(() => _busy = true);
    try {
      await action();
    } catch (e) {
      _snack(apiErrorMessage(e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _showDetails() async {
    final password = await showDialog<String>(context: context, builder: (_) => const _PasswordDialog());
    if (password == null || password.isEmpty) return;
    await _run(() async {
      final d = await ref.read(cardRepositoryProvider).details(password);
      if (!mounted) return;
      setState(() => _details = d);
      // aus Sicherheitsgründen nach 30 Sekunden wieder verbergen
      _hideTimer?.cancel();
      _hideTimer = Timer(const Duration(seconds: 30), () {
        if (mounted) setState(() => _details = null);
      });
    });
  }

  Future<void> _toggleLock(bool locked) => _run(() async {
        await ref.read(cardRepositoryProvider).setLocked(locked);
        ref.invalidate(cardProvider);
        _snack(locked ? 'Karte gesperrt' : 'Karte entsperrt');
      });

  Future<void> _saveLimits(PaymentCard card) => _run(() async {
        final cents = ((_limitEuro ?? card.dailyLimitCents / 100) * 100).round();
        await ref.read(cardRepositoryProvider).setLimits(cents, _online ?? card.onlineEnabled);
        ref.invalidate(cardProvider);
        setState(() {
          _limitEuro = null;
          _online = null;
        });
        _snack('Limits gespeichert');
      });

  Future<void> _simulatePayment() async {
    final result = await showDialog<CardPaymentResult>(
      context: context,
      builder: (_) => const _PaymentDialog(),
    );
    if (result == null || !mounted) return;
    ref.invalidate(cardProvider);
    ref.invalidate(accountsProvider);
    ref.invalidate(recentBookingsProvider);
    await showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        icon: Icon(result.approved ? Icons.check_circle : Icons.cancel,
            color: result.approved ? Colors.green : Theme.of(ctx).colorScheme.error, size: 48),
        title: Text(result.approved ? 'Zahlung erfolgreich' : 'Zahlung abgelehnt'),
        content: Text('${formatCents(result.amountCents)} bei ${result.merchant}'
            '${result.approved ? '' : '\n\nGrund: ${result.message}'}'),
        actions: [TextButton(onPressed: () => Navigator.of(ctx).pop(), child: const Text('OK'))],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final card = ref.watch(cardProvider);
    return card.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => Center(
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          Text(apiErrorMessage(e)),
          const SizedBox(height: 12),
          OutlinedButton(onPressed: () => ref.invalidate(cardProvider), child: const Text('Erneut versuchen')),
        ]),
      ),
      data: (c) => _buildContent(c),
    );
  }

  Widget _buildContent(PaymentCard c) {
    final theme = Theme.of(context);
    final limitEuro = _limitEuro ?? c.dailyLimitCents / 100;
    final online = _online ?? c.onlineEnabled;
    final changed = _limitEuro != null || _online != null;
    final usedRatio = c.dailyLimitCents == 0 ? 1.0 : (c.spentTodayCents / c.dailyLimitCents).clamp(0.0, 1.0);

    return RefreshIndicator(
      onRefresh: () => ref.refresh(cardProvider.future),
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 420),
              child: _CardVisual(card: c, details: _details),
            ),
          ),
          const SizedBox(height: 12),
          Row(children: [
            Expanded(
              child: OutlinedButton.icon(
                onPressed: _busy
                    ? null
                    : _details == null
                        ? _showDetails
                        : () => setState(() => _details = null),
                icon: Icon(_details == null ? Icons.visibility : Icons.visibility_off),
                label: Text(_details == null ? 'Kartendaten zeigen' : 'Verbergen'),
              ),
            ),
            if (_details != null) ...[
              const SizedBox(width: 8),
              IconButton(
                tooltip: 'Nummer kopieren',
                icon: const Icon(Icons.copy),
                onPressed: () {
                  Clipboard.setData(ClipboardData(text: _details!.number));
                  _snack('Kartennummer kopiert');
                },
              ),
            ],
          ]),
          const SizedBox(height: 12),
          Card(
            child: SwitchListTile(
              secondary: Icon(c.isLocked ? Icons.lock : Icons.lock_open),
              title: const Text('Karte sperren'),
              subtitle: Text(c.isLocked
                  ? 'Gesperrt: alle Zahlungen werden abgelehnt'
                  : 'Aktiv: vorübergehend sperren, z. B. wenn du sie verlegt hast'),
              value: c.isLocked,
              onChanged: _busy ? null : _toggleLock,
            ),
          ),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text('Heute ausgegeben', style: theme.textTheme.titleSmall),
                const SizedBox(height: 8),
                LinearProgressIndicator(value: usedRatio, minHeight: 8, borderRadius: BorderRadius.circular(4)),
                const SizedBox(height: 6),
                Text('${formatCents(c.spentTodayCents)} von ${formatCents(c.dailyLimitCents)}'),
                const Divider(height: 32),
                Text('Tageslimit: ${formatCents((limitEuro * 100).round())}', style: theme.textTheme.titleSmall),
                Slider(
                  value: limitEuro.clamp(0, c.maxDailyLimitCents / 100).toDouble(),
                  min: 0,
                  max: c.maxDailyLimitCents / 100,
                  divisions: (c.maxDailyLimitCents / 100 / 50).round(),
                  label: formatCents((limitEuro * 100).round()),
                  onChanged: _busy ? null : (v) => setState(() => _limitEuro = v),
                ),
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Online-Zahlungen erlauben'),
                  value: online,
                  onChanged: _busy ? null : (v) => setState(() => _online = v),
                ),
                if (changed)
                  Align(
                    alignment: Alignment.centerRight,
                    child: FilledButton(
                      onPressed: _busy ? null : () => _saveLimits(c),
                      child: const Text('Limits speichern'),
                    ),
                  ),
              ]),
            ),
          ),
          const SizedBox(height: 8),
          Card(
            color: theme.colorScheme.tertiaryContainer,
            child: ListTile(
              leading: const Icon(Icons.storefront),
              title: const Text('Demo: Kartenzahlung simulieren'),
              subtitle: const Text('Löst eine Zahlung bei einem fiktiven Händler aus'),
              trailing: const Icon(Icons.chevron_right),
              onTap: _busy ? null : _simulatePayment,
            ),
          ),
        ],
      ),
    );
  }
}

class _CardVisual extends StatelessWidget {
  const _CardVisual({required this.card, required this.details});

  final PaymentCard card;
  final CardDetails? details;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    const white = TextStyle(color: Colors.white);
    return AspectRatio(
      aspectRatio: 1.586, // Format einer echten Karte
      child: Container(
        padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(18),
          gradient: LinearGradient(
            colors: card.isLocked
                ? [Colors.grey.shade600, Colors.grey.shade800]
                : [scheme.primary, const Color(0xFF14213D)],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(children: [
              const Text('Projektbank', style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold)),
              const Spacer(),
              if (card.isLocked)
                const Chip(
                  avatar: Icon(Icons.lock, size: 16),
                  label: Text('Gesperrt'),
                  visualDensity: VisualDensity.compact,
                ),
            ]),
            const Spacer(),
            Text(
              details?.formattedNumber ?? card.maskedNumber,
              style: const TextStyle(color: Colors.white, fontSize: 22, letterSpacing: 2, fontFamily: 'monospace'),
            ),
            const SizedBox(height: 12),
            Row(children: [
              Expanded(child: Text(card.holderName, style: white, overflow: TextOverflow.ellipsis)),
              Text('gültig bis ${card.expiry}', style: white),
              if (details != null) ...[
                const SizedBox(width: 12),
                Text('CVC ${details!.cvc}', style: white),
              ],
            ]),
          ],
        ),
      ),
    );
  }
}

class _PasswordDialog extends StatefulWidget {
  const _PasswordDialog();

  @override
  State<_PasswordDialog> createState() => _PasswordDialogState();
}

class _PasswordDialogState extends State<_PasswordDialog> {
  final _password = TextEditingController();

  @override
  void dispose() {
    _password.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('Passwort bestätigen'),
      content: TextField(
        controller: _password,
        obscureText: true,
        autofocus: true,
        decoration: const InputDecoration(labelText: 'Passwort'),
        onSubmitted: (v) => Navigator.of(context).pop(v),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.of(context).pop(), child: const Text('Abbrechen')),
        FilledButton(onPressed: () => Navigator.of(context).pop(_password.text), child: const Text('Anzeigen')),
      ],
    );
  }
}

/// Dialog des Händler-Simulators (/F340/).
class _PaymentDialog extends ConsumerStatefulWidget {
  const _PaymentDialog();

  @override
  ConsumerState<_PaymentDialog> createState() => _PaymentDialogState();
}

class _PaymentDialogState extends ConsumerState<_PaymentDialog> {
  static const _merchants = ['Bäckerei Schmidt', 'Supermarkt Frisch', 'Tankstelle Nord', 'Online-Shop Elektro', 'Kino am Main'];

  final _merchant = TextEditingController(text: _merchants.first);
  final _amount = TextEditingController(text: '12,90');
  bool _online = false;
  bool _sending = false;
  String? _error;

  @override
  void dispose() {
    _merchant.dispose();
    _amount.dispose();
    super.dispose();
  }

  Future<void> _pay() async {
    final cents = parseEuroToCents(_amount.text);
    if (cents == null || cents <= 0) {
      setState(() => _error = 'Bitte gültigen Betrag eingeben');
      return;
    }
    if (_merchant.text.trim().isEmpty) {
      setState(() => _error = 'Bitte Händler eingeben');
      return;
    }
    setState(() {
      _sending = true;
      _error = null;
    });
    try {
      final r = await ref.read(cardRepositoryProvider).simulatePayment(_merchant.text, cents, _online);
      if (mounted) Navigator.of(context).pop(r);
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
    return AlertDialog(
      title: const Text('Kartenzahlung simulieren'),
      content: SingleChildScrollView(
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          Wrap(spacing: 6, runSpacing: 6, children: [
            for (final m in _merchants)
              ActionChip(
                label: Text(m),
                onPressed: () => setState(() {
                  _merchant.text = m;
                  _online = m.startsWith('Online');
                }),
              ),
          ]),
          const SizedBox(height: 12),
          TextField(controller: _merchant, decoration: const InputDecoration(labelText: 'Händler')),
          const SizedBox(height: 12),
          TextField(
            controller: _amount,
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
            decoration: const InputDecoration(labelText: 'Betrag', suffixText: '€'),
          ),
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            title: const Text('Online-Zahlung'),
            value: _online,
            onChanged: (v) => setState(() => _online = v),
          ),
          if (_error != null) Text(_error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
        ]),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.of(context).pop(), child: const Text('Abbrechen')),
        FilledButton(onPressed: _sending ? null : _pay, child: const Text('Bezahlen')),
      ],
    );
  }
}
