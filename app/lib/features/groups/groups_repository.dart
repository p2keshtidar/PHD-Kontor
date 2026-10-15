import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_client.dart';

int _i(Object? v) => (v as num).toInt();

class GroupSummary {
  const GroupSummary(this.id, this.name, this.memberCount, this.myBalanceCents, this.totalSpentCents);

  final int id;
  final String name;
  final int memberCount;
  final int myBalanceCents;
  final int totalSpentCents;

  factory GroupSummary.fromJson(Map<String, dynamic> j) => GroupSummary(
      _i(j['id']), j['name'] as String, _i(j['memberCount']), _i(j['myBalanceCents']), _i(j['totalSpentCents']));
}

class GroupMember {
  const GroupMember(this.userId, this.name, this.owner, this.me, this.balanceCents);

  final int userId;
  final String name;
  final bool owner;
  final bool me;
  final int balanceCents;

  factory GroupMember.fromJson(Map<String, dynamic> j) => GroupMember(
      _i(j['userId']), j['name'] as String, j['owner'] as bool, j['me'] as bool, _i(j['balanceCents']));
}

class GroupSuggestion {
  const GroupSuggestion(this.fromUserId, this.fromName, this.toUserId, this.toName, this.amountCents);

  final int fromUserId;
  final String fromName;
  final int toUserId;
  final String toName;
  final int amountCents;

  factory GroupSuggestion.fromJson(Map<String, dynamic> j) => GroupSuggestion(_i(j['fromUserId']),
      j['fromName'] as String, _i(j['toUserId']), j['toName'] as String, _i(j['amountCents']));
}

class GroupExpense {
  const GroupExpense(this.id, this.payerName, this.amountCents, this.description, this.date, this.deletable,
      this.myShareCents, this.participants);

  final int id;
  final String payerName;
  final int amountCents;
  final String description;
  final DateTime date;
  final bool deletable;
  final int myShareCents;
  final int participants;

  factory GroupExpense.fromJson(Map<String, dynamic> j) => GroupExpense(
      _i(j['id']),
      j['payerName'] as String,
      _i(j['amountCents']),
      j['description'] as String,
      DateTime.parse(j['date'] as String),
      j['deletable'] as bool,
      _i(j['myShareCents']),
      (j['shares'] as List<dynamic>).where((s) => _i((s as Map<String, dynamic>)['shareCents']) > 0).length);
}

class GroupSettlement {
  const GroupSettlement(this.id, this.fromName, this.toName, this.amountCents, this.methodLabel, this.createdAt);

  final int id;
  final String fromName;
  final String toName;
  final int amountCents;
  final String methodLabel;
  final DateTime createdAt;

  factory GroupSettlement.fromJson(Map<String, dynamic> j) => GroupSettlement(_i(j['id']), j['fromName'] as String,
      j['toName'] as String, _i(j['amountCents']), j['methodLabel'] as String, DateTime.parse(j['createdAt'] as String));
}

class GroupDetail {
  const GroupDetail(this.id, this.name, this.owner, this.myBalanceCents, this.totalSpentCents, this.members,
      this.suggestions, this.expenses, this.settlements, this.note);

  final int id;
  final String name;
  final bool owner;
  final int myBalanceCents;
  final int totalSpentCents;
  final List<GroupMember> members;
  final List<GroupSuggestion> suggestions;
  final List<GroupExpense> expenses;
  final List<GroupSettlement> settlements;
  final String note;

  GroupMember get me => members.firstWhere((m) => m.me);

  factory GroupDetail.fromJson(Map<String, dynamic> j) => GroupDetail(
        _i(j['id']),
        j['name'] as String,
        j['owner'] as bool,
        _i(j['myBalanceCents']),
        _i(j['totalSpentCents']),
        [for (final m in j['members'] as List<dynamic>) GroupMember.fromJson(m as Map<String, dynamic>)],
        [for (final s in j['suggestions'] as List<dynamic>) GroupSuggestion.fromJson(s as Map<String, dynamic>)],
        [for (final e in j['expenses'] as List<dynamic>) GroupExpense.fromJson(e as Map<String, dynamic>)],
        [for (final s in j['settlements'] as List<dynamic>) GroupSettlement.fromJson(s as Map<String, dynamic>)],
        j['note'] as String? ?? '',
      );
}

class GroupsRepository {
  GroupsRepository(this._api);

  final ApiClient _api;

  Future<List<GroupSummary>> list() async {
    final res = await _api.dio.get<List<dynamic>>('/api/groups');
    return [for (final g in res.data!) GroupSummary.fromJson(g as Map<String, dynamic>)];
  }

  Future<GroupDetail> create(String name) async =>
      GroupDetail.fromJson((await _api.dio.post<Map<String, dynamic>>('/api/groups', data: {'name': name})).data!);

  Future<GroupDetail> detail(int id) async =>
      GroupDetail.fromJson((await _api.dio.get<Map<String, dynamic>>('/api/groups/$id')).data!);

  Future<void> rename(int id, String name) => _api.dio.put<void>('/api/groups/$id', data: {'name': name});

  Future<void> delete(int id) => _api.dio.delete<void>('/api/groups/$id');

  Future<void> addMember(int id, String recipient) =>
      _api.dio.post<void>('/api/groups/$id/members', data: {'recipient': recipient});

  Future<void> removeMember(int id, int userId) => _api.dio.delete<void>('/api/groups/$id/members/$userId');

  Future<void> addExpense(int id,
          {required String description, required int amountCents, int? payerId, List<int>? participants}) =>
      _api.dio.post<void>('/api/groups/$id/expenses', data: {
        'description': description,
        'amountCents': amountCents,
        if (payerId != null) 'payerId': payerId,
        if (participants != null) 'participants': participants,
      });

  Future<void> deleteExpense(int id, int expenseId) => _api.dio.delete<void>('/api/groups/$id/expenses/$expenseId');

  Future<void> settle(int id, int toUserId, int amountCents, String idempotencyKey) => _api.dio.post<void>(
        '/api/groups/$id/settlements',
        data: {'toUserId': toUserId, 'amountCents': amountCents},
        options: Options(headers: {'Idempotency-Key': idempotencyKey}),
      );

  Future<void> confirmCash(int id, int fromUserId, int amountCents) => _api.dio
      .post<void>('/api/groups/$id/settlements/cash', data: {'fromUserId': fromUserId, 'amountCents': amountCents});
}

final groupsRepositoryProvider = Provider<GroupsRepository>((ref) => GroupsRepository(ref.watch(apiClientProvider)));

final groupsProvider = FutureProvider.autoDispose<List<GroupSummary>>((ref) => ref.watch(groupsRepositoryProvider).list());

final groupDetailProvider =
    FutureProvider.autoDispose.family<GroupDetail, int>((ref, id) => ref.watch(groupsRepositoryProvider).detail(id));
