import 'package:file_selector/file_selector.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/api_error.dart';
import '../../core/state_views.dart';
import '../../core/dates.dart';
import '../../core/layout.dart';
import '../../core/money.dart';
import '../../core/text_dialog.dart';
import '../../l10n/l10n.dart';
import 'contracts_repository.dart';

/// Verträge: Kosten im Monat/Jahr, nächste Kündigungsfristen zuerst.
class ContractsScreen extends ConsumerWidget {
  const ContractsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final data = ref.watch(contractsProvider);
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(title: Text(tr('Verträge & Dokumente'))),
      floatingActionButton: FloatingActionButton.extended(
        key: const Key('contracts_add'),
        onPressed: () async {
          await Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => const ContractFormScreen()));
          ref.invalidate(contractsProvider);
        },
        icon: const Icon(Icons.add),
        label: Text(tr('Vertrag')),
      ),
      body: data.when(
        loading: () => const LoadingView(),
        error: (e, _) => ErrorView(error: e, onRetry: () => ref.invalidate(contractsProvider)),
        data: (o) => RefreshIndicator(
          onRefresh: () async => ref.invalidate(contractsProvider),
          child: ListView(padding: safeListPadding(context), children: [
            Card(
              key: const Key('contracts_summary'),
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text(tr('Fixkosten aus Verträgen'), style: theme.textTheme.titleSmall),
                  Text(tr('{0} pro Monat', [formatCents(o.monthlyTotalCents)]),
                      key: const Key('contracts_monthly'), style: theme.textTheme.headlineSmall),
                  Text(tr('{0} pro Jahr', [formatCents(o.yearlyTotalCents)])),
                  if (o.dueSoon > 0)
                    Padding(
                      padding: const EdgeInsets.only(top: 6),
                      child: Text(tr('{0} Kündigungsfrist(en) in den nächsten 30 Tagen', [o.dueSoon]),
                          key: const Key('contracts_due_soon'),
                          style: TextStyle(color: theme.colorScheme.error, fontWeight: FontWeight.w600)),
                    ),
                ]),
              ),
            ),
            if (o.contracts.isEmpty)
              Padding(
                padding: const EdgeInsets.all(24),
                child: Text(tr('Noch keine Verträge. Trag Handy, Strom, Versicherungen oder Abos ein – die App erinnert dich vor Ablauf der Kündigungsfrist.'),
                    key: const Key('contracts_empty'), textAlign: TextAlign.center),
              ),
            for (final c in o.contracts)
              Card(
                key: Key('contract_${c.id}'),
                child: ListTile(
                  title: Text(c.name),
                  subtitle: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text('${c.kindLabel}${c.provider == null ? '' : ' · ${c.provider}'} · '
                        '${formatCents(c.costCents)} ${c.billingLabel}'),
                    Text(c.hintText,
                        style: TextStyle(
                            color: c.dueSoon ? theme.colorScheme.error : null,
                            fontWeight: c.dueSoon ? FontWeight.w600 : null)),
                  ]),
                  trailing: c.active ? const Icon(Icons.chevron_right) : Chip(label: Text(c.statusLabel)),
                  onTap: () => context.push('/vertraege/${c.id}'),
                ),
              ),
            const SizedBox(height: 8),
            Text(o.note, style: theme.textTheme.bodySmall),
            const SizedBox(height: 80),
          ]),
        ),
      ),
    );
  }
}

/// Ein Vertrag mit Fristen, Aktionen und Dokumenten.
class ContractDetailScreen extends ConsumerWidget {
  const ContractDetailScreen({super.key, required this.contractId});

  final int contractId;

  Future<bool> _run(BuildContext context, WidgetRef ref, Future<void> Function() action, [String? done]) async {
    try {
      await action();
      ref.invalidate(contractProvider(contractId));
      ref.invalidate(contractDocumentsProvider(contractId));
      ref.invalidate(contractsProvider);
      if (done != null && context.mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(done)));
      return true;
    } catch (e) {
      if (context.mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(apiErrorMessage(e))));
      return false;
    }
  }

  Future<void> _upload(BuildContext context, WidgetRef ref) async {
    const types = XTypeGroup(
      label: 'PDF/Bild',
      extensions: ['pdf', 'png', 'jpg', 'jpeg'],
      mimeTypes: ['application/pdf', 'image/png', 'image/jpeg'],
      uniformTypeIdentifiers: ['com.adobe.pdf', 'public.png', 'public.jpeg'],
    );
    final XFile? file = await openFile(acceptedTypeGroups: const [types]);
    if (file == null || !context.mounted) return;
    final bytes = await file.readAsBytes();
    if (bytes.length > 5 * 1024 * 1024) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(tr('Die Datei ist größer als 5 MB.'))));
      }
      return;
    }
    if (!context.mounted) return;
    final title = await askText(context,
        title: tr('Titel des Dokuments'),
        initial: file.name,
        maxLength: 100,
        fieldKey: const Key('document_title_field'),
        saveKey: const Key('document_title_save'));
    if (title == null || !context.mounted) return;
    await _run(context, ref, () => ref.read(contractsRepositoryProvider).upload(contractId, file.name, bytes, title),
        tr('Dokument verschlüsselt gespeichert'));
  }

  Future<void> _open(BuildContext context, WidgetRef ref, ContractDocument d) async {
    final messenger = ScaffoldMessenger.of(context);
    try {
      final bytes = await ref.read(contractsRepositoryProvider).content(d.id);
      if (!context.mounted) return;
      if (d.isImage) {
        await showDialog<void>(
          context: context,
          builder: (c) => AlertDialog(
            title: Text(d.title),
            content: SingleChildScrollView(child: Image.memory(bytes, key: const Key('document_preview'))),
            actions: [TextButton(onPressed: () => Navigator.pop(c), child: Text(tr('Schließen')))],
          ),
        );
      } else if (kIsWeb) {
        await XFile.fromData(bytes, mimeType: d.contentType, name: d.fileName).saveTo(d.fileName);
        messenger.showSnackBar(SnackBar(content: Text(tr('Heruntergeladen: {0}', [d.fileName]))));
      } else {
        messenger.showSnackBar(
            SnackBar(content: Text(tr('PDFs lassen sich in dieser Version nur in der Web-App herunterladen.'))));
      }
    } catch (e) {
      messenger.showSnackBar(SnackBar(content: Text(apiErrorMessage(e))));
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final data = ref.watch(contractProvider(contractId));
    final docs = ref.watch(contractDocumentsProvider(contractId));
    final repo = ref.read(contractsRepositoryProvider);
    final theme = Theme.of(context);
    return data.when(
      loading: () => Scaffold(appBar: AppBar(), body: const Center(child: CircularProgressIndicator())),
      error: (e, _) => Scaffold(
          appBar: AppBar(), body: ErrorView(error: e, onRetry: () => ref.invalidate(contractProvider(contractId)))),
      data: (c) => Scaffold(
        appBar: AppBar(title: Text(c.name), actions: [
          IconButton(
            key: const Key('contract_edit'),
            tooltip: tr('Bearbeiten'),
            icon: const Icon(Icons.edit_outlined),
            onPressed: () async {
              await Navigator.of(context)
                  .push(MaterialPageRoute<void>(builder: (_) => ContractFormScreen(existing: c)));
              ref.invalidate(contractProvider(contractId));
              ref.invalidate(contractsProvider);
            },
          ),
          IconButton(
            key: const Key('contract_delete'),
            tooltip: tr('Löschen'),
            icon: const Icon(Icons.delete_outline),
            onPressed: () async {
              final ok = await showDialog<bool>(
                context: context,
                builder: (d) => AlertDialog(
                  title: Text(tr('Vertrag löschen?')),
                  content: Text(tr('Der Eintrag wird aus der App entfernt. Beim Anbieter ändert sich nichts.')),
                  actions: [
                    TextButton(onPressed: () => Navigator.pop(d, false), child: Text(tr('Abbrechen'))),
                    FilledButton(
                        key: const Key('contract_delete_confirm'),
                        onPressed: () => Navigator.pop(d, true),
                        child: Text(tr('Löschen'))),
                  ],
                ),
              );
              if (ok == true && context.mounted && await _run(context, ref, () => repo.delete(c.id)) && context.mounted) {
                context.pop();
              }
            },
          ),
        ]),
        body: ListView(padding: safeListPadding(context), children: [
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(c.hintText,
                    key: const Key('contract_hint'),
                    style: theme.textTheme.titleMedium?.copyWith(color: c.dueSoon ? theme.colorScheme.error : null)),
                const SizedBox(height: 8),
                _row(tr('Status'), c.statusLabel),
                _row(tr('Art'), c.kindLabel),
                if (c.provider != null) _row(tr('Anbieter'), c.provider!),
                _row(tr('Kosten'), '${formatCents(c.costCents)} ${c.billingLabel}'),
                _row(tr('Im Jahr'), formatCents(c.yearlyCents)),
                _row(tr('Beginn'), formatDate(c.startDate)),
                _row(tr('Mindestlaufzeit'), tr('{0} Monate', [c.minTermMonths])),
                _row(tr('Verlängerung'), tr('{0} Monate', [c.renewalMonths])),
                _row(tr('Kündigungsfrist'), c.noticeLabel),
                if (c.cancelByDate != null) _row(tr('Kündigen bis'), formatDate(c.cancelByDate!)),
                if (c.nextEndDate != null) _row(tr('Vertragsende'), formatDate(c.nextEndDate!)),
                _row(tr('Erinnerung'),
                    c.reminderDays == 0 ? tr('aus') : tr('{0} Tage vor Fristende', [c.reminderDays])),
                if (c.note != null) _row(tr('Notiz'), c.note!),
              ]),
            ),
          ),
          const SizedBox(height: 8),
          if (c.active)
            OutlinedButton.icon(
              key: const Key('contract_cancel'),
              icon: const Icon(Icons.event_busy),
              label: Text(tr('Kündigung vermerken')),
              onPressed: () async {
                final ok = await showDialog<bool>(
                  context: context,
                  builder: (d) => AlertDialog(
                    title: Text(tr('Kündigung vermerken')),
                    content: Text(tr('Die App vermerkt nur, dass du zum {0} gekündigt hast. Die Kündigung selbst schickst du an den Anbieter.',
                        [c.nextEndDate == null ? '' : formatDate(c.nextEndDate!)])),
                    actions: [
                      TextButton(onPressed: () => Navigator.pop(d, false), child: Text(tr('Abbrechen'))),
                      FilledButton(
                          key: const Key('contract_cancel_confirm'),
                          onPressed: () => Navigator.pop(d, true),
                          child: Text(tr('Vermerken'))),
                    ],
                  ),
                );
                if (ok == true && context.mounted) await _run(context, ref, () => repo.cancel(c.id), tr('Kündigung vermerkt'));
              },
            )
          else
            OutlinedButton.icon(
              key: const Key('contract_reactivate'),
              icon: const Icon(Icons.undo),
              label: Text(tr('Kündigung zurücknehmen')),
              onPressed: () => _run(context, ref, () => repo.reactivate(c.id)),
            ),
          const SizedBox(height: 16),
          Row(children: [
            Expanded(child: Text(tr('Dokumente'), style: theme.textTheme.titleMedium)),
            TextButton.icon(
              key: const Key('document_upload'),
              icon: const Icon(Icons.upload_file),
              label: Text(tr('Hochladen')),
              onPressed: () => _upload(context, ref),
            ),
          ]),
          Text(tr('PDF, PNG oder JPEG bis 5 MB. Wird verschlüsselt gespeichert und nur dir angezeigt.'),
              style: theme.textTheme.bodySmall),
          docs.when(
            loading: () => const Padding(padding: EdgeInsets.all(16), child: LinearProgressIndicator()),
            error: (e, _) => Text(apiErrorMessage(e)),
            data: (list) => Column(children: [
              if (list.isEmpty)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 12),
                  child: Text(tr('Noch keine Dokumente.'), key: const Key('documents_empty')),
                ),
              for (final d in list)
                ListTile(
                  key: Key('document_${d.id}'),
                  contentPadding: EdgeInsets.zero,
                  leading: Icon(d.isImage ? Icons.image_outlined : Icons.picture_as_pdf_outlined),
                  title: Text(d.title),
                  subtitle: Text('${d.fileName} · ${(d.sizeBytes / 1024).ceil()} KB · ${formatDate(d.createdAt)}'),
                  onTap: () => _open(context, ref, d),
                  trailing: IconButton(
                    key: Key('document_delete_${d.id}'),
                    tooltip: tr('Löschen'),
                    icon: const Icon(Icons.delete_outline),
                    onPressed: () => _run(context, ref, () => repo.deleteDocument(d.id)),
                  ),
                ),
            ]),
          ),
          const SizedBox(height: 40),
        ]),
      ),
    );
  }

  Widget _row(String label, String value) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 2),
        child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Expanded(flex: 2, child: Text(label)),
          Expanded(flex: 3, child: Text(value, textAlign: TextAlign.right)),
        ]),
      );
}

/// Vertrag anlegen oder ändern.
class ContractFormScreen extends ConsumerStatefulWidget {
  const ContractFormScreen({super.key, this.existing});

  final Contract? existing;

  @override
  ConsumerState<ContractFormScreen> createState() => _ContractFormScreenState();
}

class _ContractFormScreenState extends ConsumerState<ContractFormScreen> {
  late final _name = TextEditingController(text: widget.existing?.name ?? '');
  late final _provider = TextEditingController(text: widget.existing?.provider ?? '');
  late final _cost = TextEditingController(
      text: widget.existing == null ? '' : formatCents(widget.existing!.costCents).replaceAll(RegExp(r'[^0-9,.]'), ''));
  late final _start = TextEditingController(
      text: widget.existing == null ? '' : formatDate(widget.existing!.startDate));
  late final _minTerm = TextEditingController(text: '${widget.existing?.minTermMonths ?? 24}');
  late final _renewal = TextEditingController(text: '${widget.existing?.renewalMonths ?? 1}');
  late final _notice = TextEditingController(text: '${widget.existing?.noticeValue ?? 1}');
  late final _reminder = TextEditingController(text: '${widget.existing?.reminderDays ?? 14}');
  late final _note = TextEditingController(text: widget.existing?.note ?? '');
  late String _kind = widget.existing?.kind ?? 'HANDY_INTERNET';
  late String _billing = widget.existing?.billing ?? 'MONTHLY';
  late String _noticeUnit = widget.existing?.noticeUnit ?? 'MONTHS';
  String? _error;
  bool _busy = false;

  @override
  void dispose() {
    for (final c in [_name, _provider, _cost, _start, _minTerm, _renewal, _notice, _reminder, _note]) {
      c.dispose();
    }
    super.dispose();
  }

  static DateTime? _parseDate(String s) {
    final m = RegExp(r'^(\d{1,2})\.(\d{1,2})\.(\d{4})$').firstMatch(s.trim());
    if (m == null) return null;
    final d = DateTime(int.parse(m[3]!), int.parse(m[2]!), int.parse(m[1]!));
    return d.day == int.parse(m[1]!) ? d : null;
  }

  Future<void> _save() async {
    final cost = parseEuroToCents(_cost.text);
    final start = _start.text.trim().isEmpty ? null : _parseDate(_start.text);
    final ints = [_minTerm, _renewal, _notice, _reminder].map((c) => int.tryParse(c.text.trim())).toList();
    if (_name.text.trim().isEmpty) {
      setState(() => _error = tr('Bitte einen Namen angeben.'));
      return;
    }
    if (cost == null || cost < 0) {
      setState(() => _error = tr('Bitte einen gültigen Betrag eingeben.'));
      return;
    }
    if (_start.text.trim().isNotEmpty && start == null) {
      setState(() => _error = tr('Datum bitte als TT.MM.JJJJ eingeben.'));
      return;
    }
    if (ints.contains(null)) {
      setState(() => _error = tr('Bitte ganze Zahlen für Laufzeit, Frist und Erinnerung eingeben.'));
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    final body = <String, Object?>{
      'name': _name.text.trim(),
      'provider': _provider.text.trim().isEmpty ? null : _provider.text.trim(),
      'kind': _kind,
      'costCents': cost,
      'billing': _billing,
      if (start != null)
        'startDate': '${start.year}-${start.month.toString().padLeft(2, '0')}-${start.day.toString().padLeft(2, '0')}',
      'minTermMonths': ints[0],
      'renewalMonths': ints[1],
      'noticeValue': ints[2],
      'noticeUnit': _noticeUnit,
      'reminderDays': ints[3],
      'note': _note.text.trim().isEmpty ? null : _note.text.trim(),
    };
    try {
      await ref.read(contractsRepositoryProvider).save(widget.existing?.id, body);
      if (mounted) Navigator.of(context).pop();
    } catch (e) {
      if (mounted) setState(() => _error = apiErrorMessage(e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Widget _number(TextEditingController c, String label, Key key) => TextField(
        key: key,
        controller: c,
        keyboardType: TextInputType.number,
        decoration: InputDecoration(labelText: label),
      );

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(widget.existing == null ? tr('Neuer Vertrag') : tr('Vertrag bearbeiten'))),
      body: ListView(padding: safeListPadding(context), children: [
        TextField(
            key: const Key('contract_name'),
            controller: _name,
            maxLength: 80,
            decoration: InputDecoration(labelText: tr('Name, z. B. Handyvertrag'))),
        TextField(
            key: const Key('contract_provider'),
            controller: _provider,
            maxLength: 80,
            decoration: InputDecoration(labelText: tr('Anbieter (optional)'))),
        DropdownButtonFormField<String>(
          key: const Key('contract_kind'),
          value: _kind,
          isExpanded: true,
          decoration: InputDecoration(labelText: tr('Art')),
          items: [for (final e in contractKinds.entries) DropdownMenuItem(value: e.key, child: Text(tr(e.value)))],
          onChanged: (v) => setState(() => _kind = v ?? _kind),
        ),
        TextField(
          key: const Key('contract_cost'),
          controller: _cost,
          keyboardType: const TextInputType.numberWithOptions(decimal: true),
          decoration: InputDecoration(labelText: tr('Kosten in €')),
        ),
        DropdownButtonFormField<String>(
          key: const Key('contract_billing'),
          value: _billing,
          isExpanded: true,
          decoration: InputDecoration(labelText: tr('Zahlung')),
          items: [
            DropdownMenuItem(value: 'MONTHLY', child: Text(tr('monatlich'))),
            DropdownMenuItem(value: 'QUARTERLY', child: Text(tr('vierteljährlich'))),
            DropdownMenuItem(value: 'YEARLY', child: Text(tr('jährlich'))),
          ],
          onChanged: (v) => setState(() => _billing = v ?? _billing),
        ),
        TextField(
          key: const Key('contract_start'),
          controller: _start,
          keyboardType: TextInputType.datetime,
          decoration: InputDecoration(labelText: tr('Vertragsbeginn (TT.MM.JJJJ, leer = heute)')),
        ),
        _number(_minTerm, tr('Mindestlaufzeit in Monaten'), const Key('contract_min_term')),
        _number(_renewal, tr('Verlängerung danach in Monaten'), const Key('contract_renewal')),
        Row(children: [
          Expanded(child: _number(_notice, tr('Kündigungsfrist'), const Key('contract_notice'))),
          const SizedBox(width: 12),
          Expanded(
            child: DropdownButtonFormField<String>(
              key: const Key('contract_notice_unit'),
              value: _noticeUnit,
              isExpanded: true,
              items: [
                DropdownMenuItem(value: 'DAYS', child: Text(tr('Tage'))),
                DropdownMenuItem(value: 'WEEKS', child: Text(tr('Wochen'))),
                DropdownMenuItem(value: 'MONTHS', child: Text(tr('Monate'))),
              ],
              onChanged: (v) => setState(() => _noticeUnit = v ?? _noticeUnit),
            ),
          ),
        ]),
        _number(_reminder, tr('Erinnerung (Tage vor Fristende, 0 = aus)'), const Key('contract_reminder')),
        TextField(
            key: const Key('contract_note'),
            controller: _note,
            maxLength: 300,
            decoration: InputDecoration(labelText: tr('Notiz (optional)'))),
        if (_error != null)
          Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: Text(_error!, key: const Key('contract_error'), style: TextStyle(color: Theme.of(context).colorScheme.error)),
          ),
        FilledButton(
          key: const Key('contract_save'),
          onPressed: _busy ? null : _save,
          child: Text(tr('Speichern')),
        ),
        const SizedBox(height: 40),
      ]),
    );
  }
}
