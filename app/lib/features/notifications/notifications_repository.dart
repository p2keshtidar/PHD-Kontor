import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_client.dart';
import 'app_notification.dart';

class NotificationsRepository {
  NotificationsRepository(this._api);

  final ApiClient _api;

  Future<List<AppNotification>> fetchLatest({int size = 50}) async {
    final res = await _api.dio.get<Map<String, dynamic>>('/api/notifications',
        queryParameters: {'page': 0, 'size': size});
    return (res.data!['items'] as List<dynamic>)
        .map((e) => AppNotification.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<int> unreadCount() async {
    final res = await _api.dio.get<Map<String, dynamic>>('/api/notifications/unread-count');
    return (res.data!['count'] as num).toInt();
  }

  Future<void> markAllRead() => _api.dio.put<void>('/api/notifications/read-all');

  Future<void> markRead(int id) => _api.dio.put<void>('/api/notifications/$id/read');
}

final notificationsRepositoryProvider = Provider<NotificationsRepository>(
    (ref) => NotificationsRepository(ref.watch(apiClientProvider)));

/// Anzahl ungelesener Meldungen für das Glockensymbol.
final unreadCountProvider = FutureProvider.autoDispose<int>(
    (ref) => ref.watch(notificationsRepositoryProvider).unreadCount());

final notificationsProvider = FutureProvider.autoDispose<List<AppNotification>>(
    (ref) => ref.watch(notificationsRepositoryProvider).fetchLatest());
