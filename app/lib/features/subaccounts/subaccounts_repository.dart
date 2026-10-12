import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_client.dart';

/// Unterkonten (/F410/ bis /F430/). Die Liste kommt über GET /api/accounts (accountsProvider).
class SubaccountsRepository {
  SubaccountsRepository(this._api);

  final ApiClient _api;

  Future<void> create(String name, String icon) =>
      _api.dio.post<void>('/api/subaccounts', data: {'name': name.trim(), 'icon': icon});

  Future<void> rename(int id, String name, String icon) =>
      _api.dio.put<void>('/api/subaccounts/$id', data: {'name': name.trim(), 'icon': icon});

  Future<void> move(int fromAccountId, int toAccountId, int amountCents) =>
      _api.dio.post<void>('/api/subaccounts/move', data: {
        'fromAccountId': fromAccountId,
        'toAccountId': toAccountId,
        'amountCents': amountCents,
      });

  Future<void> close(int id) => _api.dio.delete<void>('/api/subaccounts/$id');
}

final subaccountsRepositoryProvider =
    Provider<SubaccountsRepository>((ref) => SubaccountsRepository(ref.watch(apiClientProvider)));
