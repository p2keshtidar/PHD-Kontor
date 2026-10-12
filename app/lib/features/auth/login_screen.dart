import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/api_error.dart';
import '../../core/config.dart';
import '../../core/validators.dart';
import 'auth_controller.dart';

class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final _formKey = GlobalKey<FormState>();
  final _email = TextEditingController();
  final _password = TextEditingController();
  final _code = TextEditingController();
  bool _needsCode = false; // /K050/: Zwei-Faktor-Code abfragen
  bool _loading = false;
  bool _obscure = true;
  String? _error;

  @override
  void dispose() {
    _email.dispose();
    _password.dispose();
    _code.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      // Bei Erfolg leitet der Router automatisch zur Startseite weiter.
      await ref
          .read(authControllerProvider.notifier)
          .login(_email.text, _password.text, code: _needsCode ? _code.text : null);
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = apiErrorMessage(e);
          if (apiErrorCode(e) == 'TOTP_REQUIRED') _needsCode = true;
        });
      }
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _editServer() async {
    final controller = TextEditingController(text: AppConfig.apiBaseUrl);
    final result = await showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Server-Adresse'),
        content: SingleChildScrollView(child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Adresse des Backends, z. B. http://192.168.178.20:8080 '
                '(IP deines PCs im selben WLAN). Leer lassen = Standard.'),
            const SizedBox(height: 12),
            TextField(
              controller: controller,
              keyboardType: TextInputType.url,
              autofocus: true,
              decoration: const InputDecoration(labelText: 'http://…:8080'),
            ),
          ],
        )),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(ctx), child: const Text('Abbrechen')),
          FilledButton(
              onPressed: () => Navigator.pop(ctx, controller.text),
              child: const Text('Speichern')),
        ],
      ),
    );
    if (result == null) return;
    await AppConfig.save(result);
    if (mounted) setState(() => _error = null);
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 420),
              child: Form(
                key: _formKey,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Icon(Icons.account_balance_wallet_rounded,
                        size: 64, color: theme.colorScheme.primary),
                    const SizedBox(height: 12),
                    Text('Willkommen zurück',
                        textAlign: TextAlign.center,
                        style: theme.textTheme.headlineSmall),
                    const SizedBox(height: 4),
                    Text('Melde dich bei deinem Konto an',
                        textAlign: TextAlign.center,
                        style: theme.textTheme.bodyMedium),
                    const SizedBox(height: 32),
                    TextFormField(
                      key: const Key('login_email'),
                      controller: _email,
                      keyboardType: TextInputType.emailAddress,
                      autofillHints: const [AutofillHints.email],
                      decoration: const InputDecoration(
                          labelText: 'E-Mail', prefixIcon: Icon(Icons.mail)),
                      validator: validateEmail,
                    ),
                    const SizedBox(height: 16),
                    TextFormField(
                      key: const Key('login_password'),
                      controller: _password,
                      obscureText: _obscure,
                      autofillHints: const [AutofillHints.password],
                      decoration: InputDecoration(
                        labelText: 'Passwort',
                        prefixIcon: const Icon(Icons.lock),
                        suffixIcon: IconButton(
                          icon: Icon(
                              _obscure ? Icons.visibility : Icons.visibility_off),
                          onPressed: () => setState(() => _obscure = !_obscure),
                        ),
                      ),
                      validator: (v) =>
                          (v == null || v.isEmpty) ? 'Bitte Passwort eingeben' : null,
                      onFieldSubmitted: (_) => _submit(),
                    ),
                    if (_needsCode) ...[
                      const SizedBox(height: 16),
                      TextFormField(
                        key: const Key('login_code'),
                        controller: _code,
                        autofocus: true,
                        keyboardType: TextInputType.number,
                        autofillHints: const [AutofillHints.oneTimeCode],
                        maxLength: 6,
                        decoration: const InputDecoration(
                          labelText: 'Code aus der Authenticator-App',
                          prefixIcon: Icon(Icons.verified_user),
                          counterText: '',
                        ),
                        validator: (v) => (v == null || !RegExp(r'^\d{6}$').hasMatch(v.trim()))
                            ? 'Bitte den 6-stelligen Code eingeben'
                            : null,
                        onFieldSubmitted: (_) => _submit(),
                      ),
                    ],
                    if (_error != null) ...[
                      const SizedBox(height: 16),
                      Text(_error!,
                          style: TextStyle(color: theme.colorScheme.error)),
                    ],
                    const SizedBox(height: 24),
                    FilledButton(
                      key: const Key('login_submit'),
                      onPressed: _loading ? null : _submit,
                      child: _loading
                          ? const SizedBox(
                              height: 20,
                              width: 20,
                              child: CircularProgressIndicator(strokeWidth: 2))
                          : const Text('Anmelden'),
                    ),
                    const SizedBox(height: 12),
                    TextButton(
                      onPressed: _loading ? null : () => context.go('/register'),
                      child: const Text('Noch kein Konto? Jetzt registrieren'),
                    ),
                    const SizedBox(height: 8),
                    TextButton.icon(
                      key: const Key('server_settings'),
                      onPressed: _editServer,
                      icon: const Icon(Icons.dns_outlined, size: 18),
                      label: Text('Server: ${AppConfig.apiBaseUrl}',
                          style: theme.textTheme.bodySmall),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
