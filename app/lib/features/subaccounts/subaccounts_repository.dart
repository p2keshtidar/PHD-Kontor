import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_client.dart';

/// Digitale Konten (/F410/ bis /F430/). Die Liste kommt über GET /api/accounts (accountsProvider).
class SubaccountsRepository {
  SubaccountsRepository(this._api);

  final ApiClient _api;

  Future<void> create(String name, String icon, {String? purpose, int? targetCents, DateTime? targetDate}) =>
      _api.dio.post<void>('/api/subaccounts',
          data: _body(name, icon, purpose: purpose, targetCents: targetCents, targetDate: targetDate));

  /// Ändert alles auf einmal: fehlendes Ziel = Ziel entfernen.
  Future<void> rename(int id, String name, String icon, {String? purpose, int? targetCents, DateTime? targetDate}) =>
      _api.dio.put<void>('/api/subaccounts/$id',
          data: _body(name, icon, purpose: purpose, targetCents: targetCents, targetDate: targetDate));

  static Map<String, dynamic> _body(String name, String icon,
          {String? purpose, int? targetCents, DateTime? targetDate}) =>
      {
        'name': name.trim(),
        'icon': icon,
        if (purpose != null) 'purpose': purpose,
        if (targetCents != null) 'targetCents': targetCents,
        if (targetDate != null)
          'targetDate': '${targetDate.year.toString().padLeft(4, '0')}-'
              '${targetDate.month.toString().padLeft(2, '0')}-${targetDate.day.toString().padLeft(2, '0')}',
      };

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

  Future<void> addMember(int id, String recipient, {String role = 'FULL'}) =>
      _api.dio.post<void>('/api/subaccounts/$id/members', data: {'recipient': recipient.trim(), 'role': role});

  /// Rolle eines Mitglieds ändern (nur Besitzer): FULL, DEPOSIT oder VIEW
  Future<void> setRole(int id, int userId, String role) =>
      _api.dio.put<void>('/api/subaccounts/$id/members/$userId', data: {'role': role});

  Future<void> removeMember(int id, int userId) => _api.dio.delete<void>('/api/subaccounts/$id/members/$userId');
}

/// /K060/ Person mit Zugriff auf ein geteiltes digitales Konto
class SubaccountMember {
  const SubaccountMember(this.userId, this.name, {this.owner = false, this.you = false, this.role = 'FULL'});

  final int userId;
  final String name;
  final bool owner;
  final bool you;

  /// OWNER, FULL, DEPOSIT, VIEW
  final String role;

  factory SubaccountMember.fromJson(Map<String, dynamic> j) => SubaccountMember(
        (j['userId'] as num).toInt(),
        j['name'] as String,
        owner: j['owner'] as bool? ?? false,
        you: j['you'] as bool? ?? false,
        role: j['role'] as String? ?? ((j['owner'] as bool? ?? false) ? 'OWNER' : 'FULL'),
      );
}

final subaccountsRepositoryProvider =
    Provider<SubaccountsRepository>((ref) => SubaccountsRepository(ref.watch(apiClientProvider)));

final subaccountMembersProvider = FutureProvider.autoDispose.family<List<SubaccountMember>, int>(
    (ref, id) => ref.watch(subaccountsRepositoryProvider).members(id));
