import 'package:flutter_test/flutter_test.dart';
import 'package:girokonto_app/core/dates.dart';
import 'package:girokonto_app/core/ids.dart';
import 'package:girokonto_app/features/accounts/booking_filter.dart';
import 'package:girokonto_app/features/accounts/booking_item.dart';

void main() {
  test('Idempotenz-Schlüssel sind UUIDs und eindeutig', () {
    final a = newIdempotencyKey();
    final b = newIdempotencyKey();
    expect(a, matches(RegExp(r'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$')));
    expect(a, isNot(b));
  });

  test('Filter erzeugt nur gesetzte Parameter', () {
    expect(const BookingFilter().toQueryParameters(), isEmpty);
    final f = BookingFilter(
      from: DateTime(2026, 10, 1),
      to: DateTime(2026, 10, 31),
      direction: 'OUT',
      minCents: 1000,
      query: ' pizza ',
    );
    expect(f.toQueryParameters(), {
      'from': '2026-10-01',
      'to': '2026-10-31',
      'direction': 'OUT',
      'minCents': 1000,
      'q': 'pizza',
    });
    expect(f.activeCount, 3);
    expect(f.withoutFilters().toQueryParameters(), {'q': 'pizza'});
  });

  test('BookingItem aus JSON', () {
    final b = BookingItem.fromJson({
      'bookingId': 7,
      'type': 'TRANSFER',
      'description': 'Pizza',
      'bookedAt': '2026-10-12T13:45:00Z',
      'amountCents': -1500,
      'counterpartyName': 'Ben Muster',
      'counterpartyIban': 'DE23000100011000000001',
    });
    expect(b.isIncoming, isFalse);
    expect(bookingTypeLabel(b.type), 'Überweisung');
  });

  test('Tagesüberschriften', () {
    final now = DateTime(2026, 10, 12, 10);
    expect(dayHeader(DateTime(2026, 10, 12, 8), now), 'Heute');
    expect(dayHeader(DateTime(2026, 10, 11, 23), now), 'Gestern');
    expect(dayHeader(DateTime(2026, 10, 1, 12), now), '01.10.2026');
  });
}
