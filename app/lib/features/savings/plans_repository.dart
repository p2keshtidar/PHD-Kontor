import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_client.dart';
import '../../core/validators.dart';
import 'plan_models.dart';

class PlansRepository {
  PlansRepository(this._api);

  final ApiClient _api;

  Future<PlanCheck> check({
    required String type,
    required String riskLevel,
    int? targetCents,
    DateTime? targetDate,
    int? monthlyRateCents,
  }) async {
    final res = await _api.dio.post<Map<String, dynamic>>('/api/plans/check', data: {
      'type': type,
      'riskLevel': riskLevel,
      if (targetCents != null) 'targetCents': targetCents,
      if (targetDate != null) 'targetDate': toIsoDate(targetDate),
      if (monthlyRateCents != null) 'monthlyRateCents': monthlyRateCents,
    });
    return PlanCheck.fromJson(res.data!);
  }

  Future<PlanDetail> create({
    required String type,
    required String name,
    required String icon,
    required int monthlyRateCents,
    required int executionDay,
    required String riskLevel,
    int? targetCents,
    DateTime? targetDate,
  }) async {
    final res = await _api.dio.post<Map<String, dynamic>>('/api/plans', data: {
      'type': type,
      'name': name.trim(),
      'icon': icon,
      'monthlyRateCents': monthlyRateCents,
      'executionDay': executionDay,
      'riskLevel': riskLevel,
      if (targetCents != null) 'targetCents': targetCents,
      if (targetDate != null) 'targetDate': toIsoDate(targetDate),
    });
    return PlanDetail.fromJson(res.data!);
  }

  Future<List<PlanSummary>> list() async {
    final res = await _api.dio.get<List<dynamic>>('/api/plans');
    return res.data!.map((e) => PlanSummary.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<PlanDetail> detail(int id) async {
    final res = await _api.dio.get<Map<String, dynamic>>('/api/plans/$id');
    return PlanDetail.fromJson(res.data!);
  }

  Future<PlanDetail> update(int id, Map<String, dynamic> changes) async {
    final res = await _api.dio.put<Map<String, dynamic>>('/api/plans/$id', data: changes);
    return PlanDetail.fromJson(res.data!);
  }

  Future<void> payout(int id) => _api.dio.post<void>('/api/plans/$id/payout');

  Future<void> close(int id) => _api.dio.delete<void>('/api/plans/$id');
}

final plansRepositoryProvider = Provider<PlansRepository>((ref) => PlansRepository(ref.watch(apiClientProvider)));

final plansProvider =
    FutureProvider.autoDispose<List<PlanSummary>>((ref) => ref.watch(plansRepositoryProvider).list());

final planDetailProvider = FutureProvider.autoDispose.family<PlanDetail, int>(
    (ref, id) => ref.watch(plansRepositoryProvider).detail(id));
