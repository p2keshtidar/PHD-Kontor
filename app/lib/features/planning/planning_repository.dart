import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/api_client.dart';
import 'planning_models.dart';

class PlanningRepository {
  PlanningRepository(this._api);

  final ApiClient _api;

  Future<BudgetOverview> budgets() async {
    final res = await _api.dio.get<Map<String, dynamic>>('/api/budgets');
    return BudgetOverview.fromJson(res.data!);
  }

  Future<BudgetOverview> setBudget(String category, int monthlyCents) async {
    final res = await _api.dio.put<Map<String, dynamic>>('/api/budgets/$category', data: {'monthlyCents': monthlyCents});
    return BudgetOverview.fromJson(res.data!);
  }

  Future<void> deleteBudget(String category) => _api.dio.delete<void>('/api/budgets/$category');

  Future<Forecast> forecast({int days = 90}) async {
    final res = await _api.dio.get<Map<String, dynamic>>('/api/forecast', queryParameters: {'days': days});
    return Forecast.fromJson(res.data!);
  }
}

final planningRepositoryProvider =
    Provider<PlanningRepository>((ref) => PlanningRepository(ref.watch(apiClientProvider)));

final budgetsProvider =
    FutureProvider.autoDispose<BudgetOverview>((ref) => ref.watch(planningRepositoryProvider).budgets());

final forecastProvider =
    FutureProvider.autoDispose<Forecast>((ref) => ref.watch(planningRepositoryProvider).forecast());
