import 'package:flutter/material.dart';

/// Startseite für Tests, die einen Bildschirm per Navigator öffnen (damit er sich wieder schließen kann).
class Launcher extends StatelessWidget {
  const Launcher(this.child, {super.key});
  final Widget child;

  @override
  Widget build(BuildContext context) => Scaffold(
        body: Center(
          child: ElevatedButton(
            key: const Key('launcher_open'),
            onPressed: () => Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => child)),
            child: const Text('ÖFFNEN'),
          ),
        ),
      );
}
