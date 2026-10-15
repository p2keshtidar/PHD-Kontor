import 'package:flutter/material.dart';

import '../l10n/l10n.dart';

/// Fragt einen Text ab (z. B. Name oder Notiz). Gibt den Text zurück oder null bei Abbruch.
/// Der Dialog besitzt seinen Controller selbst – er wird erst nach der Schließen-Animation freigegeben.
Future<String?> askText(
  BuildContext context, {
  required String title,
  String initial = '',
  String? label,
  String? hint,
  int maxLength = 40,
  int maxLines = 1,
  Key? fieldKey,
  Key? saveKey,
}) =>
    showDialog<String>(
      context: context,
      builder: (_) => _TextDialog(
        title: title,
        initial: initial,
        label: label,
        hint: hint,
        maxLength: maxLength,
        maxLines: maxLines,
        fieldKey: fieldKey,
        saveKey: saveKey,
      ),
    );

class _TextDialog extends StatefulWidget {
  const _TextDialog({
    required this.title,
    required this.initial,
    required this.maxLength,
    required this.maxLines,
    this.label,
    this.hint,
    this.fieldKey,
    this.saveKey,
  });

  final String title;
  final String initial;
  final String? label;
  final String? hint;
  final int maxLength;
  final int maxLines;
  final Key? fieldKey;
  final Key? saveKey;

  @override
  State<_TextDialog> createState() => _TextDialogState();
}

class _TextDialogState extends State<_TextDialog> {
  late final TextEditingController _controller = TextEditingController(text: widget.initial);

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AlertDialog(
        title: Text(widget.title),
        content: TextField(
          key: widget.fieldKey,
          controller: _controller,
          maxLength: widget.maxLength,
          maxLines: widget.maxLines,
          minLines: widget.maxLines > 1 ? 2 : 1,
          autofocus: true,
          decoration: InputDecoration(labelText: widget.label, hintText: widget.hint),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.of(context).pop(), child: Text(tr('Abbrechen'))),
          FilledButton(
            key: widget.saveKey,
            onPressed: () => Navigator.of(context).pop(_controller.text),
            child: Text(tr('Speichern')),
          ),
        ],
      );
}
