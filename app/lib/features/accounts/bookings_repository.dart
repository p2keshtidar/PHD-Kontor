import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_client.dart';
import 'booking_filter.dart';
import 'booking_item.dart';

class BookingPage {
  const BookingPage(this.items, this.hasMore);

  final List<BookingItem> items;
  final bool hasMore;
}

class BookingsRepository {
  BookingsRepository(this._api);

  final ApiClient _api;

  Future<BookingPage> fetchPage(
    int accountId, {
    BookingFilter filter = const BookingFilter(),
    int page = 0,
    int size = 20,
  }) async {
    final res = await _api.dio.get<Map<String, dynamic>>(
      '/api/accounts/$accountId/bookings',
      queryParameters: {
        ...filter.toQueryParameters(),
        'page': page,
        'size': size,
      },
    );
    final data = res.data!;
    final items = (data['items'] as List<dynamic>)
        .map((e) => BookingItem.fromJson(e as Map<String, dynamic>, accountId: accountId))
        .toList();
    return BookingPage(items, data['hasMore'] as bool);
  }

  /// Eigene Notiz speichern (leer = löschen). Gibt die gespeicherte Notiz zurück.
  Future<String?> saveNote(int accountId, int bookingId, String note) async {
    final res = await _api.dio.put<Map<String, dynamic>>('/api/accounts/$accountId/bookings/$bookingId/note',
        data: {'note': note});
    return res.data?['note'] as String?;
  }
}

final bookingsRepositoryProvider =
    Provider<BookingsRepository>((ref) => BookingsRepository(ref.watch(apiClientProvider)));

/// Die letzten 5 Umsätze eines Kontos für die Startseite.
final recentBookingsProvider =
    FutureProvider.autoDispose.family<List<BookingItem>, int>((ref, accountId) async {
  final page = await ref.watch(bookingsRepositoryProvider).fetchPage(accountId, size: 5);
  return page.items;
});
