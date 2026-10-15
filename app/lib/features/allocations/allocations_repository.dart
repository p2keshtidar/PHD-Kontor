import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_client.dart';
import 'allocation_models.dart';

String _day(DateTime d) =>
    '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

/// Sparregeln und Geldzuweisungen für digitale Konten (/api/allocations).
class AllocationsRepository {
  AllocationsRepository(this._api);

  final ApiClient _api;

  Future<List<AllocationSource>> sources() async {
    final res = await _api.dio.get<List<dynamic>>('/api/allocations/sources');
    return res.data!.map((e) => AllocationSource.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<AccountAllocations> forAccount(int accountId) async {
    final res = await _api.dio.get<Map<String, dynamic>>('/api/allocations/accounts/$accountId');
    return AccountAllocations.fromJson(res.data!);
  }

  /// Neue Sparregel; ohne Startdatum beginnt sie heute (und wird gleich ausgeführt).
  Future<AllocationRule> createRule({
    required int targetAccountId,
    required String sourceType,
    int? sourceId,
    required int amountCents,
    required String interval,
    DateTime? startDate,
    DateTime? endDate,
  }) async {
    final res = await _api.dio.post<Map<String, dynamic>>('/api/allocations/rules', data: {
      'targetAccountId': targetAccountId,
      'sourceType': sourceType,
      if (sourceId != null) 'sourceId': sourceId,
      'amountCents': amountCents,
      'interval': interval,
      if (startDate != null) 'startDate': _day(startDate),
      if (endDate != null) 'endDate': _day(endDate),
    });
    return AllocationRule.fromJson(res.data!);
  }

  Future<void> updateRule(int id, {int? amountCents, String? interval}) =>
      _api.dio.put<void>('/api/allocations/rules/$id', data: {
        if (amountCents != null) 'amountCents': amountCents,
        if (interval != null) 'interval': interval,
      });

  Future<void> pause(int id) => _api.dio.post<void>('/api/allocations/rules/$id/pause');

  Future<void> resume(int id) => _api.dio.post<void>('/api/allocations/rules/$id/resume');

  /// Beenden: keine weiteren Ausführungen (Vormerkungen bleiben, bis sie aufgehoben werden).
  Future<void> end(int id) => _api.dio.delete<void>('/api/allocations/rules/$id');

  Future<AllocationRun> retry(int runId) async {
    final res = await _api.dio.post<Map<String, dynamic>>('/api/allocations/runs/$runId/retry');
    return AllocationRun.fromJson(res.data!);
  }

  Future<void> earmark(int targetAccountId, int externalAccountId, int amountCents) =>
      _api.dio.post<void>('/api/allocations/earmarks', data: {
        'targetAccountId': targetAccountId,
        'externalAccountId': externalAccountId,
        'amountCents': amountCents,
      });

  /// Vormerkung aufheben; ohne Betrag die ganze Vormerkung dieses Bankkontos.
  Future<void> release(int targetAccountId, int externalAccountId, {int? amountCents}) =>
      _api.dio.post<void>('/api/allocations/earmarks/release', data: {
        'targetAccountId': targetAccountId,
        'externalAccountId': externalAccountId,
        if (amountCents != null) 'amountCents': amountCents,
      });
}

final allocationsRepositoryProvider =
    Provider<AllocationsRepository>((ref) => AllocationsRepository(ref.watch(apiClientProvider)));

final allocationSourcesProvider = FutureProvider.autoDispose<List<AllocationSource>>(
    (ref) => ref.watch(allocationsRepositoryProvider).sources());

final accountAllocationsProvider = FutureProvider.autoDispose.family<AccountAllocations, int>(
    (ref, id) => ref.watch(allocationsRepositoryProvider).forAccount(id));
