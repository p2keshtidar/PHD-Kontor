import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../l10n/language_button.dart';
import '../../l10n/l10n.dart';

import '../../core/api_error.dart';
import '../../core/validators.dart';
import 'auth_controller.dart';
import '../../core/layout.dart';

/// Registrierung (/F010/): legt Nutzer, Girokonto mit IBAN und 1.000 € Startguthaben an.
class RegisterScreen extends ConsumerStatefulWidget {
  const RegisterScreen({super.key});

  @override
  ConsumerState<RegisterScreen> createState() => _RegisterScreenState();
}

class _RegisterScreenState extends ConsumerState<RegisterScreen> {
  final _formKey = GlobalKey<FormState>();
  final _firstName = TextEditingController();
  final _lastName = TextEditingController();
  final _email = TextEditingController();
  final _phone = TextEditingController();
  final _birthDateText = TextEditingController();
  final _password = TextEditingController();
  final _password2 = TextEditingController();
  DateTime? _birthDate;
  bool _loading = false;
  String? _error;

  @override
  void dispose() {
    for (final c in [
      _firstName,
      _lastName,
      _email,
      _phone,
      _birthDateText,
      _password,
      _password2
    ]) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _pickBirthDate() async {
    final now = DateTime.now();
    final picked = await showDatePicker(
      context: context,
      initialDate: _birthDate ?? DateTime(now.year - 20, now.month, now.day),
      firstDate: DateTime(now.year - 100),
      lastDate: now,
      helpText: tr('Geburtsdatum'),
    );
    if (picked != null) {
      setState(() {
        _birthDate = picked;
        _birthDateText.text = toGermanDate(picked);
      });
    }
  }

  String? _validateBirthDate(String? _) {
    if (_birthDate == null) return tr('Geburtsdatum fehlt');
    if (!isAdult(_birthDate!, DateTime.now())) {
      return tr('Ein Konto ist erst ab 18 Jahren möglich');
    }
    return null;
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      await ref.read(authControllerProvider.notifier).register(
            firstName: _firstName.text,
            lastName: _lastName.text,
            email: _email.text,
            phone: _phone.text,
            birthDate: _birthDate!,
            password: _password.text,
          );
    } catch (e) {
      if (mounted) setState(() => _error = apiErrorMessage(e));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    const gap = SizedBox(height: 14);
    return Scaffold(
      appBar: AppBar(
        title: Text(tr('Konto eröffnen')),
        leading: IconButton(
          tooltip: tr('Zurück'),
          icon: const Icon(Icons.arrow_back),
          onPressed: () => context.go('/login'),
        ),
        actions: const [LanguageButton()],
      ),
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: safeListPadding(context, 24),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 480),
              child: Form(
                key: _formKey,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Text(
                      tr('In wenigen Schritten zu deinem Konto. Du startest mit 1.000 € Spielgeld.'),
                      style: theme.textTheme.bodyMedium,
                    ),
                    const SizedBox(height: 20),
                    Row(children: [
                      Expanded(
                        child: TextFormField(
                          controller: _firstName,
                          textCapitalization: TextCapitalization.words,
                          decoration: InputDecoration(labelText: tr('Vorname')),
                          validator: (v) => validateRequired(v, tr('Vorname')),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: TextFormField(
                          controller: _lastName,
                          textCapitalization: TextCapitalization.words,
                          decoration: InputDecoration(labelText: tr('Nachname')),
                          validator: (v) => validateRequired(v, tr('Nachname')),
                        ),
                      ),
                    ]),
                    gap,
                    TextFormField(
                      controller: _email,
                      keyboardType: TextInputType.emailAddress,
                      decoration: InputDecoration(
                          labelText: tr('E-Mail'), prefixIcon: Icon(Icons.mail)),
                      validator: validateEmail,
                    ),
                    gap,
                    TextFormField(
                      controller: _phone,
                      keyboardType: TextInputType.phone,
                      decoration: InputDecoration(
                          labelText: tr('Telefonnummer'),
                          hintText: 'z. B. 0151 12345678',
                          prefixIcon: Icon(Icons.phone)),
                      validator: validatePhone,
                    ),
                    gap,
                    TextFormField(
                      controller: _birthDateText,
                      readOnly: true,
                      onTap: _pickBirthDate,
                      decoration: InputDecoration(
                          labelText: tr('Geburtsdatum'),
                          prefixIcon: Icon(Icons.cake)),
                      validator: _validateBirthDate,
                    ),
                    gap,
                    TextFormField(
                      controller: _password,
                      obscureText: true,
                      decoration: InputDecoration(
                          labelText: tr('Passwort'),
                          helperText: tr('Mindestens 10 Zeichen'),
                          prefixIcon: Icon(Icons.lock)),
                      validator: validateNewPassword,
                    ),
                    gap,
                    TextFormField(
                      controller: _password2,
                      obscureText: true,
                      decoration: InputDecoration(
                          labelText: tr('Passwort wiederholen'),
                          prefixIcon: Icon(Icons.lock_outline)),
                      validator: (v) => v != _password.text
                          ? tr('Passwörter stimmen nicht überein')
                          : null,
                    ),
                    if (_error != null) ...[
                      const SizedBox(height: 16),
                      Text(_error!,
                          style: TextStyle(color: theme.colorScheme.error)),
                    ],
                    const SizedBox(height: 24),
                    FilledButton(
                      onPressed: _loading ? null : _submit,
                      child: _loading
                          ? const SizedBox(
                              height: 20,
                              width: 20,
                              child: CircularProgressIndicator(strokeWidth: 2))
                          : Text(tr('Konto eröffnen')),
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
