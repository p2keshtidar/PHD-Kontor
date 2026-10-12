import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:girokonto_app/core/realtime_service.dart';
import 'package:girokonto_app/features/card/card_model.dart';
import 'package:girokonto_app/features/notifications/app_notification.dart';
import 'package:girokonto_app/features/subaccounts/subaccount_icons.dart';

void main() {
  test('Kartennummer wird in Vierergruppen angezeigt', () {
    const d = CardDetails('9999991234567890', '123', '10/30');
    expect(d.formattedNumber, '9999 9912 3456 7890');
  });

  test('Karte aus JSON', () {
    final c = PaymentCard.fromJson({
      'id': 1,
      'holderName': 'ANNA MUSTER',
      'maskedNumber': '•••• •••• •••• 7890',
      'last4': '7890',
      'expiry': '10/30',
      'status': 'LOCKED',
      'dailyLimitCents': 100000,
      'maxDailyLimitCents': 200000,
      'onlineEnabled': true,
      'spentTodayCents': 1290,
    });
    expect(c.isLocked, isTrue);
  });

  test('Benachrichtigung aus JSON (ohne Buchung)', () {
    final n = AppNotification.fromJson({
      'id': 5,
      'type': 'WELCOME',
      'title': 'Willkommen',
      'body': 'Dein Konto ist eröffnet.',
      'bookingId': null,
      'read': false,
      'createdAt': '2026-10-12T10:00:00Z',
    });
    expect(n.bookingId, isNull);
    expect(n.read, isFalse);
  });

  test('Unbekanntes Symbol fällt auf Sparschwein zurück', () {
    expect(subaccountIcon('gibt-es-nicht'), Icons.savings);
    expect(subaccountIcon('flight'), Icons.flight);
  });

  test('WebSocket-Adresse aus der API-Adresse', () {
    expect(RealtimeService.websocketUrl, 'ws://localhost:8080/ws');
  });
}
