import 'package:flutter/widgets.dart';

/// Innenabstand für scrollbare Seiten: rundherum [all], unten zusätzlich der Systembereich
/// (Gestenleiste bei Android 15+ "edge-to-edge", Home-Indikator beim iPhone).
/// So verschwindet der letzte Knopf einer Seite nie hinter der Systemleiste.
EdgeInsets safeListPadding(BuildContext context, [double all = 16]) =>
    EdgeInsets.fromLTRB(all, all, all, all + MediaQuery.paddingOf(context).bottom);
