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

  // /K060/ Teilen
  Future<List<SubaccountMember>> members(int id) async {
    final res = await _api.dio.get<List<dynamic>>('/api/subaccounts/$id/members');
    return res.data!.map((e) => SubaccountMember.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<void> addMember(int id, String recipient) =>
      _api.dio.post<void>('/api/subaccounts/$id/members', data: {'recipient': recipient.trim()});

  Future<void> removeMember(int id, int userId) => _api.dio.delete<void>('/api/subaccounts/$id/members/$userId');
}

/// /K060/ Person mit Zugriff auf ein geteiltes Unterkonto
class SubaccountMember {
  const SubaccountMember(this.userId, this.name, {this.owner = false, this.you = false});

  final int userId;
  final String name;
  final bool owner;
  final bool you;

  factory SubaccountMember.fromJson(Map<String, dynamic> j) => SubaccountMember(
        (j['userId'] as num).toInt(),
        j['name'] as String,
        owner: j['owner'] as bool? ?? false,
        you: j['you'] as bool? ?? false,
      );
}

final subaccountsRepositoryProvider =
    Provider<SubaccountsRepository>((ref) => SubaccountsRepository(ref.watch(apiClientProvider)));

final subaccountMembersProvider = FutureProvider.autoDispose.family<List<SubaccountMember>, int>(
    (ref, id) => ref.watch(subaccountsRepositoryProvider).members(id));
