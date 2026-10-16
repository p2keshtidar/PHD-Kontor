import 'package:flutter/material.dart';

/// Globaler Zugriff auf SnackBars, z. B. für Live-Meldungen aus dem WebSocket.
final rootMessengerKey = GlobalKey<ScaffoldMessengerState>();

/// Zeigt [bar] sofort und ersetzt eine gerade sichtbare Meldung. Hinten angestellt käme sie erst Sekunden
/// später – z. B. nach einem Sprachwechsel noch auf Deutsch, oder doppelt zur Live-Meldung desselben Vorgangs.
void showNow(ScaffoldMessengerState? messenger, SnackBar bar) {
  messenger
    ?..removeCurrentSnackBar()
    ..showSnackBar(bar);
}
